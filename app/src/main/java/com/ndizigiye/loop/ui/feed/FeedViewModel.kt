package com.ndizigiye.loop.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ndizigiye.loop.api.VideoDto
import com.ndizigiye.loop.data.FeedType
import com.ndizigiye.loop.data.PagedState
import com.ndizigiye.loop.data.Paginator
import com.ndizigiye.loop.data.VideoRepository
import com.ndizigiye.loop.data.session.SessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** The home feed's two lists, For You and Following, reloaded whenever the account changes. */
internal class FeedViewModel(
    videoRepository: VideoRepository,
    private val sessionStore: SessionStore,
    applicationScope: CoroutineScope,
) : ViewModel() {

    private val viewReporter = ViewReporter(videoRepository, applicationScope)

    private val forYou = Paginator<VideoDto>(viewModelScope) { cursor ->
        videoRepository.feed(FeedType.FOR_YOU, cursor)
    }

    private val following = Paginator<VideoDto>(viewModelScope) { cursor ->
        videoRepository.feed(FeedType.FOLLOWING, cursor)
    }

    init {
        forYou.loadMore()
        if (sessionStore.isLoggedIn) following.loadMore()
        viewModelScope.launch {
            sessionStore.session
                .map { it?.user?.id }
                .distinctUntilChanged()
                .drop(1)
                .collect { userId -> onAccountChanged(signedIn = userId != null) }
        }
    }

    fun state(type: FeedType): StateFlow<PagedState<VideoDto>> = paginator(type).state

    fun loadMore(type: FeedType) {
        if (type == FeedType.FOLLOWING && !sessionStore.isLoggedIn) return
        paginator(type).loadMore()
    }

    fun refresh(type: FeedType) {
        if (type == FeedType.FOLLOWING && !sessionStore.isLoggedIn) return
        paginator(type).refresh()
    }

    /** Reloads Following when it shows nothing, e.g. after the viewer followed someone elsewhere. */
    fun refreshFollowingIfEmpty() {
        val current = following.state.value
        if (sessionStore.isLoggedIn && current.items.isEmpty() && !current.isLoading) following.refresh()
    }

    /** Drops [videoId] from both lists. */
    fun remove(videoId: Long) {
        forYou.update { items -> items.filterNot { it.id == videoId } }
        following.update { items -> items.filterNot { it.id == videoId } }
    }

    fun recordView(videoId: Long, watchedMs: Long, completed: Boolean) {
        viewReporter.report(videoId, watchedMs, completed)
    }

    private fun onAccountChanged(signedIn: Boolean) {
        forYou.refresh()
        if (signedIn) {
            following.refresh()
        } else {
            following.update { emptyList() }
        }
    }

    private fun paginator(type: FeedType): Paginator<VideoDto> = when (type) {
        FeedType.FOR_YOU -> forYou
        FeedType.FOLLOWING -> following
    }
}
