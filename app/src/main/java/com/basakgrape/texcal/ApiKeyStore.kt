package com.basakgrape.texcal

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object ApiKeyStore {

    private const val PREF_NAME = "secure_api_prefs"
    private const val KEY_OPENAI = "openai_api_key"

    private fun prefs(context: Context) =
        EncryptedSharedPreferences.create(
            context,
            PREF_NAME,
            MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

    fun saveKey(context: Context, key: String) {
        prefs(context).edit()
            .putString(KEY_OPENAI, key.trim())
            .apply()
    }

    fun getKey(context: Context): String? {
        return prefs(context).getString(KEY_OPENAI, null)
    }

    fun hasKey(context: Context): Boolean {
        return !getKey(context).isNullOrBlank()
    }

    fun clearKey(context: Context) {
        prefs(context).edit()
            .remove(KEY_OPENAI)
            .apply()
    }
}
