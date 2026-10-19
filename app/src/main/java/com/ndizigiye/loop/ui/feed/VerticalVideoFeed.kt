package com.ndizigiye.loop.ui.feed

import android.os.SystemClock
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.ndizigiye.loop.api.VideoDto
import com.ndizigiye.loop.data.PagedState
import com.ndizigiye.loop.player.PreloadPolicy
import com.ndizigiye.loop.player.PrefetchRequest
import com.ndizigiye.loop.player.prefetchWhileBuffered
import com.ndizigiye.loop.player.rememberFeedPlayerPool
import com.ndizigiye.loop.ui.comments.CommentsSheet
import com.ndizigiye.loop.ui.comments.ShareSheet
import com.ndizigiye.loop.ui.common.EmptyBox
import com.ndizigiye.loop.ui.common.ErrorBox
import com.ndizigiye.loop.ui.common.LoadingBox
import com.ndizigiye.loop.ui.common.LocalSnackbarHostState
import com.ndizigiye.loop.ui.navigation.LocalAppContainer
import kotlinx.coroutines.delay

/** Loads the next page once the viewer settles this close to the end. */
private const val LOAD_MORE_THRESHOLD = 3
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
 * further through [onLoadMore] ([onRetry] repeats the request that failed), plays
 * the settled page while [isActive], the screen is resumed and no sheet is open,
 * prefetches the next videos and the playing video's first comments (see
 * [PreloadPolicy]), and reports each page's view through [onViewed] when it stops
 * being current or the feed is left. [onRemove] drops a video from the list (not
 * interested, deleted). [onSettled] gets the id of every video the pager settles on.
 *
 * [listId] identifies the list in [state]: when it changes (a refresh, another
 * account) the pager starts again from the top, also when the list was replaced
 * while the pager was not shown. With [allowPictureInPicture], leaving the app while
 * a video plays continues it in picture-in-picture.
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
    onSettled: (videoId: Long) -> Unit = {},
    listId: Long = 0L,
    initialPage: Int = 0,
    allowPictureInPicture: Boolean = false,
    emptyContent: @Composable () -> Unit = {
        EmptyBox(title = "No videos here yet", icon = Icons.Outlined.VideoLibrary)
    },
) {
    val error = state.error
    when {
        state.items.isNotEmpty() -> VideoFeedPager(
            state = state,
            isActive = isActive,
            listId = listId,
            initialPage = initialPage,
            allowPictureInPicture = allowPictureInPicture,
            onLoadMore = onLoadMore,
            onRetry = onRetry,
            onViewed = onViewed,
            onRemove = onRemove,
            onSettled = onSettled,
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
    listId: Long,
    initialPage: Int,
    allowPictureInPicture: Boolean,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onViewed: (videoId: Long, watchedMs: Long, completed: Boolean) -> Unit,
    onRemove: (videoId: Long) -> Unit,
    onSettled: (videoId: Long) -> Unit,
    modifier: Modifier,
) {
    val videos = state.items
    val container = LocalAppContainer.current
    val sessionStore = container.sessionStore
    val snackbarHostState = LocalSnackbarHostState.current
    val pagerState = rememberPagerState(initialPage = initialPage.coerceIn(0, videos.lastIndex)) { videos.size }
    val current = pagerState.settledPage.coerceIn(0, videos.lastIndex)
    val keys = remember(videos) { pageKeys(videos) }
    val pool = rememberFeedPlayerPool()
    val actions = rememberVideoActions()

    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    var sheet by remember { mutableStateOf<FeedSheet?>(null) }
    // Pausing by tap and the speed belong to the video shown, not to its position in the list.
    var userPaused by remember(keys[current]) { mutableStateOf(false) }
    var playbackSpeed by remember(keys[current]) { mutableFloatStateOf(1f) }
    val visiting = isActive && lifecycleState.isAtLeast(Lifecycle.State.STARTED)
    val shouldPlay = visiting &&
        lifecycleState.isAtLeast(Lifecycle.State.RESUMED) &&
        sheet == null &&
        !userPaused

    val currentOnLoadMore by rememberUpdatedState(onLoadMore)
    val currentOnRetry by rememberUpdatedState(onRetry)
    val currentOnViewed by rememberUpdatedState(onViewed)
    val currentOnRemove by rememberUpdatedState(onRemove)
    val currentOnSettled by rememberUpdatedState(onSettled)

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

    // The video on screen, e.g. for the pager to reopen it after process death.
    val settledVideoId = videos[current].id
    LaunchedEffect(settledVideoId) { currentOnSettled(settledVideoId) }

    // One view per visit of the current page, measured while it actually plays.
    val visitVideo = videos[current]
    val visitPlayer = if (visiting) currentPlayer else null
    DisposableEffect(visitPlayer, visitVideo.id) {
        val player = visitPlayer ?: return@DisposableEffect onDispose {}
        val tracker = WatchTracker(player)
        tracker.start()
        onDispose {
            val result = tracker.stop()
            currentOnViewed(visitVideo.id, result.watchedMs, result.completed)
        }
    }

    // Upcoming videos are prefetched only while the one on screen is well buffered.
    val aheadPaths = PreloadPolicy.TASKS.map { videos.getOrNull(current + it.offset)?.videoUrl }
    LaunchedEffect(visitPlayer, aheadPaths, baseUrl) {
        val player = visitPlayer ?: return@LaunchedEffect
        val requests = PreloadPolicy.TASKS.zip(aheadPaths).mapNotNull { (task, path) ->
            sessionStore.resolveUrl(path)?.let { PrefetchRequest(it, task.maxBytes) }
        }
        container.videoCache.prefetchWhileBuffered(player, requests)
    }

    // Once the visit has played a while, its first comments are fetched so the sheet opens filled.
    LaunchedEffect(visitPlayer, visitVideo.id) {
        val player = visitPlayer ?: return@LaunchedEffect
        val durationMs = visitVideo.durationMs.takeIf { it > 0L } ?: Long.MAX_VALUE
        player.awaitPlayingFor(minOf(PreloadPolicy.COMMENTS_AFTER_MS, durationMs))
        container.commentRepository.prefetchFirstPage(visitVideo.id)
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
            if (result == SnackbarResult.ActionPerformed) currentOnRetry()
        }
    }

    // A refreshed list (new account, pull to refresh) starts again from the top. The list
    // shown last is saved with the page, so one replaced while the pager was away counts too.
    var shownListId by rememberSaveable { mutableLongStateOf(listId) }
    LaunchedEffect(listId) {
        if (listId != shownListId) {
            shownListId = listId
            pagerState.scrollToPage(0)
        }
    }

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
            allowPictureInPicture = allowPictureInPicture,
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
 * Suspends until this player has been playing for [ms] in total, sampled every
 * [PreloadPolicy.POLL_INTERVAL_MS]. Main thread only.
 */
private suspend fun Player.awaitPlayingFor(ms: Long) {
    var playedMs = 0L
    var lastSampleMs = SystemClock.elapsedRealtime()
    while (playedMs < ms) {
        delay(PreloadPolicy.POLL_INTERVAL_MS)
        val now = SystemClock.elapsedRealtime()
        if (isPlaying) playedMs += now - lastSampleMs
        lastSampleMs = now
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
