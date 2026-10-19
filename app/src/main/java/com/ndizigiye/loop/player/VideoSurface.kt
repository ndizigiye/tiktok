package com.ndizigiye.loop.player

import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

/**
 * Renders [player]'s video without any controls. [fill] crops the video to fill
 * the bounds (feed pages); otherwise it is letterboxed. Shows black until the
 * first frame and keeps the last frame while the player is swapped.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun VideoSurface(player: ExoPlayer?, modifier: Modifier = Modifier, fill: Boolean = true) {
    AndroidView(
        factory = { context ->
            PlayerView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                useController = false
                setShutterBackgroundColor(android.graphics.Color.BLACK)
                setKeepContentOnPlayerReset(true)
            }
        },
        modifier = modifier,
        onReset = { view -> view.player = null },
        onRelease = { view -> view.player = null },
        update = { view ->
            view.resizeMode = if (fill) {
                AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            } else {
                AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
            if (view.player !== player) view.player = player
        },
    )
}
