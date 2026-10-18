package com.ndizigiye.loop.ui.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ndizigiye.loop.api.TrendingDto
import com.ndizigiye.loop.data.DiscoverRepository
import com.ndizigiye.loop.data.remote.userMessage
import com.ndizigiye.loop.data.session.SessionStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DiscoverUiState(
    val trending: TrendingDto? = null,
    /** Load in flight while there is nothing to show yet. */
    val isLoading: Boolean = false,
    /** Pull to refresh in flight while the current content stays visible. */
    val isRefreshing: Boolean = false,
    /** Why loading failed while there is nothing to show; later failures go to [DiscoverViewModel.messages]. */
    val error: String? = null,
)

/** Trending hashtags, sounds and creators for the Discover tab. */
class DiscoverViewModel(
    private val discoverRepository: DiscoverRepository,
    sessionStore: SessionStore,
) : ViewModel() {

    private val _state = MutableStateFlow(DiscoverUiState())
    val state: StateFlow<DiscoverUiState> = _state.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)

    /** One-off messages for the snackbar. */
    val messages: Flow<String> = _messages.receiveAsFlow()

    private var job: Job? = null

    init {
        load(userInitiated = false)
        // Suggested creators and follow/like flags depend on who is signed in.
        viewModelScope.launch {
            sessionStore.session
                .map { it?.user?.id }
                .distinctUntilChanged()
                .drop(1)
                .collect { load(userInitiated = false) }
        }
    }

    /** Pull to refresh. */
    fun refresh() {
        load(userInitiated = true)
    }

    fun retry() {
        load(userInitiated = true)
    }

    private fun load(userInitiated: Boolean) {
        job?.cancel()
        _state.update {
            it.copy(
                isLoading = it.trending == null,
                isRefreshing = userInitiated && it.trending != null,
                error = null,
            )
        }
        job = viewModelScope.launch {
            discoverRepository.discover()
                .onSuccess { trending -> _state.value = DiscoverUiState(trending = trending) }
                .onFailure { error ->
                    val message = error.userMessage()
                    val hasContent = _state.value.trending != null
                    _state.update {
                        it.copy(isLoading = false, isRefreshing = false, error = if (hasContent) null else message)
                    }
                    if (hasContent && userInitiated) _messages.send(message)
                }
        }
    }
}
