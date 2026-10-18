package com.ndizigiye.loop.ui.feed

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** Soft shadow that keeps white overlay text legible on bright video. */
internal val OverlayTextShadow = Shadow(color = Color(0x73000000), offset = Offset(0f, 1.5f), blurRadius = 4f)

private val IconShadowColor = Color(0x40000000)

/** An icon drawn over video, with a faint drop shadow underneath it. */
@Composable
internal fun ShadowedIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        Icon(
            imageVector = imageVector,
            contentDescription = null,
            tint = IconShadowColor,
            modifier = Modifier
                .matchParentSize()
                .offset(y = 1.dp),
        )
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.matchParentSize(),
        )
    }
}
