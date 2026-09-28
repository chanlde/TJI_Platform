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
    private val preferences: SharedPreferences?
) {
    val isAvailable: Boolean
        get() = preferences != null

    fun load(): RememberedLoginCredentials? {
        val securePreferences = preferences ?: return null
        return runCatching {
            if (!securePreferences.getBoolean(KEY_REMEMBER_ME, false)) return null
            RememberedLoginCredentials(
                account = securePreferences.getString(KEY_ACCOUNT, "").orEmpty(),
                password = securePreferences.getString(KEY_PASSWORD, "").orEmpty()
            )
        }.onFailure { throwable ->
            Log.w(TAG, "读取记住登录信息失败，忽略损坏的偏好数据", throwable)
        }.getOrNull()
    }

    fun save(account: String, password: String): Boolean {
        val securePreferences = preferences ?: return false
        return runCatching {
            securePreferences.edit(commit = true) {
                putString(KEY_ACCOUNT, account)
                putString(KEY_PASSWORD, password)
                putBoolean(KEY_REMEMBER_ME, true)
            }
            true
        }.onFailure { throwable ->
            Log.w(TAG, "安全保存记住登录信息失败，本次不保存", throwable)
        }.getOrDefault(false)
    }

    fun clear() {
        preferences?.let { securePreferences ->
            runCatching { securePreferences.edit(commit = true) { clear() } }
                .onFailure { throwable ->
                    Log.w(TAG, "清除记住登录信息失败", throwable)
                }
        }
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

            return openWithEncryptedPreferences(legacyPreferences) {
                val masterKey = MasterKey.Builder(appContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    appContext,
                    ENCRYPTED_PREFERENCES,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            }
        }

        internal fun openWithEncryptedPreferences(
            legacyPreferences: SharedPreferences,
            encryptedPreferencesFactory: () -> SharedPreferences
        ): RememberedLoginStore {
            val preferences = runCatching {
                encryptedPreferencesFactory().also { encryptedPreferences ->
                    migrateLegacyLogin(legacyPreferences, encryptedPreferences)
                }
            }.getOrElse { throwable ->
                Log.w(TAG, "加密偏好初始化失败，禁用记住密码并清理旧明文", throwable)
                runCatching { legacyPreferences.edit(commit = true) { clear() } }
                    .onFailure { clearFailure ->
                        Log.e(TAG, "旧明文登录信息清理失败", clearFailure)
                    }
                null
            }
            return RememberedLoginStore(preferences)
        }

        private fun migrateLegacyLogin(
            legacyPreferences: SharedPreferences,
            encryptedPreferences: SharedPreferences
        ) {
            if (!legacyPreferences.getBoolean(KEY_REMEMBER_ME, false)) return
            if (encryptedPreferences.getBoolean(KEY_REMEMBER_ME, false)) return

            encryptedPreferences.edit(commit = true) {
                putString(KEY_ACCOUNT, legacyPreferences.getString(KEY_ACCOUNT, ""))
                putString(KEY_PASSWORD, legacyPreferences.getString(KEY_PASSWORD, ""))
                putBoolean(KEY_REMEMBER_ME, true)
            }

            legacyPreferences.edit(commit = true) { clear() }
        }
    }
}
