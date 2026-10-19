package com.ndizigiye.loop.data.session

import android.content.Context
import com.ndizigiye.loop.BuildConfig
import com.ndizigiye.loop.api.UserDto
import com.ndizigiye.loop.data.remote.LoopJson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerializationException

data class Session(val token: String, val user: UserDto)

/**
 * The signed-in account and the server URL, persisted in the SharedPreferences
 * file "loop_session". All members are safe to call from any thread.
 */
class SessionStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _session = MutableStateFlow(readSession())
    val session: StateFlow<Session?> = _session.asStateFlow()

    val token: String? get() = _session.value?.token
    val currentUser: UserDto? get() = _session.value?.user
    val isLoggedIn: Boolean get() = _session.value != null

    private val _baseUrl = MutableStateFlow(
        normalizeBaseUrl(prefs.getString(KEY_BASE_URL, null) ?: BuildConfig.DEFAULT_BASE_URL),
    )

    /** The server's root URL, always ending in "/". */
    val baseUrl: StateFlow<String> = _baseUrl.asStateFlow()

    fun save(token: String, user: UserDto) {
        val me = user.copy(isMe = true)
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_USER, LoopJson.encodeToString(UserDto.serializer(), me))
            .apply()
        _session.value = Session(token, me)
    }

    /** Replaces the stored profile of the signed-in user; ignored when signed out. */
    fun updateUser(user: UserDto) {
        val current = _session.value ?: return
        if (current.user.id != user.id) return
        val me = user.copy(isMe = true)
        prefs.edit().putString(KEY_USER, LoopJson.encodeToString(UserDto.serializer(), me)).apply()
        _session.value = current.copy(user = me)
    }

    fun clear() {
        prefs.edit().remove(KEY_TOKEN).remove(KEY_USER).apply()
        _session.value = null
    }

    fun setBaseUrl(url: String) {
        val normalized = normalizeBaseUrl(url)
        prefs.edit().putString(KEY_BASE_URL, normalized).apply()
        _baseUrl.value = normalized
    }

    /**
     * Resolves a server-relative media path ("/media/videos/a.mp4") against [baseUrl].
     * Absolute URLs are returned unchanged; null or blank input gives null.
     */
    fun resolveUrl(path: String?): String? {
        if (path.isNullOrBlank()) return null
        if (path.startsWith("http://", ignoreCase = true) || path.startsWith("https://", ignoreCase = true)) {
            return path
        }
        return _baseUrl.value + path.trimStart('/')
    }

    private fun readSession(): Session? {
        val token = prefs.getString(KEY_TOKEN, null) ?: return null
        val userJson = prefs.getString(KEY_USER, null) ?: return null
        return try {
            Session(token, LoopJson.decodeFromString(UserDto.serializer(), userJson))
        } catch (e: SerializationException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    companion object {
        private const val PREFS_NAME = "loop_session"
        private const val KEY_TOKEN = "token"
        private const val KEY_USER = "user"
        private const val KEY_BASE_URL = "base_url"

        /**
         * Trims [url], adds "http://" when no scheme is given and makes it end with "/".
         * Does not validate; use `toHttpUrlOrNull()` on the result for that.
         */
        fun normalizeBaseUrl(url: String): String {
            val trimmed = url.trim()
            val withScheme = if (trimmed.contains("://")) trimmed else "http://$trimmed"
            return if (withScheme.endsWith("/")) withScheme else "$withScheme/"
        }
    }
}
