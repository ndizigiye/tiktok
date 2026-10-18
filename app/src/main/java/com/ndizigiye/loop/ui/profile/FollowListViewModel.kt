package com.ndizigiye.loop.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ndizigiye.loop.api.UserDto
import com.ndizigiye.loop.data.PagedState
import com.ndizigiye.loop.data.Paginator
import com.ndizigiye.loop.data.UserRepository
import kotlinx.coroutines.flow.StateFlow

enum class FollowListTab { FOLLOWING, FOLLOWERS }

/** The accounts [username] (lowercase) follows and the accounts following them. */
class FollowListViewModel(
    val username: String,
    userRepository: UserRepository,
) : ViewModel() {

    private val following = Paginator(viewModelScope) { cursor -> userRepository.following(username, cursor) }
    private val followers = Paginator(viewModelScope) { cursor -> userRepository.followers(username, cursor) }

    fun state(tab: FollowListTab): StateFlow<PagedState<UserDto>> = paginator(tab).state

    /** Loads the first page of [tab] unless it is loaded, loading or failed (see [retry]). */
    fun ensureLoaded(tab: FollowListTab) {
        val paginator = paginator(tab)
        val state = paginator.state.value
        if (!state.loadedOnce && !state.isLoading && state.error == null) paginator.refresh()
    }

    fun refresh(tab: FollowListTab) = paginator(tab).refresh()

    /** Next page of [tab]; ignored before its first page and after an error. */
    fun loadMore(tab: FollowListTab) {
        val paginator = paginator(tab)
        val state = paginator.state.value
        if (state.loadedOnce && state.error == null) paginator.loadMore()
    }

    fun retry(tab: FollowListTab) {
        val paginator = paginator(tab)
        val state = paginator.state.value
        if (state.loadedOnce && !state.endReached) paginator.loadMore() else paginator.refresh()
    }

    private fun paginator(tab: FollowListTab): Paginator<UserDto> = when (tab) {
        FollowListTab.FOLLOWING -> following
        FollowListTab.FOLLOWERS -> followers
    }
}
