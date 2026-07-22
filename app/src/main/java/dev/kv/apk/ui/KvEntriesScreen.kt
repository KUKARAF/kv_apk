package dev.kv.apk.ui

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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kv.apk.data.CreateKvRequest
import dev.kv.apk.data.DeviceItem
import dev.kv.apk.data.DeviceKvPayload
import dev.kv.apk.data.DeviceKvRecipientRequest
import dev.kv.apk.data.KvApi
import dev.kv.apk.data.KvEntryItem
import dev.kv.apk.data.ManagementKeyRow
import dev.kv.apk.data.OpenRouterApi
import dev.kv.apk.data.OpenRouterCreateKeyRequest
import dev.kv.apk.data.Prefs
import dev.kv.apk.data.ReEncryptRequest
import dev.kv.apk.data.buildOpenRouterApi
import dev.kv.apk.ui.theme.KvAccent
import dev.kv.apk.ui.theme.KvBg
import dev.kv.apk.ui.theme.KvDanger
import dev.kv.apk.ui.theme.KvDim
import dev.kv.apk.ui.theme.KvFaint
import dev.kv.apk.ui.theme.KvInk
import dev.kv.apk.ui.theme.KvOrange
import dev.kv.apk.ui.theme.PressStart2P
import dev.kv.apk.ui.theme.VT323
import kotlinx.coroutines.launch
import android.os.Build
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.NamedParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

// HKDF-SHA256

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

// AES-256-GCM decrypt/encrypt

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

private fun encryptForDevices(
    plaintext: String,
    entryKey: String,
    devices: List<DeviceItem>,
): ReEncryptRequest {
    val rng = SecureRandom()

    val dek = ByteArray(32).also { rng.nextBytes(it) }
    val nonce = ByteArray(12).also { rng.nextBytes(it) }
    val aadBytes = "device-kv:$entryKey".toByteArray(Charsets.UTF_8)
    val ciphertext = aesGcmEncrypt(dek, nonce, plaintext.toByteArray(Charsets.UTF_8), aadBytes)

    val recipients = devices.mapNotNull { device ->
        try {
            val pubKeyBytes = Base64.decode(device.publicKey ?: return@mapNotNull null, Base64.DEFAULT)
            val keyType = device.keyType ?: "p256"
            val (sharedSecret, rawEphPub) = ecdhWrap(keyType, pubKeyBytes)

            val wrapKey = hkdf(
                secret = sharedSecret,
                salt = ByteArray(32),
                info = "kv-device-wrap".toByteArray(Charsets.UTF_8),
                length = 32,
            )

            val dekNonce = ByteArray(12).also { rng.nextBytes(it) }
            val encryptedDek = aesGcmEncrypt(wrapKey, dekNonce, dek, ByteArray(0))

            DeviceKvRecipientRequest(
                deviceId = device.id,
                keyType = keyType,
                ephemeralPub = Base64.encodeToString(rawEphPub, Base64.NO_WRAP),
                dekNonce = Base64.encodeToString(dekNonce, Base64.NO_WRAP),
                encryptedDek = Base64.encodeToString(encryptedDek, Base64.NO_WRAP),
            )
        } catch (e: Exception) {
            // Skip a device we can't encrypt for (unsupported key type, malformed key,
            // Android too old for X25519) rather than failing the whole request.
            null
        }
    }

    if (recipients.isEmpty()) {
        throw IllegalStateException("failed to encrypt for any selected device")
    }

    return ReEncryptRequest(
        key = entryKey,
        nonce = Base64.encodeToString(nonce, Base64.NO_WRAP),
        ciphertext = Base64.encodeToString(ciphertext, Base64.NO_WRAP),
        aad = Base64.encodeToString(aadBytes, Base64.NO_WRAP),
        recipients = recipients,
    )
}

// P-256 SPKI header (26 bytes): prepend before the 65-byte uncompressed EC point
private val P256_SPKI_HEADER = byteArrayOf(
    0x30, 0x59.toByte(),
    0x30, 0x13,
    0x06, 0x07, 0x2a, 0x86.toByte(), 0x48, 0xce.toByte(), 0x3d, 0x02, 0x01,
    0x06, 0x08, 0x2a, 0x86.toByte(), 0x48, 0xce.toByte(), 0x3d, 0x03, 0x01, 0x07,
    0x03, 0x42, 0x00,
)

