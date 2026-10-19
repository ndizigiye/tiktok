package com.ndizigiye.loop.testutil

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences

/**
 * Just enough of a [Context] for [com.ndizigiye.loop.data.session.SessionStore] and
 * [com.ndizigiye.loop.di.AppContainer] on the plain JVM: SharedPreferences are kept
 * in memory, one instance per name, so a second store on the same context sees what
 * the first one persisted.
 */
class FakeContext : ContextWrapper(null) {

    private val preferences = mutableMapOf<String, InMemoryPreferences>()

    override fun getApplicationContext(): Context = this

    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
        synchronized(preferences) { preferences.getOrPut(name) { InMemoryPreferences() } }
}

/** A thread-safe in-memory [SharedPreferences]; `apply()` and `commit()` write at once. */
class InMemoryPreferences : SharedPreferences {

    private val values = mutableMapOf<String, Any?>()
    private val listeners = mutableSetOf<SharedPreferences.OnSharedPreferenceChangeListener>()

    override fun getAll(): Map<String, *> = synchronized(values) { values.toMap() }

    override fun getString(key: String, defValue: String?): String? = read(key) ?: defValue

    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? =
        read<Set<String>>(key)?.toSet() ?: defValues

    override fun getInt(key: String, defValue: Int): Int = read(key) ?: defValue

    override fun getLong(key: String, defValue: Long): Long = read(key) ?: defValue

    override fun getFloat(key: String, defValue: Float): Float = read(key) ?: defValue

    override fun getBoolean(key: String, defValue: Boolean): Boolean = read(key) ?: defValue

    override fun contains(key: String): Boolean = synchronized(values) { key in values }

    override fun edit(): SharedPreferences.Editor = Editor()

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener,
    ) {
        synchronized(listeners) { listeners += listener }
    }

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener,
    ) {
        synchronized(listeners) { listeners -= listener }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> read(key: String): T? = synchronized(values) { values[key] as T? }

    private inner class Editor : SharedPreferences.Editor {
        private val changes = mutableMapOf<String, Any?>()
        private var clearFirst = false

        override fun putString(key: String, value: String?) = put(key, value)

        override fun putStringSet(key: String, values: Set<String>?) = put(key, values?.toSet())

        override fun putInt(key: String, value: Int) = put(key, value)

        override fun putLong(key: String, value: Long) = put(key, value)

        override fun putFloat(key: String, value: Float) = put(key, value)

        override fun putBoolean(key: String, value: Boolean) = put(key, value)

        override fun remove(key: String) = put(key, null)

        override fun clear(): SharedPreferences.Editor = apply { clearFirst = true }

        override fun commit(): Boolean {
            val changed = synchronized(values) {
                if (clearFirst) values.clear()
                for ((key, value) in changes) {
                    if (value == null) values.remove(key) else values[key] = value
                }
                changes.keys.toList()
            }
            val toNotify = synchronized(listeners) { listeners.toList() }
            for (key in changed) toNotify.forEach { it.onSharedPreferenceChanged(this@InMemoryPreferences, key) }
            return true
        }

        override fun apply() {
            commit()
        }

        private fun put(key: String, value: Any?): SharedPreferences.Editor = apply { changes[key] = value }
    }
}
