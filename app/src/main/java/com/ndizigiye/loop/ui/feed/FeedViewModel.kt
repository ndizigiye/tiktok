package com.ndizigiye.loop.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ndizigiye.loop.api.PageDto
import com.ndizigiye.loop.api.VideoDto
import com.ndizigiye.loop.data.FeedType
import com.ndizigiye.loop.data.PagedState
import com.ndizigiye.loop.data.Paginator
import com.ndizigiye.loop.data.VideoRepository
import com.ndizigiye.loop.data.session.SessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.random.Random

/** The home feed's two lists, For You and Following, reloaded whenever the account or the server changes. */
internal class FeedViewModel(
    private val videoRepository: VideoRepository,
    private val sessionStore: SessionStore,
    applicationScope: CoroutineScope,
) : ViewModel() {

    private val viewReporter = ViewReporter(videoRepository, applicationScope)

    private val forYou = Paginator<VideoDto>(viewModelScope) { cursor ->
        loadPage(FeedType.FOR_YOU, cursor)
    }

    private val following = Paginator<VideoDto>(viewModelScope) { cursor ->
        loadPage(FeedType.FOLLOWING, cursor)
    }

    private val listIds = FeedType.entries.associateWith { MutableStateFlow(0L) }

    /** Feeds whose last failed request was for the first page, i.e. a refresh. */
    private val firstPageFailed = mutableSetOf<FeedType>()

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
        viewModelScope.launch {
            sessionStore.baseUrl
                .drop(1)
                .collect { onServerChanged() }
        }
    }

    fun state(type: FeedType): StateFlow<PagedState<VideoDto>> = paginator(type).state

    /**
     * Identifies the list loaded by [type]'s latest first page (first load, refresh,
     * account change). Random, so it also differs from one saved before process death.
     */
    fun listId(type: FeedType): StateFlow<Long> = listIds.getValue(type).asStateFlow()

    fun loadMore(type: FeedType) {
        if (type == FeedType.FOLLOWING && !sessionStore.isLoggedIn) return
        paginator(type).loadMore()
    }

    fun refresh(type: FeedType) {
        if (type == FeedType.FOLLOWING && !sessionStore.isLoggedIn) return
        paginator(type).refresh()
    }

    /** Repeats the request that failed last: the refresh (the items stay meanwhile), or the next page. */
    fun retry(type: FeedType) {
        if (type in firstPageFailed) refresh(type) else loadMore(type)
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

    /** Starts both lists over: another server's ids and media paths mean nothing on the new one. */
    private fun onServerChanged() {
        forYou.update { emptyList() }
        following.update { emptyList() }
        onAccountChanged(signedIn = sessionStore.isLoggedIn)
    }

    private suspend fun loadPage(type: FeedType, cursor: String?): Result<PageDto<VideoDto>> {
        val firstPage = cursor == null
        return videoRepository.feed(type, cursor)
            .onSuccess { if (firstPage) listIds.getValue(type).value = Random.nextLong() }
            .onFailure { if (firstPage) firstPageFailed += type else firstPageFailed -= type }
    }

    private fun paginator(type: FeedType): Paginator<VideoDto> = when (type) {
        FeedType.FOR_YOU -> forYou
        FeedType.FOLLOWING -> following
    }
}