// X25519 SPKI header (12 bytes, RFC 8410): prepend before the raw 32-byte point.
// Registered via kv_cli (key_type "x25519"), whose public_key is stored raw, unlike
// P-256 devices whose public_key is already SPKI-DER-encoded.
private val X25519_SPKI_HEADER = byteArrayOf(
    0x30, 0x2a,
    0x30, 0x05,
    0x06, 0x03, 0x2b, 0x65.toByte(), 0x6e,
    0x03, 0x21, 0x00,
)

// ECDH for a device's registered public key, keyed off its key_type — "p256" (SPKI-DER-encoded
// public_key, matches this device's own registration) or "x25519" (raw 32-byte public_key,
// registered via kv_cli — kv_apk isn't the only client). Returns (sharedSecret, rawEphemeralPub).
private fun ecdhWrap(keyType: String, devicePubKeyBytes: ByteArray): Pair<ByteArray, ByteArray> {
    return when (keyType) {
        "x25519" -> {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                throw UnsupportedOperationException("X25519 requires Android 9 (API 28) or newer")
            }
            val devicePubKey = KeyFactory.getInstance("XDH")
                .generatePublic(X509EncodedKeySpec(X25519_SPKI_HEADER + devicePubKeyBytes))
            val ephKp = KeyPairGenerator.getInstance("XDH")
                .apply { initialize(NamedParameterSpec.X25519) }
                .generateKeyPair()
            val ka = KeyAgreement.getInstance("XDH")
            ka.init(ephKp.private)
            ka.doPhase(devicePubKey, true)
            val sharedSecret = ka.generateSecret()
            val rawEphPub = ephKp.public.encoded.drop(X25519_SPKI_HEADER.size).toByteArray()
            sharedSecret to rawEphPub
        }
        "p256" -> {
            val devicePubKey = KeyFactory.getInstance("EC")
                .generatePublic(X509EncodedKeySpec(devicePubKeyBytes))
            val ephKp = KeyPairGenerator.getInstance("EC")
                .apply { initialize(ECGenParameterSpec("secp256r1")) }
                .generateKeyPair()
            val ka = KeyAgreement.getInstance("ECDH")
            ka.init(ephKp.private)
            ka.doPhase(devicePubKey, true)
            val sharedSecret = ka.generateSecret()
            val rawEphPub = ephKp.public.encoded.drop(P256_SPKI_HEADER.size).toByteArray()
            sharedSecret to rawEphPub
        }
        else -> throw UnsupportedOperationException("unsupported device key type: $keyType")
    }
}

private fun decryptDeviceKv(
    privKeyPkcs8B64: String,
    nonce: String,
    ciphertext: String,
    aad: String,
    ephemeralPub: String,
    dekNonce: String,
    encryptedDek: String,
): String {
    val kf = KeyFactory.getInstance("EC")

    // Reconstruct private key
    val myPrivKey = kf.generatePrivate(
        PKCS8EncodedKeySpec(Base64.decode(privKeyPkcs8B64, Base64.DEFAULT))
    )

    // Parse ephemeral public key: raw 65-byte uncompressed point → wrap in SPKI
    val rawEphPub = Base64.decode(ephemeralPub, Base64.DEFAULT)
    val spki = P256_SPKI_HEADER + rawEphPub
    val ephPubKey = kf.generatePublic(X509EncodedKeySpec(spki))

    // ECDH
    val ka = KeyAgreement.getInstance("ECDH")
    ka.init(myPrivKey)
    ka.doPhase(ephPubKey, true)
    val sharedSecret = ka.generateSecret() // 32-byte x-coordinate for P-256

    // HKDF → wrap key
    val wrapKey = hkdf(
        secret = sharedSecret,
        salt = ByteArray(32),
        info = "kv-device-wrap".toByteArray(Charsets.UTF_8),
        length = 32,
    )

    // Unwrap DEK
    val dek = aesGcmDecrypt(
        key = wrapKey,
        nonce = Base64.decode(dekNonce, Base64.DEFAULT),
        ciphertext = Base64.decode(encryptedDek, Base64.DEFAULT),
        aad = ByteArray(0),
    )

    // Decrypt body
    val plaintext = aesGcmDecrypt(
        key = dek,
        nonce = Base64.decode(nonce, Base64.DEFAULT),
        ciphertext = Base64.decode(ciphertext, Base64.DEFAULT),
        aad = Base64.decode(aad, Base64.DEFAULT),
    )

    return String(plaintext, Charsets.UTF_8)
}

