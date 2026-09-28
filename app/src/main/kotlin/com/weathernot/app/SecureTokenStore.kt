package com.weathernot.app

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.weathernot.kit.networking.TokenStoring

/**
 * The session token is a credential, so it lives in Keystore-backed
 * EncryptedSharedPreferences, never plain SharedPreferences.
 */
class SecureTokenStore(context: Context) : TokenStoring {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "com.weathernot.app.session",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    private val key = "session-token"

    override fun token(): String? = prefs.getString(key, null)

    override fun save(token: String) {
        prefs.edit().putString(key, token).apply()
    }

    override fun clear() {
        prefs.edit().remove(key).apply()
    }
}
