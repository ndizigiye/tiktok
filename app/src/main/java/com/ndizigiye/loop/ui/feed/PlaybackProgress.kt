package com.ndizigiye.loop.ui.feed

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.ndizigiye.loop.common.formatDuration
import com.ndizigiye.loop.ui.theme.LoopColors
import kotlinx.coroutines.delay

private val TrackHeight = 2.dp
private val ScrubTrackHeight = 6.dp
private val TouchHeight = 24.dp
private const val POLL_INTERVAL_MS = 100L
private val TrackColor = Color(0x40FFFFFF)
private val PlayedColor = Color(0xCCFFFFFF)

/**
 * A thin progress line for [player], polled while [isCurrent]. Dragging
 * horizontally anywhere on its (taller) touch area scrubs: the line thickens, the
 * target time shows above it, and the player seeks when the finger lifts.
 */
@Composable
internal fun PlaybackProgress(
    player: Player?,
    isCurrent: Boolean,
    modifier: Modifier = Modifier,
) {
    var positionMs by remember(player) { mutableLongStateOf(0L) }
    var durationMs by remember(player) { mutableLongStateOf(0L) }
    var scrubFraction by remember(player) { mutableStateOf<Float?>(null) }

    LaunchedEffect(player, isCurrent) {
        if (player == null || !isCurrent) {
            positionMs = 0L
            return@LaunchedEffect
        }
        while (true) {
            durationMs = player.duration.coerceAtLeast(0L)
            positionMs = player.currentPosition.coerceAtLeast(0L)
            delay(POLL_INTERVAL_MS)
        }
    }

    val scrub = scrubFraction
    val thickness by animateDpAsState(
        targetValue = if (scrub != null) ScrubTrackHeight else TrackHeight,
        label = "progressThickness",
    )
    val playedFraction = scrub
        ?: if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (scrub != null) {
            Text(
                text = "${formatDuration((scrub * durationMs).toLong())} / ${formatDuration(durationMs)}",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    shadow = OverlayTextShadow,
                ),
                color = LoopColors.OverlayText,
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(TouchHeight)
                .pointerInput(player) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            if (player != null && durationMs > 0L) {
                                scrubFraction = (offset.x / size.width).coerceIn(0f, 1f)
                            }
                        },
                        onDragEnd = {
                            val fraction = scrubFraction
                            if (player != null && fraction != null) {
                                val target = (fraction * durationMs).toLong()
                                player.seekTo(target)
                                positionMs = target
                            }
                            scrubFraction = null
                        },
                        onDragCancel = { scrubFraction = null },
                        onHorizontalDrag = { change, dragAmount ->
                            scrubFraction?.let { fraction ->
                                change.consume()
                                scrubFraction = (fraction + dragAmount / size.width).coerceIn(0f, 1f)
                            }
                        },
                    )
                },
            contentAlignment = Alignment.BottomCenter,
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(thickness),
            ) {
                drawRect(color = TrackColor)
                drawRect(
                    color = if (scrub != null) LoopColors.OverlayText else PlayedColor,
                    size = Size(size.width * playedFraction, size.height),
                )
            }
        }
    }
}
