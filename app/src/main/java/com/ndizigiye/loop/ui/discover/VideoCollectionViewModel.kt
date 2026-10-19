package com.ndizigiye.loop.ui.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ndizigiye.loop.api.PageDto
import com.ndizigiye.loop.api.VideoDto
import com.ndizigiye.loop.data.PagedState
import com.ndizigiye.loop.data.Paginator
import com.ndizigiye.loop.data.remote.ApiException
import com.ndizigiye.loop.data.remote.userMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CollectionHeaderState<H>(
    val value: H? = null,
    val isLoading: Boolean = false,
    /** Why the last load failed; cleared when one starts. */
    val error: String? = null,
    /** The server answered 404. */
    val notFound: Boolean = false,
)

/**
 * A header (a hashtag or a sound) and a paginated grid of its videos. Both load on
 * creation. [loadPage] must not capture the ViewModel: it is also handed to the
 * full-screen pager as [pageLoader].
 */
abstract class VideoCollectionViewModel<H : Any>(
    private val loadHeader: suspend () -> Result<H>,
    loadPage: suspend (cursor: String?) -> Result<PageDto<VideoDto>>,
) : ViewModel() {

    private val _header = MutableStateFlow(CollectionHeaderState<H>())
    val header: StateFlow<CollectionHeaderState<H>> = _header.asStateFlow()

    private val paginator = Paginator(viewModelScope, loadPage)
    val videos: StateFlow<PagedState<VideoDto>> = paginator.state

    private val _isPullRefreshing = MutableStateFlow(false)
    val isPullRefreshing: StateFlow<Boolean> = _isPullRefreshing.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)

    /** One-off messages for the snackbar. */
    val messages: Flow<String> = _messages.receiveAsFlow()

    /** Continues the grid's list in the full-screen pager. */
    val pageLoader: suspend (cursor: String) -> Result<PageDto<VideoDto>> = loadPage

    private var headerJob: Job? = null

    init {
        reloadHeader()
        paginator.refresh()
    }

    /** Pull to refresh: reloads the header and the grid from its first page. */
    fun pullRefresh() {
        if (_isPullRefreshing.value) return
        _isPullRefreshing.value = true
        reloadHeader()
        paginator.refresh()
        viewModelScope.launch {
            try {
                header.first { !it.isLoading }
                val grid = videos.first { !it.isLoading && !it.isRefreshing }
                // An empty grid shows its own error.
                val error = grid.error
                if (error != null && grid.items.isNotEmpty()) _messages.send(error)
            } finally {
                _isPullRefreshing.value = false
            }
        }
    }

    /** Next page; ignored before the first page and after an error (see [retry]). */
    fun loadMore() {
        val state = paginator.state.value
        if (state.loadedOnce && state.error == null) paginator.loadMore()
    }

    /** Retries whatever failed: the header, the first page or the next page. */
    fun retry() {
        val headerState = _header.value
        if (headerState.error != null && !headerState.isLoading) reloadHeader()
        val state = paginator.state.value
        if (state.error == null || state.isLoading || state.isRefreshing) return
        if (state.loadedOnce && !state.endReached) paginator.loadMore() else paginator.refresh()
    }

    private fun reloadHeader() {
        headerJob?.cancel()
        _header.update { it.copy(isLoading = true, error = null) }
        headerJob = viewModelScope.launch {
            loadHeader()
                .onSuccess { value -> _header.value = CollectionHeaderState(value = value) }
                .onFailure { error ->
                    _header.update {
                        it.copy(
                            isLoading = false,
                            error = error.userMessage(),
                            notFound = error is ApiException && error.status == 404,
                        )
                    }
                }
        }
    }
}
