package dev.kv.apk.data

import android.util.Base64
import java.security.KeyFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Canonical device-envelope decryption primitive, shared across screens.
 *
 * A server "envelope" (poll response for an approved session request, or a device-KV /
 * management-key payload) maps 1:1 to [DeviceKvPayload]. The scheme is:
 *   ECDH(this device's P-256 private key, ephemeral pub) -> shared secret
 *   HKDF-SHA256(salt = 32x0x00, info = "kv-device-wrap") -> 32-byte wrap key
 *   AES-256-GCM unwrap the DEK (key = wrap key, nonce = dek_nonce, no AAD)
 *   AES-256-GCM decrypt the body (key = DEK, nonce = nonce, aad = aad)
 *
 * Only P-256 recipients are handled here: this app always registers a secp256r1 device key,
 * so any envelope wrapped to *this* device is P-256. (x25519 recipients exist for kv_cli
 * devices but are never this device.)
 *
 * This is the same primitive previously inlined privately in KvEntriesScreen /
 * ManagementKeysScreen; those copies can be consolidated onto this one as later cleanup.
 */
object DeviceCrypto {
    // P-256 SPKI header (26 bytes): prepend before the 65-byte uncompressed EC point.
    private val P256_SPKI_HEADER = byteArrayOf(
        0x30, 0x59.toByte(),
        0x30, 0x13,
        0x06, 0x07, 0x2a, 0x86.toByte(), 0x48, 0xce.toByte(), 0x3d, 0x02, 0x01,
        0x06, 0x08, 0x2a, 0x86.toByte(), 0x48, 0xce.toByte(), 0x3d, 0x03, 0x01, 0x07,
        0x03, 0x42, 0x00,
    )

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

    /**
     * Decrypt [payload] to plaintext bytes using this device's PKCS#8 (base64) P-256 private key.
     * Caller is responsible for zeroing the returned array once done if it holds a secret.
     */
    fun decryptEnvelopeBytes(privKeyPkcs8B64: String, payload: DeviceKvPayload): ByteArray {
        val kf = KeyFactory.getInstance("EC")
        val myPrivKey = kf.generatePrivate(
            PKCS8EncodedKeySpec(Base64.decode(privKeyPkcs8B64, Base64.DEFAULT))
        )

        val rawEphPub = Base64.decode(payload.recipient.ephemeralPub, Base64.DEFAULT)
        val ephPubKey = kf.generatePublic(X509EncodedKeySpec(P256_SPKI_HEADER + rawEphPub))

        val ka = KeyAgreement.getInstance("ECDH")
        ka.init(myPrivKey)
        ka.doPhase(ephPubKey, true)
        val sharedSecret = ka.generateSecret() // 32-byte x-coordinate for P-256

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

    /** Convenience wrapper returning the plaintext as a UTF-8 string (e.g. a session token). */
    fun decryptEnvelope(privKeyPkcs8B64: String, payload: DeviceKvPayload): String =
        String(decryptEnvelopeBytes(privKeyPkcs8B64, payload), Charsets.UTF_8)
}
