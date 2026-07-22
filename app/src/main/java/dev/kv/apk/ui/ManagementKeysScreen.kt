package dev.kv.apk.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Base64
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kv.apk.data.CreateManagementKeyRequest
import dev.kv.apk.data.CreateProvisionedKeyRequest
import dev.kv.apk.data.DeviceItem
import dev.kv.apk.data.DeviceKvPayload
import dev.kv.apk.data.DeviceKvRecipientRequest
import dev.kv.apk.data.KvApi
import dev.kv.apk.data.ManagementKeyRow
import dev.kv.apk.data.OpenRouterCreateKeyRequest
import dev.kv.apk.data.OpenRouterKeyData
import dev.kv.apk.data.Prefs
import dev.kv.apk.data.ProvisionedKeyRow
import dev.kv.apk.data.OpenRouterApi
import dev.kv.apk.data.buildOpenRouterApi
import dev.kv.apk.ui.theme.KvAccent
import dev.kv.apk.ui.theme.KvBg
import dev.kv.apk.ui.theme.KvDanger
import dev.kv.apk.ui.theme.KvDim
import dev.kv.apk.ui.theme.KvFaint
import dev.kv.apk.ui.theme.KvInk
import dev.kv.apk.ui.theme.PressStart2P
import dev.kv.apk.ui.theme.VT323
import kotlinx.coroutines.launch
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

// Same device-envelope protocol as KvEntriesScreen.kt, generalized to an explicit AAD string
// (management keys use "mgmt-key:{label}", provisioned keys use "provisioned-key:{provider_key_id}"
// instead of the "device-kv:{key}" AAD used for regular KV entries).

// Management key plaintext is only ever handled as bytes and explicitly wiped after use — see
// decryptManagementKeyBytes/encryptForDevices. A JVM String can't be scrubbed (interned/immutable),
// so it's never used to hold the management key itself, only the short-lived "Bearer {secret}"
// header string Retrofit requires, whose lifetime is limited to a single suspend call.
private fun ByteArray.zero() {
    java.util.Arrays.fill(this, 0)
}

private fun hkdfExtract(salt: ByteArray, ikm: ByteArray): ByteArray {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(salt, "HmacSHA256"))
    return mac.doFinal(ikm)
}

private fun hkdfExpand(prk: ByteArray, info: ByteArray, length: Int): ByteArray {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(prk, "HmacSHA256"))
    val result = ByteArray(length)
    var t = ByteArray(0)
    var offset = 0
    var counter = 1
    while (offset < length) {
        mac.update(t); mac.update(info); mac.update(counter.toByte())
        t = mac.doFinal()
        val len = minOf(t.size, length - offset)
        t.copyInto(result, offset, 0, len)
        offset += len; counter++
    }
    return result
}

