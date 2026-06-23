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
import dev.kv.apk.data.DeviceKvRecipientRequest
import dev.kv.apk.data.KvApi
import dev.kv.apk.data.KvEntryItem
import dev.kv.apk.data.Prefs
import dev.kv.apk.data.ReEncryptRequest
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
    scope: String,
    devices: List<DeviceItem>,
): ReEncryptRequest {
    val rng = SecureRandom()
    val kf = KeyFactory.getInstance("EC")
    val kpg = KeyPairGenerator.getInstance("EC")
    kpg.initialize(ECGenParameterSpec("secp256r1"))

    val dek = ByteArray(32).also { rng.nextBytes(it) }
    val nonce = ByteArray(12).also { rng.nextBytes(it) }
    val aadBytes = "device-kv:$entryKey".toByteArray(Charsets.UTF_8)
    val ciphertext = aesGcmEncrypt(dek, nonce, plaintext.toByteArray(Charsets.UTF_8), aadBytes)

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

        val dekNonce = ByteArray(12).also { rng.nextBytes(it) }
        val encryptedDek = aesGcmEncrypt(wrapKey, dekNonce, dek, ByteArray(0))

        // SPKI-encoded public key: strip the 26-byte header to get the raw 65-byte point
        val rawEphPub = ephKp.public.encoded.drop(P256_SPKI_HEADER.size).toByteArray()

        DeviceKvRecipientRequest(
            deviceId = device.id,
            keyType = device.keyType ?: "p256",
            ephemeralPub = Base64.encodeToString(rawEphPub, Base64.NO_WRAP),
            dekNonce = Base64.encodeToString(dekNonce, Base64.NO_WRAP),
            encryptedDek = Base64.encodeToString(encryptedDek, Base64.NO_WRAP),
        )
    }

    return ReEncryptRequest(
        key = entryKey,
        scope = scope,
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
    var kvScope by remember { mutableStateOf("") }
    var kvValue by remember { mutableStateOf("") }
    var kvTtl by remember { mutableStateOf("") }
    var kvSliding by remember { mutableStateOf(false) }
    var kvOpenAccess by remember { mutableStateOf(false) }
    var kvOneTime by remember { mutableStateOf(false) }
    var kvApproval by remember { mutableStateOf(false) }
    var kvZeroTrust by remember { mutableStateOf(false) }

    var genLinkDesc by remember { mutableStateOf("") }
    var genLinkScope by remember { mutableStateOf("") }

    var importText by remember { mutableStateOf("") }
    var importScope by remember { mutableStateOf("") }

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

    LaunchedEffect(Unit) { loadEntries() }

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

                            KvLabel("SCOPE")
                            KvInput(
                                value = kvScope,
                                onValueChange = { kvScope = it },
                                placeholder = "osmosis/deployment",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 13.dp),
                            )

                            KvLabel("VALUE")
                            KvInput(
                                value = kvValue,
                                onValueChange = { kvValue = it },
                                placeholder = "the value…",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 13.dp),
                                singleLine = false,
                                maxLines = 4,
                                fontSize = 17,
                            )

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
                                text = "SAVE",
                                enabled = kvKey.isNotBlank() && kvValue.isNotBlank(),
                                onClick = {
                                    scope.launch {
                                        try {
                                            api.setKvEntry(
                                                CreateKvRequest(
                                                    key = kvKey.trim(),
                                                    scope = kvScope.trim(),
                                                    value = kvValue,
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
                                            loadEntries()
                                        } catch (e: retrofit2.HttpException) {
                                            if (e.code() == 401) onLogout()
                                            else toast = "error ${e.code()}"
                                        } catch (e: Exception) {
                                            toast = e.message ?: "error"
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
                        Text("SCOPE", fontFamily = PressStart2P, fontSize = 8.sp, color = KvDim)
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(KvFaint),
                    )
                }

                items(filtered, key = { "${it.key}|${it.scope}" }) { entry ->
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
                            KvInput(
                                value = genLinkScope,
                                onValueChange = { genLinkScope = it },
                                placeholder = "Scope  e.g. myapp/production",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 14.dp),
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
                            KvInput(
                                value = importScope,
                                onValueChange = { importScope = it },
                                placeholder = "Scope",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 14.dp),
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
                                                        CreateKvRequest(key = k, scope = importScope.trim(), value = v)
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
                            val request = encryptForDevices(plaintext, entry.key, entry.scope ?: "", selected)
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
            if (!item.scope.isNullOrEmpty()) {
                KvChip(item.scope)
            } else {
                Text("+ scope", fontFamily = VT323, fontSize = 15.sp, color = KvFaint)
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
