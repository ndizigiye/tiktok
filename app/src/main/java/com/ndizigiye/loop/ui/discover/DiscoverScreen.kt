package com.ndizigiye.loop.ui.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ndizigiye.loop.api.SoundDto
import com.ndizigiye.loop.api.TrendingDto
import com.ndizigiye.loop.api.UserDto
import com.ndizigiye.loop.api.VideoDto
import com.ndizigiye.loop.ui.common.EmptyBox
import com.ndizigiye.loop.ui.common.ErrorBox
import com.ndizigiye.loop.ui.common.LoadingBox
import com.ndizigiye.loop.ui.common.LocalSnackbarHostState
import com.ndizigiye.loop.ui.navigation.LocalAppContainer
import com.ndizigiye.loop.ui.navigation.LocalNavigator

/** The Discover tab: a search entry point, trending creators, hashtags and sounds. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(modifier: Modifier = Modifier) {
    val container = LocalAppContainer.current
    val navigator = LocalNavigator.current
    val snackbarHostState = LocalSnackbarHostState.current
    val viewModel: DiscoverViewModel = viewModel {
        DiscoverViewModel(container.discoverRepository, container.sessionStore)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel, snackbarHostState) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        SearchFieldButton(
            onClick = navigator::openSearch,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            DiscoverContent(state = state, onRetry = viewModel::retry)
        }
    }
}

@Composable
private fun DiscoverContent(state: DiscoverUiState, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val navigator = LocalNavigator.current
    val videoRepository = LocalAppContainer.current.videoRepository
    val trending = state.trending
    val error = state.error
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        when {
            trending == null && error != null -> item(key = "error") {
                ErrorBox(message = error, onRetry = onRetry, modifier = Modifier.fillParentMaxSize())
            }
            trending == null -> item(key = "loading") {
                LoadingBox(modifier = Modifier.fillParentMaxSize())
            }
            trending.isEmpty() -> item(key = "empty") {
                EmptyBox(
                    title = "Nothing trending yet",
                    subtitle = "Popular hashtags, sounds and creators will show up here.",
                    icon = Icons.Outlined.Explore,
                    modifier = Modifier.fillParentMaxSize(),
                )
            }
            else -> trendingItems(
                trending = trending,
                onCreatorClick = { user -> navigator.openProfile(user.username) },
                onHashtagClick = navigator::openHashtag,
                onVideoClick = { hashtag, videos, index ->
                    navigator.openVideoPager(
                        videos,
                        index,
                        null,
                        loadMore = { cursor -> videoRepository.hashtagVideos(hashtag, cursor) },
                    )
                },
                onSoundClick = { sound -> navigator.openSound(sound.id) },
            )
        }
    }
}

private fun TrendingDto.isEmpty(): Boolean = hashtags.isEmpty() && sounds.isEmpty() && creators.isEmpty()

private fun LazyListScope.trendingItems(
    trending: TrendingDto,
    onCreatorClick: (UserDto) -> Unit,
    onHashtagClick: (name: String) -> Unit,
    onVideoClick: (hashtag: String, videos: List<VideoDto>, index: Int) -> Unit,
    onSoundClick: (SoundDto) -> Unit,
) {
    if (trending.creators.isNotEmpty()) {
        item(key = "creators-title", contentType = "title") { SectionTitle("Trending creators") }
        item(key = "creators", contentType = "creators") {
            CreatorRow(creators = trending.creators, onCreatorClick = onCreatorClick)
        }
    }
    itemsIndexed(
        items = trending.hashtags,
        key = { index, item -> "hashtag:$index:${item.hashtag.name}" },
        contentType = { _, _ -> "hashtag" },
    ) { _, item ->
        val name = item.hashtag.name
        TrendingHashtagSection(
            trending = item,
            onHeaderClick = { onHashtagClick(name) },
            onVideoClick = { index -> onVideoClick(name, item.videos, index) },
        )
    }
    if (trending.sounds.isNotEmpty()) {
        item(key = "sounds-title", contentType = "title") { SectionTitle("Trending sounds") }
        itemsIndexed(
            items = trending.sounds,
            key = { index, sound -> "sound:$index:${sound.id}" },
            contentType = { _, _ -> "sound" },
        ) { _, sound ->
            TrendingSoundRow(sound = sound, onClick = { onSoundClick(sound) })
        }
    }
}
