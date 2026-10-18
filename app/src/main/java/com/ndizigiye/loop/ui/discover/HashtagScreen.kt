package com.ndizigiye.loop.ui.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ndizigiye.loop.api.HashtagDto
import com.ndizigiye.loop.ui.common.LocalSnackbarHostState
import com.ndizigiye.loop.ui.common.LoopTopBar
import com.ndizigiye.loop.ui.common.fullWidthItem
import com.ndizigiye.loop.ui.navigation.LocalAppContainer
import com.ndizigiye.loop.ui.navigation.LocalNavigator
import com.ndizigiye.loop.ui.theme.LoopColors

/** Hashtag page: its totals and a grid of its videos, most viewed first. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HashtagScreen(name: String) {
    val container = LocalAppContainer.current
    val navigator = LocalNavigator.current
    val snackbarHostState = LocalSnackbarHostState.current
    val tag = name.removePrefix("#").lowercase()
    val viewModel: HashtagViewModel = viewModel(key = "hashtag:$tag") {
        HashtagViewModel(tag, container.discoverRepository, container.videoRepository)
    }
    val headerState by viewModel.header.collectAsStateWithLifecycle()
    val videos by viewModel.videos.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isPullRefreshing.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel, snackbarHostState) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        LoopTopBar(title = "#$tag")
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = viewModel::pullRefresh,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            PagedVideoGrid(
                state = videos,
                emptyTitle = "No videos with #$tag yet",
                emptyIcon = Icons.Outlined.VideoLibrary,
                onVideoClick = { index ->
                    navigator.openVideoPager(videos.items, index, videos.nextCursor, viewModel.pageLoader)
                },
                onLoadMore = viewModel::loadMore,
                onRetry = viewModel::retry,
                modifier = Modifier.fillMaxSize(),
                contentPadding = WindowInsets.navigationBars.asPaddingValues(),
                header = {
                    fullWidthItem("hashtag-header") { HashtagHeader(name = tag, state = headerState) }
                },
            )
        }
    }
}

@Composable
private fun HashtagHeader(name: String, state: CollectionHeaderState<HashtagDto>, modifier: Modifier = Modifier) {
    val hashtag = state.value
    val stats = when {
        hashtag != null -> "${videosLabel(hashtag.videoCount)} · ${viewsLabel(hashtag.viewCount)}"
        state.notFound -> "${videosLabel(0)} · ${viewsLabel(0)}"
        else -> ""
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HashtagBadge(size = 88.dp)
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "#$name",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stats,
                style = MaterialTheme.typography.bodyMedium,
                color = LoopColors.TextSecondary,
                maxLines = 1,
            )
        }
    }
}
