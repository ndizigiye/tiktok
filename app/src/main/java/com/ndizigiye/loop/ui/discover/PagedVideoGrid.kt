package com.ndizigiye.loop.ui.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ndizigiye.loop.api.VideoDto
import com.ndizigiye.loop.data.PagedState
import com.ndizigiye.loop.ui.common.EmptyBox
import com.ndizigiye.loop.ui.common.ErrorBox
import com.ndizigiye.loop.ui.common.LoadingBox
import com.ndizigiye.loop.ui.common.videoGridItems
import com.ndizigiye.loop.ui.theme.LoopColors
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

internal const val GRID_COLUMNS = 3
private const val LOAD_MORE_THRESHOLD = 6

/** An item spanning the whole width of a grid; [key] doubles as its content type. */
internal fun LazyGridScope.fullWidthItem(key: String, content: @Composable LazyGridItemScope.() -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }, contentType = key, content = content)
}

/**
 * Three-column grid over a [PagedState]: [header] items first, then the videos, or
 * a loading, error or empty state below the header. Asks for the next page near
 * the end and shows a footer while it loads or when it failed.
 */
@Composable
internal fun PagedVideoGrid(
    state: PagedState<VideoDto>,
    emptyTitle: String,
    onVideoClick: (index: Int) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    emptyIcon: ImageVector? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    header: LazyGridScope.() -> Unit = {},
) {
    val gridState = rememberLazyGridState()
    LazyVerticalGrid(
        columns = GridCells.Fixed(GRID_COLUMNS),
        modifier = modifier,
        state = gridState,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(1.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        header()
        pagedVideoItems(state, emptyTitle, emptyIcon, onVideoClick, onRetry)
    }
    LoadMoreEffect(gridState, onLoadMore)
}

private fun LazyGridScope.pagedVideoItems(
    state: PagedState<VideoDto>,
    emptyTitle: String,
    emptyIcon: ImageVector?,
    onVideoClick: (index: Int) -> Unit,
    onRetry: () -> Unit,
) {
    val videos = state.items
    val error = state.error
    when {
        videos.isEmpty() && error != null -> fullWidthItem("grid:error") {
            ErrorBox(message = error, onRetry = onRetry)
        }
        videos.isEmpty() && (state.isLoading || !state.loadedOnce) -> fullWidthItem("grid:loading") {
            LoadingBox(modifier = Modifier.height(240.dp))
        }
        videos.isEmpty() -> fullWidthItem("grid:empty") {
            EmptyBox(title = emptyTitle, icon = emptyIcon)
        }
        else -> {
            videoGridItems(videos, onVideoClick)
            if (state.isLoading) {
                fullWidthItem("grid:loading-more") { LoadingMoreFooter() }
            } else if (error != null) {
                fullWidthItem("grid:load-more-error") { LoadMoreErrorFooter(message = error, onRetry = onRetry) }
            }
        }
    }
}

@Composable
private fun LoadingMoreFooter() {
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

@Composable
private fun LoadMoreErrorFooter(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = LoopColors.TextSecondary,
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = onRetry) { Text("Try again") }
    }
}

/** Calls [onLoadMore] when the user nears the end, and again whenever the list grows while still near it. */
@Composable
private fun LoadMoreEffect(state: LazyGridState, onLoadMore: () -> Unit) {
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
