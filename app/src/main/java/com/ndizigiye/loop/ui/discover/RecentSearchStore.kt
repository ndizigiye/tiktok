package com.ndizigiye.loop.ui.discover

import android.content.Context
import android.content.SharedPreferences
import com.ndizigiye.loop.data.remote.LoopJson
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

internal const val MAX_RECENT_SEARCHES = 10

/** Recent search queries, most recent first, in the SharedPreferences file "loop_search". */
class RecentSearchStore(context: Context) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences by lazy {
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /** Reads the stored queries; the first call reads the file, so call it off the main thread. */
    fun load(): List<String> {
        val json = prefs.getString(KEY_QUERIES, null) ?: return emptyList()
        return try {
            LoopJson.decodeFromString(QueriesSerializer, json)
                .filter { it.isNotBlank() }
                .take(MAX_RECENT_SEARCHES)
        } catch (e: IllegalArgumentException) {
            // SerializationException is an IllegalArgumentException: a corrupt entry is dropped.
            emptyList()
        }
    }

    /** Replaces the stored queries; writes to disk asynchronously. */
    fun save(queries: List<String>) {
        prefs.edit().putString(KEY_QUERIES, LoopJson.encodeToString(QueriesSerializer, queries)).apply()
    }

    /** Forgets every query, e.g. on log-out; writes to disk asynchronously. */
    fun clear() {
        prefs.edit().remove(KEY_QUERIES).apply()
    }

    private companion object {
        const val PREFS_NAME = "loop_search"
        const val KEY_QUERIES = "recent_queries"
        val QueriesSerializer = ListSerializer(String.serializer())
    }
}
