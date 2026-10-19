package com.ndizigiye.loop.data

import com.ndizigiye.loop.api.NotificationDto
import com.ndizigiye.loop.api.PageDto
import com.ndizigiye.loop.data.remote.LoopApi
import com.ndizigiye.loop.data.remote.apiCall
import com.ndizigiye.loop.data.session.SessionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class InboxRepository(
    private val api: LoopApi,
    private val sessionStore: SessionStore,
    private val interactions: InteractionStore,
) {
    private val _unreadCount = MutableStateFlow(0L)

    /** Unread notifications of the signed-in user; 0 when signed out. */
    val unreadCount: StateFlow<Long> = _unreadCount.asStateFlow()

    suspend fun notifications(cursor: String?): Result<PageDto<NotificationDto>> =
        apiCall { api.notifications(cursor) }
            .onSuccess { page -> interactions.rememberUsers(page.items.map { it.actor }) }

    /** Fetches [unreadCount] from the server; failures keep the previous value. */
    suspend fun refreshUnreadCount() {
        if (!sessionStore.isLoggedIn) {
            _unreadCount.value = 0
            return
        }
        apiCall { api.unreadCount() }.onSuccess { _unreadCount.value = it.count }
    }

    /**
     * Marks the notification [upTo] (the newest one loaded) and every older one as read, so
     * notifications that arrived after the list was loaded stay unread, then refreshes
     * [unreadCount] (also when the request failed).
     */
    suspend fun markAllRead(upTo: Long): Result<Unit> =
        apiCall { api.markNotificationsRead(upTo) }.also { refreshUnreadCount() }

    /** Resets [unreadCount], e.g. on logout. */
    fun reset() {
        _unreadCount.value = 0
    }
}
