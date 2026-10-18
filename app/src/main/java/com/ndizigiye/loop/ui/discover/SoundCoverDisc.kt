package com.ndizigiye.loop.ui.discover

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import com.ndizigiye.loop.api.UserDto
import com.ndizigiye.loop.ui.common.Avatar
import com.ndizigiye.loop.ui.common.rememberResolvedUrl
import com.ndizigiye.loop.ui.theme.LoopColors

private const val TURN_MS = 8_000

/** Light and dark grooves, so the rotation is visible. */
private val GrooveBrush = Brush.sweepGradient(
    listOf(
        Color(0xFF333333),
        Color(0xFF111111),
        Color(0xFF333333),
        Color(0xFF111111),
        Color(0xFF333333),
    ),
)

/**
 * A record whose label is the sound's cover, else [fallbackUser]'s avatar, else a
 * music note. [rotating] turns it slowly.
 */
@Composable
internal fun SoundCoverDisc(
    coverUrl: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    fallbackUser: UserDto? = null,
    rotating: Boolean = false,
) {
    val cover = rememberResolvedUrl(coverUrl)
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(rotating) {
        if (!rotating) return@LaunchedEffect
        // One infinite animation rather than a loop of finite ones, so Compose knows it never
        // ends (InfiniteAnimationPolicy): otherwise UI tests never see this screen idle.
        val start = rotation.value % 360f
        rotation.snapTo(start)
        rotation.animateTo(start + 360f, infiniteRepeatable(tween(TURN_MS, easing = LinearEasing)))
    }

    val labelSize = size * 0.64f
    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { rotationZ = rotation.value }
            .clip(CircleShape)
            .background(GrooveBrush),
        contentAlignment = Alignment.Center,
    ) {
        if (cover == null && fallbackUser != null) {
            Avatar(user = fallbackUser, size = labelSize)
        } else {
            Box(
                modifier = Modifier
                    .size(labelSize)
                    .clip(CircleShape)
                    .background(LoopColors.Accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(labelSize * 0.5f),
                )
                if (cover != null) {
                    AsyncImage(
                        model = cover,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize(),
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .size(size * 0.08f)
                .clip(CircleShape)
                .background(Color.Black),
        )
    }
}
