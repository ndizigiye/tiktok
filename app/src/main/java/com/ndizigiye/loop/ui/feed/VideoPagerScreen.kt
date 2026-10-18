package com.ndizigiye.loop.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.VideocamOff
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ndizigiye.loop.ui.common.EmptyBox
import com.ndizigiye.loop.ui.common.rememberBackAction
import com.ndizigiye.loop.ui.navigation.LocalAppContainer
import com.ndizigiye.loop.ui.navigation.LocalPictureInPicture
import com.ndizigiye.loop.ui.theme.LoopColors

private val TopScrim = Brush.verticalGradient(listOf(Color(0x59000000), Color.Transparent))

/**
 * Full-screen pager over a list handed off by [sourceKey] (see VideoListHandoff),
 * opened at [startIndex]. Without that list it shows just [videoId]. Leaving the app
 * while a video plays here continues it in picture-in-picture.
 */
@Composable
fun VideoPagerScreen(sourceKey: String?, startIndex: Int, videoId: Long?) {
    val container = LocalAppContainer.current
    val viewModel: VideoPagerViewModel = viewModel(key = sourceKey ?: "video:$videoId") {
        VideoPagerViewModel(
            source = sourceKey?.let { container.videoListHandoff.get(it) },
            startIndex = startIndex,
            videoId = videoId,
            videoRepository = container.videoRepository,
            applicationScope = container.applicationScope,
        )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val inPictureInPicture by LocalPictureInPicture.current.isActive.collectAsStateWithLifecycle()
    val onBack = rememberBackAction()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        VerticalVideoFeed(
            state = state,
            isActive = true,
            onLoadMore = viewModel::loadMore,
            onRetry = viewModel::retry,
            onViewed = viewModel::recordView,
            onRemove = viewModel::remove,
            modifier = Modifier.fillMaxSize(),
            initialPage = viewModel.initialPage,
            allowPictureInPicture = true,
            emptyContent = {
                EmptyBox(title = "No videos left here", icon = Icons.Outlined.VideocamOff)
            },
        )
        // A picture-in-picture window shows only the video.
        if (!inPictureInPicture) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(112.dp)
                    .background(TopScrim),
            )
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(4.dp),
            ) {
                ShadowedIcon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = LoopColors.OverlayText,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
    }
}