private fun ByteArray.zero() {
    java.util.Arrays.fill(this, 0)
}

// Byte-array variant of decryptDeviceKv, for secrets (a management key) that are only ever
// needed as a single short-lived Authorization header value, never displayed — callers must
// zero() the result in a finally block once done, same discipline as ManagementKeysScreen.kt.
private fun decryptDeviceKvBytes(privKeyPkcs8B64: String, payload: DeviceKvPayload): ByteArray {
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

    val dek = aesGcmDecrypt(
        key = wrapKey,
        nonce = Base64.decode(payload.recipient.dekNonce, Base64.DEFAULT),
        ciphertext = Base64.decode(payload.recipient.encryptedDek, Base64.DEFAULT),
        aad = ByteArray(0),
    )

    return aesGcmDecrypt(
        key = dek,
        nonce = Base64.decode(payload.nonce, Base64.DEFAULT),
        ciphertext = Base64.decode(payload.ciphertext, Base64.DEFAULT),
        aad = Base64.decode(payload.aad, Base64.DEFAULT),
    )
}

private suspend fun decryptManagementKeyBytes(api: KvApi, prefs: Prefs, managementKeyId: String): ByteArray {
    val payload = api.getManagementKeyEnvelope(managementKeyId, prefs.deviceId)
    return decryptDeviceKvBytes(prefs.devicePrivKeyPkcs8, payload)
}

// Android's INTERNET permission has no per-host allowlist, so this is the enforcement point:
// a management key's decrypted secret is only ever sent to the host registered for its own
// `provider` field, never to an arbitrary or mismatched provider's API.
private fun providerApiFor(provider: String): OpenRouterApi? = when (provider) {
    "openrouter" -> buildOpenRouterApi()
    else -> null
}

private fun providerDisplayName(provider: String): String = when (provider) {
    "openrouter" -> "OpenRouter"
    else -> provider
}

