package com.ndizigiye.loop.ui.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ndizigiye.loop.api.NotificationDto
import com.ndizigiye.loop.data.InboxRepository
import com.ndizigiye.loop.data.PagedState
import com.ndizigiye.loop.data.Paginator
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The signed-in user's notifications. Every reload marks them all as read on the
 * server once the first page is in, while the loaded items keep the `read` flags
 * they arrived with, so the rows that were new stay highlighted.
 */
class InboxViewModel(private val inboxRepository: InboxRepository) : ViewModel() {

    private val paginator = Paginator(viewModelScope) { cursor -> inboxRepository.notifications(cursor) }

    val state: StateFlow<PagedState<NotificationDto>> = paginator.state

    private val _isPullRefreshing = MutableStateFlow(false)

    /** True while a reload started by pull-to-refresh is in flight (automatic reloads stay silent). */
    val isPullRefreshing: StateFlow<Boolean> = _isPullRefreshing.asStateFlow()

    private var reloadJob: Job? = null

    /** Reloads from the first page without showing the pull-to-refresh indicator. */
    fun reload() = startReload(byUser = false)

    fun pullToRefresh() = startReload(byUser = true)

    /** Next page; ignored before the first page has loaded and after an error (see [retry]). */
    fun loadMore() {
        val current = state.value
        if (current.loadedOnce && current.error == null) paginator.loadMore()
    }

    fun retry() {
        val current = state.value
        if (current.items.isNotEmpty() && !current.endReached) paginator.loadMore() else startReload(byUser = false)
    }

    private fun startReload(byUser: Boolean) {
        reloadJob?.cancel()
        paginator.refresh()
        _isPullRefreshing.value = byUser
        reloadJob = viewModelScope.launch {
            val settled = paginator.state.first { !it.isLoading && !it.isRefreshing }
            _isPullRefreshing.value = false
            if (settled.error == null) {
                inboxRepository.markAllRead()
                inboxRepository.refreshUnreadCount()
            }
        }
    }
}
