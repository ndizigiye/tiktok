package com.ndizigiye.loop.ui.discover

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.ndizigiye.loop.api.VideoDto
import com.ndizigiye.loop.data.PagedState
import com.ndizigiye.loop.ui.common.EmptyBox
import com.ndizigiye.loop.ui.common.ErrorBox
import com.ndizigiye.loop.ui.common.LoadMoreErrorFooter
import com.ndizigiye.loop.ui.common.LoadingBox
import com.ndizigiye.loop.ui.common.VideoGrid
import com.ndizigiye.loop.ui.common.fullWidthItem

internal const val GRID_COLUMNS = 3

/**
 * [VideoGrid] over a [PagedState]: [header] items first, then the videos, or a
 * loading, error or empty state below the header. Asks for the next page near the
 * end and shows a footer while it loads or when it failed.
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
    val videos = state.items
    val error = state.error
    VideoGrid(
        videos = videos,
        onVideoClick = onVideoClick,
        modifier = modifier,
        onLoadMore = onLoadMore,
        isLoadingMore = state.isLoading && videos.isNotEmpty(),
        loadMoreFailed = error != null,
        contentPadding = contentPadding,
        header = header,
        footer = {
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
                error != null -> fullWidthItem("grid:load-more-error") {
                    LoadMoreErrorFooter(message = error, onRetry = onRetry)
                }
            }
        },
    )
}
