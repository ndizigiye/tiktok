package com.ndizigiye.loop.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ndizigiye.loop.api.VideoDto

private const val GRID_COLUMNS = 3

/**
 * Adds one [VideoThumbnailCard] per video. Keys are unique even when the same
 * video appears twice in [videos]. [onClick] receives the index in [videos].
 */
fun LazyGridScope.videoGridItems(videos: List<VideoDto>, onClick: (index: Int) -> Unit) {
    val keys = uniqueVideoKeys(videos)
    items(
        count = videos.size,
        key = { index -> keys[index] },
        contentType = { "video" },
    ) { index ->
        VideoThumbnailCard(video = videos[index], onClick = { onClick(index) })
    }
}

/** An item spanning the whole width of a grid; [key] doubles as its content type. */
fun LazyGridScope.fullWidthItem(key: String, content: @Composable LazyGridItemScope.() -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }, contentType = key, content = content)
}

/**
 * Three-column grid of video thumbnails with 1 dp gaps. [header] items come first and
 * [footer] items last, e.g. a [LoadMoreErrorFooter] after a failed page load or an
 * empty state; [fullWidthItem] makes an item take the full width.
 * [onLoadMore] is called whenever the user scrolls near the end (and again each
 * time the list grows while still near the end), but not while [loadMoreFailed]:
 * then the user retries explicitly, see [LoadMoreEffect].
 */
@Composable
fun VideoGrid(
    videos: List<VideoDto>,
    onVideoClick: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
    onLoadMore: () -> Unit = {},
    isLoadingMore: Boolean = false,
    loadMoreFailed: Boolean = false,
    state: LazyGridState = rememberLazyGridState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    header: LazyGridScope.() -> Unit = {},
    footer: LazyGridScope.() -> Unit = {},
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(GRID_COLUMNS),
        modifier = modifier,
        state = state,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(1.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        header()
        videoGridItems(videos, onVideoClick)
        if (isLoadingMore) fullWidthItem(LOADING_MORE_KEY) { LoadingMoreFooter() }
        footer()
    }
    LoadMoreEffect(state = state, enabled = !loadMoreFailed, onLoadMore = onLoadMore)
}

private const val LOADING_MORE_KEY = "video-grid:loading-more"

/** "v:<id>" for the first occurrence of an id, "v:<id>:<n>" for later ones. */
private fun uniqueVideoKeys(videos: List<VideoDto>): List<String> {
    val seen = HashMap<Long, Int>(videos.size)
    return videos.map { video ->
        val occurrence = seen[video.id] ?: 0
        seen[video.id] = occurrence + 1
        if (occurrence == 0) "v:${video.id}" else "v:${video.id}:$occurrence"
    }
}
