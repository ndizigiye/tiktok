package com.ndizigiye.loop.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ndizigiye.loop.data.AuthRepository
import com.ndizigiye.loop.data.session.Session
import com.ndizigiye.loop.data.session.SessionStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import okhttp3.OkHttpClient

data class StorageState(
    /** False until the cache size is known, and when the cache cannot be inspected. */
    val available: Boolean = false,
    val usedBytes: Long? = null,
    val clearing: Boolean = false,
)

class SettingsViewModel(
    private val sessionStore: SessionStore,
    private val authRepository: AuthRepository,
    private val okHttpClient: OkHttpClient,
    private val videoCacheStorage: VideoCacheStorage,
) : ViewModel() {

    val session: StateFlow<Session?> = sessionStore.session
    val baseUrl: StateFlow<String> = sessionStore.baseUrl

    private val _connection = MutableStateFlow<ConnectionCheck>(ConnectionCheck.Idle)
    val connection: StateFlow<ConnectionCheck> = _connection.asStateFlow()

    private val _storage = MutableStateFlow(StorageState())
    val storage: StateFlow<StorageState> = _storage.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)

    /** One-off messages for the snackbar. */
    val messages: Flow<String> = _messages.receiveAsFlow()

    private var connectionJob: Job? = null

    /** Stores [url] as the server URL and returns it as saved (normalised). */
    fun saveServer(url: HttpUrl): String {
        sessionStore.setBaseUrl(url.toString())
        _messages.trySend("Server saved")
        return sessionStore.baseUrl.value
    }

    fun testConnection(url: HttpUrl) {
        connectionJob?.cancel()
        _connection.value = ConnectionCheck.Running
        connectionJob = viewModelScope.launch {
            _connection.value = checkServerHealth(okHttpClient, url)
        }
    }

    /** Forgets the last test result, e.g. because the URL was edited. */
    fun resetConnectionCheck() {
        connectionJob?.cancel()
        connectionJob = null
        _connection.value = ConnectionCheck.Idle
    }

    fun logout() {
        authRepository.logout()
    }

    fun refreshStorage() {
        viewModelScope.launch {
            val used = videoCacheStorage.usedBytes()
            _storage.update { it.copy(available = used != null, usedBytes = used) }
        }
    }

    fun clearVideoCache() {
        if (_storage.value.clearing) return
        _storage.update { it.copy(clearing = true) }
        viewModelScope.launch {
            val cleared = videoCacheStorage.clear()
            val used = videoCacheStorage.usedBytes()
            _storage.value = StorageState(available = used != null, usedBytes = used, clearing = false)
            _messages.trySend(if (cleared) "Video cache cleared" else "Couldn't clear the video cache")
        }
    }
}