private fun hkdf(secret: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray =
    hkdfExpand(hkdfExtract(salt, secret), info, length)

private fun aesGcmDecrypt(key: ByteArray, nonce: ByteArray, ciphertext: ByteArray, aad: ByteArray): ByteArray {
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
    cipher.updateAAD(aad)
    return cipher.doFinal(ciphertext)
}

private fun aesGcmEncrypt(key: ByteArray, nonce: ByteArray, plaintext: ByteArray, aad: ByteArray): ByteArray {
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
    cipher.updateAAD(aad)
    return cipher.doFinal(plaintext)
}

// P-256 SPKI header (26 bytes): prepend before the 65-byte uncompressed EC point
private val P256_SPKI_HEADER = byteArrayOf(
    0x30, 0x59.toByte(),
    0x30, 0x13,
    0x06, 0x07, 0x2a, 0x86.toByte(), 0x48, 0xce.toByte(), 0x3d, 0x02, 0x01,
    0x06, 0x08, 0x2a, 0x86.toByte(), 0x48, 0xce.toByte(), 0x3d, 0x03, 0x01, 0x07,
    0x03, 0x42, 0x00,
)

private data class EncryptedEnvelope(
    val nonce: String,
    val ciphertext: String,
    val aad: String,
    val recipients: List<DeviceKvRecipientRequest>,
)

private fun encryptForDevices(plaintext: ByteArray, aad: String, devices: List<DeviceItem>): EncryptedEnvelope {
    val rng = SecureRandom()
    val kf = KeyFactory.getInstance("EC")
    val kpg = KeyPairGenerator.getInstance("EC")
    kpg.initialize(ECGenParameterSpec("secp256r1"))

    val dek = ByteArray(32).also { rng.nextBytes(it) }
    val nonce = ByteArray(12).also { rng.nextBytes(it) }
    val aadBytes = aad.toByteArray(Charsets.UTF_8)
    try {
        val ciphertext = aesGcmEncrypt(dek, nonce, plaintext, aadBytes)

        val recipients = devices.mapNotNull { device ->
            val pubKeyBytes = Base64.decode(device.publicKey ?: return@mapNotNull null, Base64.DEFAULT)
            val devicePubKey = kf.generatePublic(X509EncodedKeySpec(pubKeyBytes))

            val ephKp = kpg.generateKeyPair()
            val ka = KeyAgreement.getInstance("ECDH")
            ka.init(ephKp.private)
            ka.doPhase(devicePubKey, true)
            val sharedSecret = ka.generateSecret()

            val wrapKey = hkdf(
                secret = sharedSecret,
                salt = ByteArray(32),
                info = "kv-device-wrap".toByteArray(Charsets.UTF_8),
                length = 32,
            )
            sharedSecret.zero()

            val dekNonce = ByteArray(12).also { rng.nextBytes(it) }
            val encryptedDek = aesGcmEncrypt(wrapKey, dekNonce, dek, ByteArray(0))
            wrapKey.zero()

            val rawEphPub = ephKp.public.encoded.drop(P256_SPKI_HEADER.size).toByteArray()

            DeviceKvRecipientRequest(
                deviceId = device.id,
                keyType = device.keyType ?: "p256",
                ephemeralPub = Base64.encodeToString(rawEphPub, Base64.NO_WRAP),
                dekNonce = Base64.encodeToString(dekNonce, Base64.NO_WRAP),
                encryptedDek = Base64.encodeToString(encryptedDek, Base64.NO_WRAP),
            )
        }

        return EncryptedEnvelope(
            nonce = Base64.encodeToString(nonce, Base64.NO_WRAP),
            ciphertext = Base64.encodeToString(ciphertext, Base64.NO_WRAP),
            aad = Base64.encodeToString(aadBytes, Base64.NO_WRAP),
            recipients = recipients,
        )
    } finally {
        dek.zero()
    }
}

private fun decryptEnvelopeBytes(privKeyPkcs8B64: String, payload: DeviceKvPayload): ByteArray {
    val kf = KeyFactory.getInstance("EC")

    val myPrivKey = kf.generatePrivate(
        PKCS8EncodedKeySpec(Base64.decode(privKeyPkcs8B64, Base64.DEFAULT))
    )

    val rawEphPub = Base64.decode(payload.recipient.ephemeralPub, Base64.DEFAULT)
    val spki = P256_SPKI_HEADER + rawEphPub
    val ephPubKey = kf.generatePublic(X509EncodedKeySpec(spki))

    val ka = KeyAgreement.getInstance("ECDH")
    ka.init(myPrivKey)
    ka.doPhase(ephPubKey, true)
    val sharedSecret = ka.generateSecret()

    val wrapKey = hkdf(
        secret = sharedSecret,
        salt = ByteArray(32),
        info = "kv-device-wrap".toByteArray(Charsets.UTF_8),
        length = 32,
    )
    sharedSecret.zero()

    val dek = aesGcmDecrypt(
        key = wrapKey,
        nonce = Base64.decode(payload.recipient.dekNonce, Base64.DEFAULT),
        ciphertext = Base64.decode(payload.recipient.encryptedDek, Base64.DEFAULT),
        aad = ByteArray(0),
    )
    wrapKey.zero()

    try {
        return aesGcmDecrypt(
            key = dek,
            nonce = Base64.decode(payload.nonce, Base64.DEFAULT),
            ciphertext = Base64.decode(payload.ciphertext, Base64.DEFAULT),
            aad = Base64.decode(payload.aad, Base64.DEFAULT),
        )
    } finally {
        dek.zero()
    }
}

// For secrets that are meant to be displayed (provisioned/generated keys, via SHOW) — converts
// to a String and wipes the intermediate byte buffer, but the resulting String itself is
// necessarily retained (in UI state) since displaying it is the point.
private fun decryptEnvelope(privKeyPkcs8B64: String, payload: DeviceKvPayload): String {
    val bytes = decryptEnvelopeBytes(privKeyPkcs8B64, payload)
    try {
        return String(bytes, Charsets.UTF_8)
    } finally {
        bytes.zero()
    }
}

// For the management key itself: never converted to a String beyond the single short-lived
// "Bearer {secret}" header value needed for one Retrofit call — callers must zero() the
// returned array in a finally block once that call returns.
private suspend fun decryptManagementKeyBytes(api: KvApi, prefs: Prefs, managementKeyId: String): ByteArray {
    val payload = api.getManagementKeyEnvelope(managementKeyId, prefs.deviceId)
    return decryptEnvelopeBytes(prefs.devicePrivKeyPkcs8, payload)
}

// Android's INTERNET permission has no per-host allowlist, so this is the enforcement point:
// a management key's decrypted secret is only ever sent to the host registered for its own
// `provider` field, never to an arbitrary or mismatched provider's API.
private fun providerApiFor(provider: String): OpenRouterApi? = when (provider) {
    "openrouter" -> buildOpenRouterApi()
    else -> null
}

@Composable
fun ManagementKeysScreen(
    api: KvApi,
    prefs: Prefs,
    onBack: () -> Unit,
    onLogout: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var managementKeys by remember { mutableStateOf<List<ManagementKeyRow>>(emptyList()) }
    var allDevices by remember { mutableStateOf<List<DeviceItem>>(emptyList()) }
    var toast by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<ManagementKeyRow?>(null) }

    fun handleHttpError(e: retrofit2.HttpException, fallback: String) {
        if (e.code() == 401) onLogout() else toast = "$fallback ${e.code()}"
    }

    fun loadManagementKeys() {
        scope.launch {
            try {
                managementKeys = api.listManagementKeys()
            } catch (e: retrofit2.HttpException) {
                if (e.code() == 401) onLogout()
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(Unit) {
        loadManagementKeys()
        try { allDevices = api.listDevices() } catch (_: Exception) {}
    }

    // Add management key dialog
    var showAddDialog by remember { mutableStateOf(false) }
    var addLabel by remember { mutableStateOf("") }
    var addSecret by remember { mutableStateOf("") }
    var addSelectedDeviceIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var addBusy by remember { mutableStateOf(false) }
    var addError by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(KvBg),
    ) {
        if (selected == null) {
            Column(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp)) {
                    KvScreenHeader(
                        title = "MANAGEMENT KEYS",
                        onBack = onBack,
                        trailing = {
                            KvButtonOutline(
                                text = "ADD",
                                onClick = {
                                    addLabel = ""
                                    addSecret = ""
                                    addSelectedDeviceIds = emptySet()
                                    addError = ""
                                    showAddDialog = true
                                },
                                color = KvAccent,
                            )
                        },
                    )
                    Text(
                        "Third-party provider keys (e.g. OpenRouter) that control your API keys. Stored encrypted to your registered devices — this server never sees the plaintext.",
                        fontFamily = VT323,
                        fontSize = 15.sp,
                        color = KvDim,
                        lineHeight = 18.sp,
                    )
                }

                if (managementKeys.isEmpty()) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("no management keys stored", fontFamily = VT323, fontSize = 16.sp, color = KvDim)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f).padding(horizontal = 14.dp),
                    ) {
                        items(managementKeys, key = { it.id }) { row ->
                            ManagementKeyRowView(
                                row = row,
                                onOpen = { selected = row },
                                onRevoke = {
                                    scope.launch {
                                        try {
                                            api.revokeManagementKey(row.id)
                                            toast = "revoked ${row.label}"
                                            loadManagementKeys()
                                        } catch (e: retrofit2.HttpException) {
                                            handleHttpError(e, "error")
                                        } catch (e: Exception) {
                                            toast = e.message ?: "error"
                                        }
                                    }
                                },
                            )
                        }
                        item { Spacer(Modifier.height(80.dp)) }
                    }
                }
            }
        } else {
            ProvisionedKeysView(
                api = api,
                prefs = prefs,
                allDevices = allDevices,
                managementKey = selected!!,
                onBack = { selected = null },
                onLogout = onLogout,
                onToast = { toast = it },
            )
        }

        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
            KvToast(toast)
        }

        ScanlineOverlay()

        if (showAddDialog) {
            AlertDialog(
                onDismissRequest = { if (!addBusy) { showAddDialog = false; addSecret = "" } },
                title = { Text("ADD MANAGEMENT KEY", fontFamily = PressStart2P, fontSize = 9.sp, color = KvAccent) },
                text = {
                    Column {
                        KvLabel("LABEL")
                        KvInput(
                            value = addLabel,
                            onValueChange = { addLabel = it },
                            placeholder = "openrouter-primary",
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        )
                        KvLabel("PROVIDER")
                        Text(
                            "openrouter",
                            fontFamily = VT323,
                            fontSize = 17.sp,
                            color = KvDim,
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                        KvLabel("MANAGEMENT KEY")
                        KvInput(
                            value = addSecret,
                            onValueChange = { addSecret = it },
                            placeholder = "sk-or-...",
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        )
                        KvLabel("ENCRYPT FOR DEVICES")
                        if (allDevices.none { it.publicKey != null }) {
                            Text(
                                "No devices with public keys registered.",
                                fontFamily = VT323,
                                fontSize = 15.sp,
                                color = KvDim,
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                allDevices.filter { it.publicKey != null }.forEach { device ->
                                    KvCheckbox(
                                        checked = device.id in addSelectedDeviceIds,
                                        label = device.name,
                                        onToggle = {
                                            addSelectedDeviceIds = if (device.id in addSelectedDeviceIds)
                                                addSelectedDeviceIds - device.id else addSelectedDeviceIds + device.id
                                        },
                                    )
                                }
                            }
                        }
                        if (addError.isNotBlank()) {
                            Text(addError, fontFamily = VT323, fontSize = 15.sp, color = KvDanger, modifier = Modifier.padding(top = 10.dp))
                        }
                    }
                },
                confirmButton = {
                    KvButton(
                        text = if (addBusy) "…" else "SAVE",
                        enabled = !addBusy && addLabel.isNotBlank() && addSecret.isNotBlank() && addSelectedDeviceIds.isNotEmpty(),
                        onClick = {
                            scope.launch {
                                addBusy = true
                                addError = ""
                                try {
                                    val devices = allDevices.filter { it.id in addSelectedDeviceIds }
                                    val plaintextBytes = addSecret.toByteArray(Charsets.UTF_8)
                                    // Plaintext is only needed to build the envelope below — drop it
                                    // from UI state and wipe the byte copy immediately rather than
                                    // holding either until the network call returns.
                                    addSecret = ""
                                    val envelope = try {
                                        encryptForDevices(plaintextBytes, "mgmt-key:${addLabel.trim()}", devices)
                                    } finally {
                                        plaintextBytes.zero()
                                    }
                                    val resp = api.createManagementKey(
                                        CreateManagementKeyRequest(
                                            provider = "openrouter",
                                            label = addLabel.trim(),
                                            nonce = envelope.nonce,
                                            ciphertext = envelope.ciphertext,
                                            aad = envelope.aad,
                                            recipients = envelope.recipients,
                                        )
                                    )
                                    if (resp.isSuccessful) {
                                        toast = "management key added"
                                        showAddDialog = false
                                        loadManagementKeys()
                                    } else {
                                        addError = "failed: HTTP ${resp.code()}"
                                    }
                                } catch (e: Exception) {
                                    addError = e.message ?: "failed"
                                } finally {
                                    addBusy = false
                                }
                            }
                        },
                    )
                },
                dismissButton = {
                    KvButtonOutline(
                        text = "CANCEL",
                        onClick = { showAddDialog = false; addSecret = "" },
                        enabled = !addBusy,
                    )
                },
                containerColor = Color(0xFF0C120C),
                titleContentColor = KvAccent,
                textContentColor = KvInk,
            )
        }
    }
}

