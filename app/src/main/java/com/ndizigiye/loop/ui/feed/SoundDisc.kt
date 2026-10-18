package com.ndizigiye.loop.ui.feed

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ndizigiye.loop.api.UserDto
import com.ndizigiye.loop.ui.common.Avatar

private val DiscSize = 44.dp
private val LabelSize = 24.dp
private const val TURN_MS = 4_000

/** Light streaks on a dark disc, so the rotation is visible. */
private val DiscBrush = Brush.sweepGradient(
    listOf(
        Color(0xFF2E2E2E),
        Color(0xFF0E0E0E),
        Color(0xFF2E2E2E),
        Color(0xFF0E0E0E),
        Color(0xFF2E2E2E),
    ),
)

/**
 * A small dark record with [author]'s avatar as its label. It turns only while
 * [isPlaying] and stops where it is when playback pauses.
 */
@Composable
internal fun SoundDisc(
    author: UserDto,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(isPlaying) {
        if (!isPlaying) return@LaunchedEffect
        // One infinite animation rather than a loop of finite ones, so Compose knows it never
        // ends (InfiniteAnimationPolicy): otherwise UI tests never see this screen idle.
        val start = rotation.value % 360f
        rotation.snapTo(start)
        rotation.animateTo(start + 360f, infiniteRepeatable(tween(TURN_MS, easing = LinearEasing)))
    }

    Box(
        modifier = modifier
            .size(DiscSize)
            .graphicsLayer { rotationZ = rotation.value }
            .clip(CircleShape)
            .background(DiscBrush)
            .border(width = 1.dp, color = Color(0x26FFFFFF), shape = CircleShape)
            .clickable(onClickLabel = "Open sound", role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Avatar(user = author, size = LabelSize)
    }
}
