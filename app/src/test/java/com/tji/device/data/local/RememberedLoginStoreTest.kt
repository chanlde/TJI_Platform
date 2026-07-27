package com.tji.device.data.local

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RememberedLoginStoreTest {
    @Test
    fun missingRememberFlagDoesNotRestoreCredentials() {
        val store = RememberedLoginStore(InMemorySharedPreferences())

        assertNull(store.load())
    }

    @Test
    fun saveMakesCredentialsAvailableToNextLoad() {
        val store = RememberedLoginStore(InMemorySharedPreferences())

        store.save(account = "operator", password = "secret")

        assertEquals(
            RememberedLoginCredentials(account = "operator", password = "secret"),
            store.load()
        )
    }

    @Test
    fun clearRemovesPreviouslyRememberedCredentials() {
        val store = RememberedLoginStore(InMemorySharedPreferences())
        store.save(account = "operator", password = "secret")

        store.clear()

        assertNull(store.load())
    }

    private class InMemorySharedPreferences : SharedPreferences {
        private val values = mutableMapOf<String, Any?>()

        override fun getAll(): Map<String, *> = values.toMap()

        override fun getString(key: String, defValue: String?): String? =
            values[key] as? String ?: defValue

        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? =
            values[key] as? Set<String> ?: defValues

        override fun getInt(key: String, defValue: Int): Int =
            values[key] as? Int ?: defValue

        override fun getLong(key: String, defValue: Long): Long =
            values[key] as? Long ?: defValue

        override fun getFloat(key: String, defValue: Float): Float =
            values[key] as? Float ?: defValue

        override fun getBoolean(key: String, defValue: Boolean): Boolean =
            values[key] as? Boolean ?: defValue

        override fun contains(key: String): Boolean = values.containsKey(key)

        override fun edit(): SharedPreferences.Editor = Editor(values)

        override fun registerOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener
        ) = Unit

        override fun unregisterOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener
        ) = Unit

        private class Editor(
            private val values: MutableMap<String, Any?>
        ) : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            private val removals = mutableSetOf<String>()
            private var clearRequested = false

            override fun putString(key: String, value: String?): SharedPreferences.Editor =
                put(key, value)

            override fun putStringSet(
                key: String,
                values: Set<String>?
            ): SharedPreferences.Editor = put(key, values)

            override fun putInt(key: String, value: Int): SharedPreferences.Editor =
                put(key, value)

            override fun putLong(key: String, value: Long): SharedPreferences.Editor =
                put(key, value)

            override fun putFloat(key: String, value: Float): SharedPreferences.Editor =
                put(key, value)

            override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor =
                put(key, value)

            override fun remove(key: String): SharedPreferences.Editor = apply {
                removals += key
                pending -= key
            }

            override fun clear(): SharedPreferences.Editor = apply {
                clearRequested = true
                pending.clear()
                removals.clear()
            }

            override fun commit(): Boolean {
                applyChanges()
                return true
            }

            override fun apply() {
                applyChanges()
            }

            private fun put(key: String, value: Any?): SharedPreferences.Editor = apply {
                pending[key] = value
                removals -= key
            }

            private fun applyChanges() {
                if (clearRequested) values.clear()
                removals.forEach(values::remove)
                values.putAll(pending)
            }
        }
    }
}