@Composable
private fun ManagementKeyRowView(
    row: ManagementKeyRow,
    onOpen: () -> Unit,
    onRevoke: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(row.label, fontFamily = VT323, fontSize = 19.sp, color = KvInk)
                Text("${row.provider} · ${row.createdAt}", fontFamily = VT323, fontSize = 14.sp, color = KvDim)
            }
            KvStatusChip(text = row.status.uppercase(), active = row.status == "active")
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KvButtonOutline(text = "OPEN", onClick = onOpen, color = KvAccent)
            if (row.status == "active") {
                KvButtonDanger(text = "REVOKE", onClick = onRevoke)
            }
        }
    }
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(KvFaint))
}

@Composable
private fun ProvisionedKeysView(
    api: KvApi,
    prefs: Prefs,
    allDevices: List<DeviceItem>,
    managementKey: ManagementKeyRow,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onToast: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var provisioned by remember { mutableStateOf<List<ProvisionedKeyRow>>(emptyList()) }
    var providerKeys by remember { mutableStateOf<List<OpenRouterKeyData>?>(null) }
    var providerLoading by remember { mutableStateOf(false) }
    var providerError by remember { mutableStateOf("") }

    fun handleHttpError(e: retrofit2.HttpException) {
        if (e.code() == 401) onLogout() else onToast("error ${e.code()}")
    }

    fun loadProvisioned() {
        scope.launch {
            try {
                provisioned = api.listProvisionedKeys(managementKey.id)
            } catch (e: retrofit2.HttpException) {
                handleHttpError(e)
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(managementKey.id) { loadProvisioned() }

    var showCreateDialog by remember { mutableStateOf(false) }
    var createLabel by remember { mutableStateOf("") }
    var createLimit by remember { mutableStateOf("") }
    var createSelectedDeviceIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var createBusy by remember { mutableStateOf(false) }
    var createError by remember { mutableStateOf("") }

    // Newly-created plaintext secret, shown once
    var createdSecret by remember { mutableStateOf<String?>(null) }
    // A previously-generated secret, recovered on demand via SHOW
    var revealedSecret by remember { mutableStateOf<String?>(null) }
    var revealBusyId by remember { mutableStateOf<String?>(null) }
    var revokeBusyId by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp)) {
            KvScreenHeader(
                title = managementKey.label.uppercase(),
                onBack = onBack,
                trailing = {
                    KvButtonOutline(
                        text = "ADD KEY",
                        onClick = {
                            createLabel = ""
                            createLimit = ""
                            createSelectedDeviceIds = emptySet()
                            createError = ""
                            showCreateDialog = true
                        },
                        color = KvAccent,
                    )
                },
            )
        }

        LazyColumn(modifier = Modifier.weight(1f).padding(horizontal = 14.dp)) {
            item {
                KvCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(15.dp)) {
                        KvSectionTitle("LIVE FROM ${managementKey.provider.uppercase()}")
                        Text(
                            "Fetches the current key list directly from the provider using this management key.",
                            fontFamily = VT323,
                            fontSize = 15.sp,
                            color = KvDim,
                            modifier = Modifier.padding(bottom = 10.dp),
                        )
                        KvButtonOutline(
                            text = if (providerLoading) "…" else "REFRESH",
                            enabled = !providerLoading,
                            onClick = {
                                scope.launch {
                                    providerLoading = true
                                    providerError = ""
                                    try {
                                        val provider = providerApiFor(managementKey.provider)
                                        if (provider == null) {
                                            providerError = "Unsupported provider: ${managementKey.provider}"
                                            return@launch
                                        }
                                        val mgmtSecretBytes = decryptManagementKeyBytes(api, prefs, managementKey.id)
                                        try {
                                            providerKeys = provider.listKeys("Bearer " + String(mgmtSecretBytes, Charsets.UTF_8)).data
                                        } finally {
                                            mgmtSecretBytes.zero()
                                        }
                                    } catch (e: retrofit2.HttpException) {
                                        handleHttpError(e)
                                    } catch (e: Exception) {
                                        providerError = e.message ?: "failed to fetch from provider"
                                    } finally {
                                        providerLoading = false
                                    }
                                }
                            },
                        )
                        if (providerError.isNotBlank()) {
                            Text(providerError, fontFamily = VT323, fontSize = 14.sp, color = KvDanger, modifier = Modifier.padding(top = 8.dp))
                        }
                        providerKeys?.let { list ->
                            Spacer(Modifier.height(10.dp))
                            if (list.isEmpty()) {
                                Text("no keys on provider", fontFamily = VT323, fontSize = 14.sp, color = KvDim)
                            } else {
                                list.forEach { k ->
                                    Text(
                                        "${k.name}  (${if (k.disabled) "disabled" else "active"})",
                                        fontFamily = VT323,
                                        fontSize = 14.sp,
                                        color = KvInk,
                                        modifier = Modifier.padding(vertical = 2.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("PROVISIONED KEYS", fontFamily = PressStart2P, fontSize = 8.sp, color = KvDim)
                }
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(KvFaint))
            }

            if (provisioned.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 30.dp), contentAlignment = Alignment.Center) {
                        Text("no keys generated yet", fontFamily = VT323, fontSize = 16.sp, color = KvDim)
                    }
                }
            } else {
                items(provisioned, key = { it.id }) { pk ->
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(pk.label, fontFamily = VT323, fontSize = 18.sp, color = KvInk)
                                Text(pk.providerKeyId, fontFamily = VT323, fontSize = 13.sp, color = KvDim)
                            }
                            KvStatusChip(text = pk.status.uppercase(), active = pk.status == "active")
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            KvButtonOutline(
                                text = if (revealBusyId == pk.id) "…" else "SHOW",
                                enabled = revealBusyId == null,
                                color = KvAccent,
                                onClick = {
                                    scope.launch {
                                        revealBusyId = pk.id
                                        try {
                                            val payload = api.getProvisionedKeyEnvelope(managementKey.id, pk.id, prefs.deviceId)
                                            revealedSecret = decryptEnvelope(prefs.devicePrivKeyPkcs8, payload)
                                        } catch (e: retrofit2.HttpException) {
                                            handleHttpError(e)
                                        } catch (e: Exception) {
                                            onToast(e.message ?: "decrypt failed")
                                        } finally {
                                            revealBusyId = null
                                        }
                                    }
                                },
                            )
                            if (pk.status == "active") {
                                KvButtonDanger(
                                    text = if (revokeBusyId == pk.id) "…" else "REVOKE",
                                    onClick = {
                                        scope.launch {
                                            revokeBusyId = pk.id
                                            try {
                                                val provider = providerApiFor(managementKey.provider)
                                                if (provider == null) {
                                                    onToast("Unsupported provider: ${managementKey.provider}")
                                                    return@launch
                                                }
                                                val mgmtSecretBytes = decryptManagementKeyBytes(api, prefs, managementKey.id)
                                                try {
                                                    provider.revokeKey("Bearer " + String(mgmtSecretBytes, Charsets.UTF_8), pk.providerKeyId)
                                                } finally {
                                                    mgmtSecretBytes.zero()
                                                }
                                                api.revokeProvisionedKey(managementKey.id, pk.id)
                                                onToast("revoked ${pk.label}")
                                                loadProvisioned()
                                            } catch (e: retrofit2.HttpException) {
                                                handleHttpError(e)
                                            } catch (e: Exception) {
                                                onToast(e.message ?: "revoke failed")
                                            } finally {
                                                revokeBusyId = null
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(KvFaint))
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { if (!createBusy) showCreateDialog = false },
            title = { Text("GENERATE KEY", fontFamily = PressStart2P, fontSize = 9.sp, color = KvAccent) },
            text = {
                Column {
                    Text(
                        "Calls ${managementKey.provider} directly from this device to create a new API key, then stores it encrypted for the devices you select below.",
                        fontFamily = VT323,
                        fontSize = 15.sp,
                        color = KvDim,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                    KvLabel("LABEL")
                    KvInput(
                        value = createLabel,
                        onValueChange = { createLabel = it },
                        placeholder = "mobile-app-key",
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    )
                    KvLabel("LIMIT (USD, OPTIONAL)")
                    KvInput(
                        value = createLimit,
                        onValueChange = { createLimit = it },
                        placeholder = "—",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                    KvLabel("ENCRYPT FOR DEVICES")
                    if (allDevices.none { it.publicKey != null }) {
                        Text("No devices with public keys registered.", fontFamily = VT323, fontSize = 15.sp, color = KvDim)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            allDevices.filter { it.publicKey != null }.forEach { device ->
                                KvCheckbox(
                                    checked = device.id in createSelectedDeviceIds,
                                    label = device.name,
                                    onToggle = {
                                        createSelectedDeviceIds = if (device.id in createSelectedDeviceIds)
                                            createSelectedDeviceIds - device.id else createSelectedDeviceIds + device.id
                                    },
                                )
                            }
                        }
                    }
                    if (createError.isNotBlank()) {
                        Text(createError, fontFamily = VT323, fontSize = 15.sp, color = KvDanger, modifier = Modifier.padding(top = 10.dp))
                    }
                }
            },
            confirmButton = {
                KvButton(
                    text = if (createBusy) "…" else "GENERATE",
                    enabled = !createBusy && createLabel.isNotBlank() && createSelectedDeviceIds.isNotEmpty(),
                    onClick = {
                        scope.launch {
                            createBusy = true
                            createError = ""
                            try {
                                val provider = providerApiFor(managementKey.provider)
                                if (provider == null) {
                                    createError = "Unsupported provider: ${managementKey.provider}"
                                    return@launch
                                }
                                val mgmtSecretBytes = decryptManagementKeyBytes(api, prefs, managementKey.id)
                                val created = try {
                                    provider.createKey(
                                        "Bearer " + String(mgmtSecretBytes, Charsets.UTF_8),
                                        OpenRouterCreateKeyRequest(
                                            name = createLabel.trim(),
                                            limit = createLimit.toDoubleOrNull(),
                                        ),
                                    )
                                } finally {
                                    mgmtSecretBytes.zero()
                                }
                                val providerKeyId = created.data.keyId
                                val devices = allDevices.filter { it.id in createSelectedDeviceIds }
                                val envelope = encryptForDevices(
                                    created.key.toByteArray(Charsets.UTF_8),
                                    "provisioned-key:$providerKeyId",
                                    devices,
                                )
                                val resp = api.createProvisionedKey(
                                    managementKey.id,
                                    CreateProvisionedKeyRequest(
                                        providerKeyId = providerKeyId,
                                        label = createLabel.trim(),
                                        nonce = envelope.nonce,
                                        ciphertext = envelope.ciphertext,
                                        aad = envelope.aad,
                                        recipients = envelope.recipients,
                                    ),
                                )
                                if (resp.isSuccessful) {
                                    createdSecret = created.key
                                    showCreateDialog = false
                                    loadProvisioned()
                                } else {
                                    createError = "stored on provider but failed to save locally: HTTP ${resp.code()}"
                                }
                            } catch (e: retrofit2.HttpException) {
                                if (e.code() == 401) onLogout() else createError = "error ${e.code()}"
                            } catch (e: Exception) {
                                createError = e.message ?: "failed"
                            } finally {
                                createBusy = false
                            }
                        }
                    },
                )
            },
            dismissButton = {
                KvButtonOutline(text = "CANCEL", onClick = { showCreateDialog = false }, enabled = !createBusy)
            },
            containerColor = Color(0xFF0C120C),
            titleContentColor = KvAccent,
            textContentColor = KvInk,
        )
    }

    (createdSecret ?: revealedSecret)?.let { secret ->
        AlertDialog(
            onDismissRequest = { createdSecret = null; revealedSecret = null },
            title = { Text("SECRET", fontFamily = PressStart2P, fontSize = 9.sp, color = KvAccent) },
            text = {
                Column {
                    if (createdSecret != null) {
                        Text(
                            "Shown once — store it now. It's saved encrypted for the devices you selected and can be recovered later with SHOW.",
                            fontFamily = VT323,
                            fontSize = 15.sp,
                            color = KvDim,
                            modifier = Modifier.padding(bottom = 10.dp),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF070D07), RoundedCornerShape(3.dp))
                            .border(1.dp, KvFaint, RoundedCornerShape(3.dp))
                            .padding(10.dp),
                    ) {
                        SelectionContainer {
                            Text(secret, fontFamily = VT323, fontSize = 15.sp, color = KvInk)
                        }
                    }
                }
            },
            confirmButton = {
                KvButton(
                    text = "COPY",
                    onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("provisioned key", secret))
                        onToast("copied")
                    },
                )
            },
            dismissButton = {
                KvButtonOutline(text = "CLOSE", onClick = { createdSecret = null; revealedSecret = null })
            },
            containerColor = Color(0xFF0C120C),
            titleContentColor = KvAccent,
            textContentColor = KvInk,
        )
    }
}
