package com.basakgrape.texcal

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object ApiKeyStore {

    private const val PREF_NAME = "secure_api_prefs"
    private const val KEY_DEEPSEEK_API = "deepseek_api_key"
    private const val KEY_DEEPSEEK_MODEL = "deepseek_model"

    const val DEFAULT_DEEPSEEK_MODEL = "deepseek-chat"

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

    fun saveDeepSeekKey(context: Context, key: String) {
        prefs(context).edit()
            .putString(KEY_DEEPSEEK_API, key.trim())
            .apply()
    }

    fun getDeepSeekKey(context: Context): String? {
        return prefs(context).getString(KEY_DEEPSEEK_API, null)
    }

    fun hasDeepSeekKey(context: Context): Boolean {
        return !getDeepSeekKey(context).isNullOrBlank()
    }

    fun clearDeepSeekKey(context: Context) {
        prefs(context).edit()
            .remove(KEY_DEEPSEEK_API)
            .apply()
    }

    fun saveDeepSeekModel(context: Context, model: String) {
        val normalized = model.trim().ifBlank { DEFAULT_DEEPSEEK_MODEL }
        prefs(context).edit()
            .putString(KEY_DEEPSEEK_MODEL, normalized)
            .apply()
    }

    fun getDeepSeekModel(context: Context): String {
        val stored = prefs(context).getString(KEY_DEEPSEEK_MODEL, DEFAULT_DEEPSEEK_MODEL)
        return stored?.ifBlank { DEFAULT_DEEPSEEK_MODEL } ?: DEFAULT_DEEPSEEK_MODEL
    }

    // Backward-compatible aliases for older call sites.
    fun saveKey(context: Context, key: String) = saveDeepSeekKey(context, key)
    fun getKey(context: Context): String? = getDeepSeekKey(context)
    fun hasKey(context: Context): Boolean = hasDeepSeekKey(context)
    fun clearKey(context: Context) = clearDeepSeekKey(context)
}
