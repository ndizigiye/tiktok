package com.ndizigiye.loop.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ndizigiye.loop.api.VideoDto
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

private const val GRID_COLUMNS = 3
private const val LOAD_MORE_THRESHOLD = 6

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

/**
 * Three-column grid of video thumbnails with 1 dp gaps. [header] items come first;
 * give them `span = { GridItemSpan(maxLineSpan) }` to take the full width.
 * [onLoadMore] is called whenever the user scrolls near the end (and again each
 * time the list grows while still near the end).
 */
@Composable
fun VideoGrid(
    videos: List<VideoDto>,
    onVideoClick: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
    onLoadMore: () -> Unit = {},
    isLoadingMore: Boolean = false,
    state: LazyGridState = rememberLazyGridState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    header: LazyGridScope.() -> Unit = {},
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
        if (isLoadingMore) {
            item(key = LOADING_MORE_KEY, span = { GridItemSpan(maxLineSpan) }, contentType = "loading") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp,
                    )
                }
            }
        }
    }

    val currentOnLoadMore by rememberUpdatedState(onLoadMore)
    LaunchedEffect(state) {
        snapshotFlow {
            val layoutInfo = state.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            val total = layoutInfo.totalItemsCount
            if (total > 0 && lastVisible >= total - LOAD_MORE_THRESHOLD) total else -1
        }
            .distinctUntilChanged()
            .filter { it >= 0 }
            .collect { currentOnLoadMore() }
    }
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