@Composable
fun KvEntriesScreen(
    api: KvApi,
    prefs: Prefs,
    onBack: () -> Unit,
    onLogout: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    var entries by remember { mutableStateOf<List<KvEntryItem>>(emptyList()) }
    var toast by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }

    var kvKey by remember { mutableStateOf("") }
    var kvValue by remember { mutableStateOf("") }
    var kvTtl by remember { mutableStateOf("") }
    var kvSliding by remember { mutableStateOf(false) }
    var kvOpenAccess by remember { mutableStateOf(false) }
    var kvOneTime by remember { mutableStateOf(false) }
    var kvApproval by remember { mutableStateOf(false) }
    var kvZeroTrust by remember { mutableStateOf(false) }

    // Generate the value from a provider via a stored management key, instead of typing it.
    var managementKeys by remember { mutableStateOf<List<ManagementKeyRow>>(emptyList()) }
    var generateViaManagementKeyId by remember { mutableStateOf<String?>(null) }
    var generateBusy by remember { mutableStateOf(false) }
    var generateError by remember { mutableStateOf("") }

    var genLinkDesc by remember { mutableStateOf("") }

    var importText by remember { mutableStateOf("") }

    // Decrypted values: key → plaintext (or error string)
    var decryptedValues by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var decryptingKey by remember { mutableStateOf<String?>(null) }

    // Manage device access dialog
    var manageDevicesEntry by remember { mutableStateOf<KvEntryItem?>(null) }
    var allDevices by remember { mutableStateOf<List<DeviceItem>>(emptyList()) }
    var selectedDeviceIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var reEncryptBusy by remember { mutableStateOf(false) }
    var reEncryptError by remember { mutableStateOf("") }

    fun loadEntries() {
        scope.launch {
            try {
                entries = api.listKvEntries()
            } catch (e: retrofit2.HttpException) {
                if (e.code() == 401) onLogout()
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(Unit) {
        loadEntries()
        try { managementKeys = api.listManagementKeys() } catch (_: Exception) {}
    }

    val filtered = if (search.isBlank()) entries
    else entries.filter { it.key.uppercase().startsWith(search.uppercase()) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(KvBg),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp)) {
                KvScreenHeader(title = "KV ENTRIES", onBack = onBack)
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp),
            ) {
                item {
                    KvCard(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(15.dp)) {
                            KvSectionTitle("ADD / UPDATE ENTRY")

                            KvLabel("KEY")
                            KvInput(
                                value = kvKey,
                                onValueChange = { kvKey = it },
                                placeholder = "DATABASE_URL",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 13.dp),
                            )

                            KvLabel("VALUE")
                            KvInput(
                                value = if (generateViaManagementKeyId != null) "" else kvValue,
                                onValueChange = { kvValue = it },
                                placeholder = if (generateViaManagementKeyId != null)
                                    "will be generated on save…" else "the value…",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 13.dp),
                                singleLine = false,
                                maxLines = 4,
                                fontSize = 17,
                                enabled = generateViaManagementKeyId == null,
                            )

                            val activeManagementKeys = managementKeys.filter { it.status == "active" }
                            if (activeManagementKeys.isNotEmpty()) {
                                Column(
                                    modifier = Modifier.padding(bottom = 13.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    KvLabel("GENERATE VALUE VIA")
                                    activeManagementKeys.forEach { mk ->
                                        KvCheckbox(
                                            checked = generateViaManagementKeyId == mk.id,
                                            label = providerDisplayName(mk.provider) +
                                                if (activeManagementKeys.size > 1) " (${mk.label})" else "",
                                            onToggle = {
                                                generateError = ""
                                                generateViaManagementKeyId =
                                                    if (generateViaManagementKeyId == mk.id) null else mk.id
                                            },
                                        )
                                    }
                                    if (generateError.isNotBlank()) {
                                        Text(generateError, fontFamily = VT323, fontSize = 15.sp, color = KvDanger)
                                    }
                                }
                            }

                            KvLabel("TTL (H)")
                            KvInput(
                                value = kvTtl,
                                onValueChange = { kvTtl = it },
                                placeholder = "—",
                                modifier = Modifier.padding(bottom = 15.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            )

                            Column(
                                modifier = Modifier.padding(bottom = 17.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                                    KvCheckbox(
                                        checked = kvSliding,
                                        label = "Sliding",
                                        onToggle = { kvSliding = !kvSliding },
                                    )
                                    KvCheckbox(
                                        checked = kvOpenAccess,
                                        label = "Open access",
                                        onToggle = { kvOpenAccess = !kvOpenAccess },
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                                    KvCheckbox(
                                        checked = kvOneTime,
                                        label = "One-time",
                                        onToggle = { kvOneTime = !kvOneTime },
                                    )
                                    KvCheckbox(
                                        checked = kvApproval,
                                        label = "Approval",
                                        onToggle = { kvApproval = !kvApproval },
                                        color = KvOrange,
                                    )
                                }
                                KvCheckbox(
                                    checked = kvZeroTrust,
                                    label = "Zero Trust",
                                    onToggle = { kvZeroTrust = !kvZeroTrust },
                                    color = KvOrange,
                                )
                            }

                            KvButton(
                                text = if (generateBusy) "…" else "SAVE",
                                enabled = !generateBusy && kvKey.isNotBlank() &&
                                    (kvValue.isNotBlank() || generateViaManagementKeyId != null),
                                onClick = {
                                    scope.launch {
                                        generateError = ""
                                        val mgmtKeyId = generateViaManagementKeyId
                                        val valueToSave: String
                                        if (mgmtKeyId != null) {
                                            generateBusy = true
                                            val mk = managementKeys.find { it.id == mgmtKeyId }
                                            val provider = mk?.let { providerApiFor(it.provider) }
                                            if (mk == null || provider == null) {
                                                generateError = "unsupported provider"
                                                generateBusy = false
                                                return@launch
                                            }
                                            val generated = try {
                                                val mgmtSecretBytes =
                                                    decryptManagementKeyBytes(api, prefs, mgmtKeyId)
                                                try {
                                                    provider.createKey(
                                                        "Bearer " + String(mgmtSecretBytes, Charsets.UTF_8),
                                                        OpenRouterCreateKeyRequest(name = kvKey.trim()),
                                                    ).key
                                                } finally {
                                                    mgmtSecretBytes.zero()
                                                }
                                            } catch (e: retrofit2.HttpException) {
                                                if (e.code() == 401) { onLogout(); generateBusy = false; return@launch }
                                                generateError = "provider error ${e.code()}"
                                                generateBusy = false
                                                return@launch
                                            } catch (e: Exception) {
                                                generateError = e.message ?: "failed to generate key"
                                                generateBusy = false
                                                return@launch
                                            }
                                            valueToSave = generated
                                        } else {
                                            valueToSave = kvValue
                                        }

                                        try {
                                            api.setKvEntry(
                                                CreateKvRequest(
                                                    key = kvKey.trim(),
                                                    value = valueToSave,
                                                    ttl = kvTtl.toIntOrNull(),
                                                    sliding = kvSliding,
                                                    openAccess = kvOpenAccess,
                                                    oneTime = kvOneTime,
                                                    approval = kvApproval,
                                                    zeroTrust = kvZeroTrust,
                                                )
                                            )
                                            toast = "saved ${kvKey.trim()}"
                                            kvKey = ""
                                            kvValue = ""
                                            generateViaManagementKeyId = null
                                            loadEntries()
                                        } catch (e: retrofit2.HttpException) {
                                            if (e.code() == 401) onLogout()
                                            else toast = "error ${e.code()}"
                                        } catch (e: Exception) {
                                            toast = e.message ?: "error"
                                        } finally {
                                            generateBusy = false
                                        }
                                    }
                                },
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                }

                item {
                    Row(modifier = Modifier.padding(bottom = 6.dp)) {
                        KvInput(
                            value = search,
                            onValueChange = { search = it },
                            placeholder = "Filter by prefix",
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp)
                            .padding(top = 14.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("KEY", fontFamily = PressStart2P, fontSize = 8.sp, color = KvDim)
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(KvFaint),
                    )
                }

                items(filtered, key = { it.key }) { entry ->
                    EntryRow(
                        item = entry,
                        decryptedValue = decryptedValues[entry.key],
                        isDecrypting = decryptingKey == entry.key,
                        canDecrypt = prefs.hasDeviceKey(),
                        onDecrypt = {
                            scope.launch {
                                decryptingKey = entry.key
                                try {
                                    val payload = api.getDeviceKv(prefs.deviceId, entry.key)
                                    val plaintext = decryptDeviceKv(
                                        privKeyPkcs8B64 = prefs.devicePrivKeyPkcs8,
                                        nonce = payload.nonce,
                                        ciphertext = payload.ciphertext,
                                        aad = payload.aad,
                                        ephemeralPub = payload.recipient.ephemeralPub,
                                        dekNonce = payload.recipient.dekNonce,
                                        encryptedDek = payload.recipient.encryptedDek,
                                    )
                                    decryptedValues = decryptedValues + (entry.key to plaintext)
                                } catch (e: retrofit2.HttpException) {
                                    if (e.code() == 401) onLogout()
                                    else if (e.code() == 404) decryptedValues = decryptedValues + (entry.key to
                                        "This device no longer has access to this entry.\nAnother authorized device must re-grant access via the DEVICES button.")
                                    else decryptedValues = decryptedValues + (entry.key to "error ${e.code()}")
                                } catch (e: Exception) {
                                    decryptedValues = decryptedValues + (entry.key to "decrypt failed: ${e.message}")
                                } finally {
                                    decryptingKey = null
                                }
                            }
                        },
                        onDismissDecrypted = {
                            decryptedValues = decryptedValues - entry.key
                        },
                        onManageDevices = {
                            scope.launch {
                                try {
                                    allDevices = api.listDevices()
                                } catch (_: Exception) {}
                                selectedDeviceIds = emptySet()
                                reEncryptError = ""
                                manageDevicesEntry = entry
                            }
                        },
                    )
                }

                item {
                    Spacer(Modifier.height(18.dp))
                    KvCard(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(15.dp)) {
                            KvSectionTitle("GENERATE REQUEST LINK")
                            Text(
                                "Share a one-time link so someone can provide you with secrets. Deactivates after first submission.",
                                fontFamily = VT323,
                                fontSize = 16.sp,
                                color = KvDim,
                                lineHeight = 19.sp,
                                modifier = Modifier.padding(bottom = 13.dp),
                            )
                            KvInput(
                                value = genLinkDesc,
                                onValueChange = { genLinkDesc = it },
                                placeholder = "Description (shown to user)",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 9.dp),
                                fontSize = 17,
                            )
                            KvButton(
                                text = "GENERATE LINK",
                                onClick = { toast = "request link generated" },
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                }

                item {
                    KvCard(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(15.dp)) {
                            KvSectionTitle("IMPORT .ENV")
                            KvInput(
                                value = importText,
                                onValueChange = { importText = it },
                                placeholder = "KEY=value\nANOTHER=value",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 11.dp),
                                singleLine = false,
                                maxLines = 5,
                                fontSize = 17,
                            )
                            KvButton(
                                text = "IMPORT",
                                enabled = importText.isNotBlank(),
                                onClick = {
                                    scope.launch {
                                        val lines = importText.lines()
                                            .map { it.trim() }
                                            .filter { it.contains('=') && !it.startsWith('#') }
                                        var imported = 0
                                        for (line in lines) {
                                            val idx = line.indexOf('=')
                                            val k = line.substring(0, idx).trim()
                                            val v = line.substring(idx + 1).trim()
                                            if (k.isNotEmpty()) {
                                                try {
                                                    api.setKvEntry(
                                                        CreateKvRequest(key = k, value = v)
                                                    )
                                                    imported++
                                                } catch (_: Exception) {}
                                            }
                                        }
                                        toast = ".env imported ($imported keys)"
                                        importText = ""
                                        loadEntries()
                                    }
                                },
                            )
                        }
                    }
                    Spacer(Modifier.height(80.dp))
                }
            }
        }

        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
            KvToast(toast)
        }

        ScanlineOverlay()

        manageDevicesEntry?.let { entry ->
            ManageDevicesDialog(
                entry = entry,
                devices = allDevices.filter { it.publicKey != null },
                selectedDeviceIds = selectedDeviceIds,
                onToggle = { id ->
                    selectedDeviceIds = if (id in selectedDeviceIds)
                        selectedDeviceIds - id else selectedDeviceIds + id
                },
                busy = reEncryptBusy,
                error = reEncryptError,
                onConfirm = {
                    scope.launch {
                        reEncryptBusy = true
                        reEncryptError = ""
                        try {
                            val payload = api.getDeviceKv(prefs.deviceId, entry.key)
                            val plaintext = decryptDeviceKv(
                                privKeyPkcs8B64 = prefs.devicePrivKeyPkcs8,
                                nonce = payload.nonce,
                                ciphertext = payload.ciphertext,
                                aad = payload.aad,
                                ephemeralPub = payload.recipient.ephemeralPub,
                                dekNonce = payload.recipient.dekNonce,
                                encryptedDek = payload.recipient.encryptedDek,
                            )
                            val selected = allDevices.filter { it.id in selectedDeviceIds }
                            val request = encryptForDevices(plaintext, entry.key, selected)
                            api.setDeviceKvEntry(request)
                            toast = "re-encrypted ${entry.key}"
                            manageDevicesEntry = null
                        } catch (e: retrofit2.HttpException) {
                            if (e.code() == 401) onLogout()
                            else if (e.code() == 404) reEncryptError =
                                "This device no longer has access to this entry. Another authorized device must re-grant access first."
                            else reEncryptError = "error ${e.code()}"
                        } catch (e: Exception) {
                            reEncryptError = e.message ?: "failed"
                        } finally {
                            reEncryptBusy = false
                        }
                    }
                },
                onDismiss = { manageDevicesEntry = null },
            )
        }
    }
}

