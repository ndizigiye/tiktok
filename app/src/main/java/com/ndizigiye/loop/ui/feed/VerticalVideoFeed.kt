package com.ndizigiye.loop.ui.feed

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import androidx.media3.exoplayer.ExoPlayer
import com.ndizigiye.loop.api.VideoDto
import com.ndizigiye.loop.data.PagedState
import com.ndizigiye.loop.player.rememberFeedPlayerPool
import com.ndizigiye.loop.ui.comments.CommentsSheet
import com.ndizigiye.loop.ui.comments.ShareSheet
import com.ndizigiye.loop.ui.common.EmptyBox
import com.ndizigiye.loop.ui.common.ErrorBox
import com.ndizigiye.loop.ui.common.LoadingBox
import com.ndizigiye.loop.ui.common.LocalSnackbarHostState
import com.ndizigiye.loop.ui.navigation.LocalAppContainer
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop

/** Loads the next page once the viewer settles this close to the end. */
private const val LOAD_MORE_THRESHOLD = 3
private const val PREFETCH_AHEAD = 3
private const val REPORT_MESSAGE = "Thanks for letting us know"

private sealed interface FeedSheet {
    val video: VideoDto

    data class Comments(override val video: VideoDto) : FeedSheet
    data class Share(override val video: VideoDto) : FeedSheet
    data class Options(override val video: VideoDto) : FeedSheet
}

/**
 * A full-screen, vertically swiped list of videos over [state], shared by the home
 * feed and the video pager. It shows the loading, error and empty states, pages
 * further through [onLoadMore], plays the settled page while [isActive], the screen
 * is resumed and no sheet is open, prefetches the next videos, and reports each
 * page's view through [onViewed] when it stops being current or the feed is left.
 * [onRemove] drops a video from the list (not interested, deleted).
 */
@Composable
internal fun VerticalVideoFeed(
    state: PagedState<VideoDto>,
    isActive: Boolean,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onViewed: (videoId: Long, watchedMs: Long, completed: Boolean) -> Unit,
    onRemove: (videoId: Long) -> Unit,
    modifier: Modifier = Modifier,
    initialPage: Int = 0,
    emptyContent: @Composable () -> Unit = {
        EmptyBox(title = "No videos here yet", icon = Icons.Outlined.VideoLibrary)
    },
) {
    val error = state.error
    when {
        state.items.isNotEmpty() -> VideoFeedPager(
            state = state,
            isActive = isActive,
            initialPage = initialPage,
            onLoadMore = onLoadMore,
            onViewed = onViewed,
            onRemove = onRemove,
            modifier = modifier,
        )
        error != null -> ErrorBox(message = error, onRetry = onRetry, modifier = modifier)
        state.loadedOnce && !state.isLoading -> Box(modifier = modifier) { emptyContent() }
        else -> LoadingBox(modifier = modifier)
    }
}

