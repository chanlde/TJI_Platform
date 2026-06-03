package com.tji.device.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object SecurePrefs {
    private const val TAG = "SecurePrefs"
    private const val LEGACY_USER_PREFS = "user_preferences"
    private const val SECURE_USER_PREFS = "user_preferences_secure"

    fun userPreferences(context: Context): SharedPreferences {
        val appContext = context.applicationContext
        val legacyPrefs = appContext.getSharedPreferences(LEGACY_USER_PREFS, Context.MODE_PRIVATE)

        return runCatching {
            val masterKey = MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                appContext,
                SECURE_USER_PREFS,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            ).also { securePrefs ->
                migrateLegacyLoginPrefs(legacyPrefs, securePrefs)
            }
        }.getOrElse { throwable ->
            Log.w(TAG, "加密偏好初始化失败，回退到普通 SharedPreferences", throwable)
            legacyPrefs
        }
    }

    private fun migrateLegacyLoginPrefs(
        legacyPrefs: SharedPreferences,
        securePrefs: SharedPreferences
    ) {
        if (!legacyPrefs.getBoolean("rememberMe", false)) return
        if (securePrefs.getBoolean("rememberMe", false)) return

        securePrefs.edit()
            .putString("account", legacyPrefs.getString("account", ""))
            .putString("password", legacyPrefs.getString("password", ""))
            .putBoolean("rememberMe", true)
            .apply()

        legacyPrefs.edit().clear().apply()
    }
}
