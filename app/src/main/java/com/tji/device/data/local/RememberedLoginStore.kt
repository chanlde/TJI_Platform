package com.tji.device.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.annotation.WorkerThread
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

data class RememberedLoginCredentials(
    val account: String,
    val password: String
)

/**
 * 管理“记住登录”的读取、保存、清除及旧明文偏好迁移。
 *
 * UI 只依赖这里的业务方法，不需要知道偏好文件名和字段键。
 */
class RememberedLoginStore internal constructor(
    private val preferences: SharedPreferences
) {
    fun load(): RememberedLoginCredentials? {
        return runCatching {
            if (!preferences.getBoolean(KEY_REMEMBER_ME, false)) return null
            RememberedLoginCredentials(
                account = preferences.getString(KEY_ACCOUNT, "").orEmpty(),
                password = preferences.getString(KEY_PASSWORD, "").orEmpty()
            )
        }.onFailure { throwable ->
            Log.w(TAG, "读取记住登录信息失败，忽略损坏的偏好数据", throwable)
        }.getOrNull()
    }

    fun save(account: String, password: String) {
        preferences.edit {
            putString(KEY_ACCOUNT, account)
            putString(KEY_PASSWORD, password)
            putBoolean(KEY_REMEMBER_ME, true)
        }
    }

    fun clear() {
        preferences.edit { clear() }
    }

    companion object {
        private const val TAG = "RememberedLoginStore"
        private const val LEGACY_PREFERENCES = "user_preferences"
        private const val ENCRYPTED_PREFERENCES = "user_preferences_secure"
        private const val KEY_ACCOUNT = "account"
        private const val KEY_PASSWORD = "password"
        private const val KEY_REMEMBER_ME = "rememberMe"

        @WorkerThread
        fun open(context: Context): RememberedLoginStore {
            val appContext = context.applicationContext
            val legacyPreferences =
                appContext.getSharedPreferences(LEGACY_PREFERENCES, Context.MODE_PRIVATE)

            val preferences = runCatching {
                val masterKey = MasterKey.Builder(appContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    appContext,
                    ENCRYPTED_PREFERENCES,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                ).also { encryptedPreferences ->
                    migrateLegacyLogin(legacyPreferences, encryptedPreferences)
                }
            }.getOrElse { throwable ->
                Log.w(TAG, "加密偏好初始化失败，回退到普通 SharedPreferences", throwable)
                legacyPreferences
            }
            return RememberedLoginStore(preferences)
        }

        private fun migrateLegacyLogin(
            legacyPreferences: SharedPreferences,
            encryptedPreferences: SharedPreferences
        ) {
            if (!legacyPreferences.getBoolean(KEY_REMEMBER_ME, false)) return
            if (encryptedPreferences.getBoolean(KEY_REMEMBER_ME, false)) return

            encryptedPreferences.edit {
                putString(KEY_ACCOUNT, legacyPreferences.getString(KEY_ACCOUNT, ""))
                putString(KEY_PASSWORD, legacyPreferences.getString(KEY_PASSWORD, ""))
                putBoolean(KEY_REMEMBER_ME, true)
            }

            legacyPreferences.edit { clear() }
        }
    }
}
