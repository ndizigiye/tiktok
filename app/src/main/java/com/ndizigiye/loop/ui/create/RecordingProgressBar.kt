package com.ndizigiye.loop.ui.create

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ndizigiye.loop.api.ApiLimits
import com.ndizigiye.loop.ui.theme.LoopViolet

private val TrackColor = Color(0x4DFFFFFF)

/**
 * Recorded time out of [ApiLimits.MAX_RECORDING_MS] as a violet fill, with a white
 * tick wherever one segment ends and the next begins.
 */
@Composable
internal fun RecordingProgressBar(
    recordedMs: Long,
    segmentEndsMs: List<Long>,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp)),
    ) {
        val maxMs = ApiLimits.MAX_RECORDING_MS.toFloat()
        drawRect(color = TrackColor)
        drawRect(
            color = LoopViolet,
            size = Size(size.width * (recordedMs / maxMs).coerceIn(0f, 1f), size.height),
        )
        val tickWidth = 2.dp.toPx()
        for (endMs in segmentEndsMs) {
            val x = size.width * (endMs / maxMs).coerceIn(0f, 1f)
            drawRect(
                color = Color.White,
                topLeft = Offset((x - tickWidth).coerceAtLeast(0f), 0f),
                size = Size(tickWidth, size.height),
            )
        }
    }
}