@Composable
private fun EntryRow(
    item: KvEntryItem,
    decryptedValue: String?,
    isDecrypting: Boolean,
    canDecrypt: Boolean,
    onDecrypt: () -> Unit,
    onDismissDecrypted: () -> Unit,
    onManageDevices: () -> Unit,
) {
    // Dialog showing the decrypted plaintext
    if (decryptedValue != null) {
        AlertDialog(
            onDismissRequest = onDismissDecrypted,
            title = {
                Text(
                    item.key,
                    fontFamily = PressStart2P,
                    fontSize = 9.sp,
                    color = KvAccent,
                )
            },
            text = {
                Column {
                    Text(
                        "PLAINTEXT",
                        fontFamily = PressStart2P,
                        fontSize = 7.sp,
                        color = KvDim,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF070D07), RoundedCornerShape(3.dp))
                            .border(1.dp, KvFaint, RoundedCornerShape(3.dp))
                            .padding(10.dp),
                    ) {
                        Text(
                            decryptedValue,
                            fontFamily = VT323,
                            fontSize = 17.sp,
                            color = KvInk,
                        )
                    }
                }
            },
            confirmButton = {
                KvButton(text = "CLOSE", onClick = onDismissDecrypted)
            },
            containerColor = Color(0xFF0C120C),
            titleContentColor = KvAccent,
            textContentColor = KvInk,
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.key, fontFamily = VT323, fontSize = 18.sp, color = KvInk, lineHeight = 18.sp)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    if (item.zt) {
                        Text(
                            "ZERO TRUST",
                            fontFamily = PressStart2P,
                            fontSize = 6.sp,
                            color = KvOrange,
                            modifier = Modifier
                                .background(KvOrange.copy(alpha = 0.1f), RoundedCornerShape(3.dp))
                                .border(1.dp, KvOrange.copy(alpha = 0.4f), RoundedCornerShape(3.dp))
                                .padding(horizontal = 5.dp, vertical = 3.dp),
                        )
                    }
                    if (item.deviceEncrypted) {
                        Text(
                            "DEVICE ENC",
                            fontFamily = PressStart2P,
                            fontSize = 6.sp,
                            color = KvAccent,
                            modifier = Modifier
                                .background(KvAccent.copy(alpha = 0.08f), RoundedCornerShape(3.dp))
                                .border(1.dp, KvAccent.copy(alpha = 0.35f), RoundedCornerShape(3.dp))
                                .padding(horizontal = 5.dp, vertical = 3.dp),
                        )
                    }
                }
            }
        }

        if (item.deviceEncrypted) {
            if (canDecrypt) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KvButtonOutline(
                        text = if (isDecrypting) "…" else "DECRYPT",
                        onClick = onDecrypt,
                        enabled = !isDecrypting,
                        color = KvAccent,
                    )
                    KvButtonOutline(
                        text = "DEVICES",
                        onClick = onManageDevices,
                        color = KvOrange,
                    )
                }
            } else {
                Spacer(Modifier.height(6.dp))
                Text(
                    "No device key — register in Devices to decrypt",
                    fontFamily = VT323,
                    fontSize = 14.sp,
                    color = KvDim,
                )
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(KvFaint),
    )
}

