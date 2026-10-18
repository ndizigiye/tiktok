package com.ndizigiye.loop.ui.feed

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.ndizigiye.loop.ui.theme.LoopColors
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.random.Random

private val HeartSize = 96.dp
private val RiseDistance = 80.dp
private const val MAX_TILT_DEGREES = 20f
private const val TOTAL_MS = 800
private const val POP_MS = 220
private const val RISE_DELAY_MS = 160
private const val FADE_DELAY_MS = 340
private const val OVERSHOOT_TENSION = 2.2f

/** Pops past full size and settles back, like an overshooting spring. */
private val OvershootEasing = Easing { fraction ->
    val t = fraction - 1f
    t * t * ((OVERSHOOT_TENSION + 1f) * t + OVERSHOOT_TENSION) + 1f
}

/** Test tag of a floating heart: it is decorative, so it has no text or description to find it by. */
internal const val FLOATING_HEART_TAG = "floating_heart"

internal class Heart(val id: Long, val position: Offset, val tiltDegrees: Float)

/** The hearts currently floating over a page; [burst] adds one at a tap position. */
@Stable
internal class HeartBurstState {
    internal val hearts = mutableStateListOf<Heart>()
    private var nextId = 0L

    fun burst(position: Offset) {
        val tilt = Random.nextFloat() * 2f * MAX_TILT_DEGREES - MAX_TILT_DEGREES
        hearts += Heart(id = nextId++, position = position, tiltDegrees = tilt)
    }

    internal fun remove(heart: Heart) {
        hearts.remove(heart)
    }
}

@Composable
internal fun rememberHeartBurstState(): HeartBurstState = remember { HeartBurstState() }

/**
 * Draws the hearts of [state]: each pops up with a random tilt where the page was
 * double-tapped, floats up and fades out, then removes itself. [modifier] should
 * give this layer the same bounds as the area receiving the taps.
 */
@Composable
internal fun HeartBurst(state: HeartBurstState, modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        for (heart in state.hearts) {
            key(heart.id) {
                FloatingHeart(heart = heart, onFinished = { state.remove(heart) })
            }
        }
    }
}

@Composable
private fun FloatingHeart(heart: Heart, onFinished: () -> Unit) {
    val density = LocalDensity.current
    val halfSizePx = with(density) { HeartSize.toPx() } / 2f
    val risePx = with(density) { RiseDistance.toPx() }
    val scale = remember { Animatable(0f) }
    val rise = remember { Animatable(0f) }
    val opacity = remember { Animatable(1f) }
    val currentOnFinished by rememberUpdatedState(onFinished)

    LaunchedEffect(heart.id) {
        coroutineScope {
            launch { scale.animateTo(1f, tween(POP_MS, easing = OvershootEasing)) }
            launch {
                delay(RISE_DELAY_MS.toLong())
                rise.animateTo(1f, tween(TOTAL_MS - RISE_DELAY_MS, easing = FastOutSlowInEasing))
            }
            launch {
                delay(FADE_DELAY_MS.toLong())
                opacity.animateTo(0f, tween(TOTAL_MS - FADE_DELAY_MS, easing = LinearEasing))
            }
        }
        currentOnFinished()
    }

    Icon(
        imageVector = Icons.Filled.Favorite,
        contentDescription = null,
        tint = LoopColors.Like,
        modifier = Modifier
            .offset {
                IntOffset(
                    x = (heart.position.x - halfSizePx).roundToInt(),
                    y = (heart.position.y - halfSizePx).roundToInt(),
                )
            }
            .size(HeartSize)
            .testTag(FLOATING_HEART_TAG)
            .graphicsLayer {
                val grow = 1f + 0.15f * rise.value
                scaleX = scale.value * grow
                scaleY = scale.value * grow
                rotationZ = heart.tiltDegrees
                translationY = -rise.value * risePx
                alpha = opacity.value
            },
    )
}
