package com.ndizigiye.loop.ui.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.outlined.MusicOff
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ndizigiye.loop.api.SoundDto
import com.ndizigiye.loop.api.UserDto
import com.ndizigiye.loop.ui.common.EmptyBox
import com.ndizigiye.loop.ui.common.ErrorBox
import com.ndizigiye.loop.ui.common.LoadingBox
import com.ndizigiye.loop.ui.common.LocalSnackbarHostState
import com.ndizigiye.loop.ui.common.LoopTopBar
import com.ndizigiye.loop.ui.common.fullWidthItem
import com.ndizigiye.loop.ui.navigation.LocalAppContainer
import com.ndizigiye.loop.ui.navigation.LocalNavigator
import com.ndizigiye.loop.ui.theme.LoopColors
import com.ndizigiye.loop.ui.theme.LoopViolet

/** Sound page: the sound, a grid of the videos using it and a button to record with it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundScreen(soundId: Long) {
    val container = LocalAppContainer.current
    val navigator = LocalNavigator.current
    val snackbarHostState = LocalSnackbarHostState.current
    val viewModel: SoundViewModel = viewModel(key = "sound:$soundId") {
        SoundViewModel(soundId, container.discoverRepository, container.videoRepository)
    }
    val headerState by viewModel.header.collectAsStateWithLifecycle()
    val videos by viewModel.videos.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isPullRefreshing.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel, snackbarHostState) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    // Every video carries its sound, so the grid can stand in for a failed sound request.
    val sound = headerState.value ?: videos.items.firstOrNull()?.sound
    val headerError = headerState.error

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        LoopTopBar(title = "Sound")
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = viewModel::pullRefresh,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            when {
                sound != null -> PagedVideoGrid(
                    state = videos,
                    emptyTitle = "No videos with this sound yet",
                    emptyIcon = Icons.Outlined.VideoLibrary,
                    onVideoClick = { index ->
                        navigator.openVideoPager(videos.items, index, videos.nextCursor, viewModel.pageLoader)
                    },
                    onLoadMore = viewModel::loadMore,
                    onRetry = viewModel::retry,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 8.dp),
                    header = {
                        fullWidthItem("sound-header") {
                            SoundHeader(sound = sound, coverUser = videos.items.firstOrNull()?.author)
                        }
                    },
                )
                headerState.notFound -> EmptyBox(
                    title = "This sound isn't available",
                    subtitle = "It may have been removed.",
                    icon = Icons.Outlined.MusicOff,
                )
                headerError != null && !headerState.isLoading && !videos.isLoading -> ErrorBox(
                    message = headerError,
                    onRetry = viewModel::retry,
                )
                else -> LoadingBox()
            }
        }
        if (!headerState.notFound) {
            UseSoundBar(onClick = { if (navigator.requireAuth()) navigator.openCamera(soundId) })
        }
    }
}

@Composable
private fun SoundHeader(sound: SoundDto, coverUser: UserDto?, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SoundCoverDisc(coverUrl = sound.coverUrl, size = 96.dp, fallbackUser = coverUser, rotating = true)
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = sound.title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = sound.authorName,
                style = MaterialTheme.typography.bodyMedium,
                color = LoopColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = videosLabel(sound.videoCount),
                style = MaterialTheme.typography.bodySmall,
                color = LoopColors.TextMuted,
                maxLines = 1,
            )
        }
    }
}

/** The full-width "Use this sound" button pinned above the navigation bar. */
@Composable
private fun UseSoundBar(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Button(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = LoopViolet, contentColor = Color.White),
        ) {
            Icon(
                imageVector = Icons.Filled.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(text = "Use this sound", style = MaterialTheme.typography.labelLarge)
        }
    }
}