@Composable
private fun ManageDevicesDialog(
    entry: KvEntryItem,
    devices: List<DeviceItem>,
    selectedDeviceIds: Set<String>,
    onToggle: (String) -> Unit,
    busy: Boolean,
    error: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = {
            Text(
                "MANAGE DEVICES",
                fontFamily = PressStart2P,
                fontSize = 9.sp,
                color = KvAccent,
            )
        },
        text = {
            Column {
                Text(
                    entry.key,
                    fontFamily = VT323,
                    fontSize = 17.sp,
                    color = KvDim,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                if (devices.isEmpty()) {
                    Text(
                        "No devices with public keys registered.",
                        fontFamily = VT323,
                        fontSize = 16.sp,
                        color = KvDim,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        devices.forEach { device ->
                            KvCheckbox(
                                checked = device.id in selectedDeviceIds,
                                label = device.name,
                                onToggle = { onToggle(device.id) },
                            )
                        }
                    }
                }
                if (error.isNotBlank()) {
                    Text(
                        error,
                        fontFamily = VT323,
                        fontSize = 15.sp,
                        color = KvDanger,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
        },
        confirmButton = {
            KvButton(
                text = if (busy) "…" else "RE-ENCRYPT",
                onClick = onConfirm,
                enabled = !busy && selectedDeviceIds.isNotEmpty(),
            )
        },
        dismissButton = {
            KvButtonOutline(
                text = "CANCEL",
                onClick = onDismiss,
                enabled = !busy,
            )
        },
        containerColor = Color(0xFF0C120C),
        titleContentColor = KvAccent,
        textContentColor = KvInk,
    )
}