@Composable
private fun VideoFeedPager(
    state: PagedState<VideoDto>,
    isActive: Boolean,
    initialPage: Int,
    onLoadMore: () -> Unit,
    onViewed: (videoId: Long, watchedMs: Long, completed: Boolean) -> Unit,
    onRemove: (videoId: Long) -> Unit,
    modifier: Modifier,
) {
    val videos = state.items
    val container = LocalAppContainer.current
    val sessionStore = container.sessionStore
    val snackbarHostState = LocalSnackbarHostState.current
    val pagerState = rememberPagerState(initialPage = initialPage.coerceIn(0, videos.lastIndex)) { videos.size }
    val current = pagerState.settledPage.coerceIn(0, videos.lastIndex)
    val pool = rememberFeedPlayerPool()
    val actions = rememberVideoActions()

    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    var sheet by remember { mutableStateOf<FeedSheet?>(null) }
    var userPaused by remember(current) { mutableStateOf(false) }
    var playbackSpeed by remember(current) { mutableFloatStateOf(1f) }
    val visiting = isActive && lifecycleState.isAtLeast(Lifecycle.State.STARTED)
    val shouldPlay = visiting &&
        lifecycleState.isAtLeast(Lifecycle.State.RESUMED) &&
        sheet == null &&
        !userPaused

    val currentOnLoadMore by rememberUpdatedState(onLoadMore)
    val currentOnViewed by rememberUpdatedState(onViewed)
    val currentOnRemove by rememberUpdatedState(onRemove)
    val latestVideos by rememberUpdatedState(videos)

    // Players for the settled page and its neighbours; pages further away show their thumbnail.
    val baseUrl by sessionStore.baseUrl.collectAsStateWithLifecycle()
    val nearbyPaths = (current - 1..current + 1).map { videos.getOrNull(it)?.videoUrl }
    var players by remember(pool) { mutableStateOf<Map<Int, ExoPlayer>>(emptyMap()) }
    LaunchedEffect(pool, current, nearbyPaths, baseUrl) {
        players = buildMap {
            nearbyPaths.forEachIndexed { offset, path ->
                val url = sessionStore.resolveUrl(path) ?: return@forEachIndexed
                val index = current - 1 + offset
                put(index, pool.acquire(index, url))
            }
        }
    }
    LaunchedEffect(pool, current, shouldPlay) {
        pool.setCurrent(current, shouldPlay)
    }

    val currentPlayer = players[current]
    DisposableEffect(currentPlayer, playbackSpeed) {
        currentPlayer?.setPlaybackSpeed(playbackSpeed)
        onDispose { currentPlayer?.setPlaybackSpeed(1f) }
    }

    // One view per visit of the current page, measured while it actually plays.
    val visitVideo = videos[current]
    val visitPlayer = if (visiting) currentPlayer else null
    DisposableEffect(visitPlayer, visitVideo.id) {
        val player = visitPlayer ?: return@DisposableEffect onDispose {}
        val tracker = WatchTracker(player, visitVideo.durationMs)
        tracker.start()
        onDispose {
            val result = tracker.stop()
            currentOnViewed(visitVideo.id, result.watchedMs, result.completed)
        }
    }

    val aheadPaths = (current + 1..current + PREFETCH_AHEAD).map { videos.getOrNull(it)?.videoUrl }
    LaunchedEffect(aheadPaths, baseUrl) {
        aheadPaths.forEach { path ->
            sessionStore.resolveUrl(path)?.let { container.videoCache.prefetch(it) }
        }
    }

    LaunchedEffect(current, videos.size) {
        if (current >= videos.size - LOAD_MORE_THRESHOLD) currentOnLoadMore()
    }

    val loadError = state.error
    LaunchedEffect(loadError, isActive) {
        if (loadError != null && isActive) {
            val result = snackbarHostState.showSnackbar(
                message = loadError,
                actionLabel = "Retry",
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) currentOnLoadMore()
        }
    }

    // A refreshed list (new account, pull to refresh) starts again from the top.
    LaunchedEffect(pagerState) {
        snapshotFlow { latestVideos.firstOrNull()?.id }
            .distinctUntilChanged()
            .drop(1)
            .collect { pagerState.scrollToPage(0) }
    }

    val keys = remember(videos) { pageKeys(videos) }
    VerticalPager(
        state = pagerState,
        modifier = modifier,
        beyondViewportPageCount = 1,
        key = { index -> keys.getOrElse(index) { "page:$index" } },
    ) { index ->
        val video = videos.getOrNull(index) ?: return@VerticalPager
        VideoPage(
            video = video,
            player = players[index],
            isCurrent = index == current,
            isPlaying = index == current && shouldPlay,
            isPaused = index == current && userPaused,
            actions = actions,
            onTogglePause = { if (index == current) userPaused = !userPaused },
            onOpenOptions = { sheet = FeedSheet.Options(video) },
            onOpenComments = { sheet = FeedSheet.Comments(video) },
            onOpenShare = { sheet = FeedSheet.Share(video) },
        )
    }

    when (val open = sheet) {
        is FeedSheet.Comments -> CommentsSheet(video = open.video, onDismiss = { sheet = null })
        is FeedSheet.Share -> ShareSheet(video = open.video, onDismiss = { sheet = null })
        is FeedSheet.Options -> VideoOptionsSheet(
            video = open.video,
            speed = playbackSpeed,
            onSpeedChange = { playbackSpeed = it },
            onNotInterested = { actions.notInterested(open.video) { id -> currentOnRemove(id) } },
            onDelete = { actions.delete(open.video) { id -> currentOnRemove(id) } },
            onReport = { actions.showMessage(REPORT_MESSAGE) },
            onDismiss = { sheet = null },
        )
        null -> Unit
    }
}

/**
 * Pager keys from video ids. The For You feed starts a new pass when it runs out,
 * so an id can repeat; later occurrences get a suffix to keep keys unique.
 */
private fun pageKeys(videos: List<VideoDto>): List<String> {
    val seen = HashMap<Long, Int>(videos.size)
    return videos.map { video ->
        val occurrence = seen[video.id] ?: 0
        seen[video.id] = occurrence + 1
        if (occurrence == 0) "video:${video.id}" else "video:${video.id}:$occurrence"
    }
}
