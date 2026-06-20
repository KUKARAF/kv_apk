package dev.kv.apk.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class Prefs(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "kv_apk_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    var token: String
        get() = prefs.getString("token", "") ?: ""
        set(v) { prefs.edit().putString("token", v).apply() }

    var sessionEmail: String
        get() = prefs.getString("session_email", "") ?: ""
        set(v) { prefs.edit().putString("session_email", v).apply() }

    var deviceId: String
        get() = prefs.getString("device_id", "") ?: ""
        set(v) { prefs.edit().putString("device_id", v).apply() }

    var devicePrivKeyPkcs8: String
        get() = prefs.getString("device_priv_key_pkcs8", "") ?: ""
        set(v) { prefs.edit().putString("device_priv_key_pkcs8", v).apply() }

    var devicePubKeySpki: String
        get() = prefs.getString("device_pub_key_spki", "") ?: ""
        set(v) { prefs.edit().putString("device_pub_key_spki", v).apply() }

    fun hasDeviceKey() = deviceId.isNotBlank() && devicePrivKeyPkcs8.isNotBlank()

    fun hasCredentials() = token.isNotBlank()

    fun clear() = prefs.edit().clear().apply()
}
