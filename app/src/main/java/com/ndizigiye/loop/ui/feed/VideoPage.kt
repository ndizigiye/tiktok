package com.ndizigiye.loop.ui.feed

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import coil3.compose.AsyncImage
import com.ndizigiye.loop.api.VideoDto
import com.ndizigiye.loop.player.VideoSurface
import com.ndizigiye.loop.ui.common.rememberResolvedUrl
import com.ndizigiye.loop.ui.navigation.LocalPictureInPicture
import com.ndizigiye.loop.ui.navigation.PictureInPictureVideo
import com.ndizigiye.loop.ui.theme.LoopColors

private val BottomScrim = Brush.verticalGradient(listOf(Color.Transparent, Color(0x99000000)))

/**
 * One full-screen video of a feed. Layers, bottom to top: the video, its thumbnail
 * until the first frame is on screen, the tap layer (tap pauses or resumes, double
 * tap likes with a heart, long press opens the options), a bottom scrim, the
 * [ActionRail], the [VideoInfo] and the [PlaybackProgress] line. In a
 * picture-in-picture window only the video and its thumbnail remain.
 *
 * [isPlaying] says whether the page is meant to play right now (with
 * [allowPictureInPicture], leaving the app then continues it in picture-in-picture);
 * [isPaused] whether the viewer paused it by tapping. When [player] fails, the current
 * page says so and a tap prepares it again.
 */
@Composable
internal fun VideoPage(
    video: VideoDto,
    player: ExoPlayer?,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isPaused: Boolean,
    allowPictureInPicture: Boolean,
    actions: VideoActions,
    onTogglePause: () -> Unit,
    onOpenOptions: () -> Unit,
    onOpenComments: () -> Unit,
    onOpenShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val thumbnailUrl = rememberResolvedUrl(video.thumbnailUrl)
    val fill = video.height >= video.width
    val inPictureInPicture by LocalPictureInPicture.current.isActive.collectAsStateWithLifecycle()
    PictureInPictureVideo(playing = allowPictureInPicture && isPlaying)

    var firstFrameRendered by remember(player, video.id) { mutableStateOf(false) }
    var playbackFailed by remember(player, video.id) { mutableStateOf(player?.playerError != null) }
    DisposableEffect(player, video.id) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                firstFrameRendered = true
            }

            override fun onPlayerErrorChanged(error: PlaybackException?) {
                playbackFailed = error != null
            }
        }
        player?.addListener(listener)
        onDispose { player?.removeListener(listener) }
    }

    val hearts = rememberHeartBurstState()
    val currentVideo by rememberUpdatedState(video)
    val currentPlayer by rememberUpdatedState(player)
    val currentActions by rememberUpdatedState(actions)
    val currentOnTogglePause by rememberUpdatedState(onTogglePause)
    val currentOnOpenOptions by rememberUpdatedState(onOpenOptions)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        VideoSurface(player = player, modifier = Modifier.fillMaxSize(), fill = fill)

        // A SurfaceView punches through anything drawn below it, so the thumbnail
        // sits above the surface and fades once the first frame is rendered.
        if (thumbnailUrl != null) {
            AnimatedVisibility(
                visible = !firstFrameRendered,
                enter = EnterTransition.None,
                exit = fadeOut(tween(durationMillis = 150)),
            ) {
                AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = null,
                    contentScale = if (fill) ContentScale.Crop else ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // Picture-in-picture shows only the video: no overlays, and it takes no gestures.
        if (!inPictureInPicture) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                // A tap on a video that failed to play tries it again instead of pausing it.
                                val failedPlayer = currentPlayer?.takeIf { it.playerError != null }
                                if (failedPlayer != null) failedPlayer.prepare() else currentOnTogglePause()
                            },
                            onDoubleTap = { position ->
                                if (currentActions.likeByDoubleTap(currentVideo)) hearts.burst(position)
                            },
                            onLongPress = { currentOnOpenOptions() },
                        )
                    },
            )

            AnimatedVisibility(
                visible = isCurrent && isPaused && !playbackFailed,
                modifier = Modifier.align(Alignment.Center),
                enter = fadeIn() + scaleIn(initialScale = 1.4f),
                exit = fadeOut() + scaleOut(targetScale = 0.8f),
            ) {
                ShadowedIcon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Paused",
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(88.dp),
                )
            }

            if (isCurrent && playbackFailed) {
                PlaybackError(modifier = Modifier.align(Alignment.Center))
            }

            HeartBurst(state = hearts, modifier = Modifier.fillMaxSize())

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(0.4f)
                    .background(BottomScrim),
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding(),
            ) {
                ActionRail(
                    video = video,
                    isPlaying = isPlaying,
                    actions = actions,
                    onOpenComments = onOpenComments,
                    onOpenShare = onOpenShare,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 4.dp, bottom = 28.dp),
                )
                VideoInfo(
                    video = video,
                    isCurrent = isCurrent,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = ActionRailWidth + 16.dp, bottom = 28.dp),
                )
                PlaybackProgress(
                    player = player,
                    isCurrent = isCurrent,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                )
            }
        }
    }
}

/** Shown over a video that failed to play; the page's tap layer retries it. */
@Composable
private fun PlaybackError(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ShadowedIcon(
            imageVector = Icons.Rounded.Refresh,
            contentDescription = null,
            tint = LoopColors.OverlayText,
            modifier = Modifier.size(48.dp),
        )
        Text(
            text = "Couldn't play this video",
            style = MaterialTheme.typography.titleMedium.copy(shadow = OverlayTextShadow),
            color = LoopColors.OverlayText,
        )
        Text(
            text = "Tap to try again",
            style = MaterialTheme.typography.bodyMedium.copy(shadow = OverlayTextShadow),
            color = LoopColors.OverlayText.copy(alpha = 0.8f),
        )
    }
}
