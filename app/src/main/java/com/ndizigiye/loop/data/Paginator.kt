package com.ndizigiye.loop.data

import com.ndizigiye.loop.api.PageDto
import com.ndizigiye.loop.data.remote.userMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PagedState<T>(
    val items: List<T> = emptyList(),
    val nextCursor: String? = null,
    /** First page or next page in flight. */
    val isLoading: Boolean = false,
    /** [Paginator.refresh] in flight while the current items stay visible. */
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val endReached: Boolean = false,
    /** True once a page has been loaded successfully. */
    val loadedOnce: Boolean = false,
)

/**
 * Cursor pagination over [load] (see docs/api.md "Pagination"), usually owned by a
 * ViewModel with `viewModelScope`. Not thread-safe: call it from the main thread.
 */
class Paginator<T>(
    private val scope: CoroutineScope,
    private val load: suspend (cursor: String?) -> Result<PageDto<T>>,
) {
    private val _state = MutableStateFlow(PagedState<T>())
    val state: StateFlow<PagedState<T>> = _state.asStateFlow()

    private var job: Job? = null

    /** Reloads from the first page, keeping the current items visible until it lands. */
    fun refresh() {
        job?.cancel()
        _state.update {
            it.copy(isLoading = it.items.isEmpty(), isRefreshing = it.items.isNotEmpty(), error = null)
        }
        job = scope.launch {
            load(null)
                .onSuccess { page ->
                    _state.value = PagedState(
                        items = page.items,
                        nextCursor = page.nextCursor,
                        endReached = page.nextCursor == null,
                        loadedOnce = true,
                    )
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(isLoading = false, isRefreshing = false, error = error.userMessage())
                    }
                }
        }
    }

    /** Loads the next page; the first call loads page 1. No-op while loading or at the end. */
    fun loadMore() {
        val current = _state.value
        if (current.isLoading || current.isRefreshing || current.endReached) return
        if (job?.isActive == true) return
        val firstPage = !current.loadedOnce
        val cursor = if (firstPage) null else current.nextCursor ?: return
        _state.update { it.copy(isLoading = true, error = null) }
        job = scope.launch {
            load(cursor)
                .onSuccess { page ->
                    _state.update {
                        it.copy(
                            items = if (firstPage) page.items else it.items + page.items,
                            nextCursor = page.nextCursor,
                            isLoading = false,
                            error = null,
                            endReached = page.nextCursor == null,
                            loadedOnce = true,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(isLoading = false, error = error.userMessage()) }
                }
        }
    }

    /** Applies a local edit, e.g. removing a deleted item or prepending a new one. */
    fun update(transform: (List<T>) -> List<T>) {
        _state.update { it.copy(items = transform(it.items)) }
    }
}
