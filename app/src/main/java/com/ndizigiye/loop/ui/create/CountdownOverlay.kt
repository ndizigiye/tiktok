package com.ndizigiye.loop.ui.create

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private val CountdownStyle = TextStyle(
    color = Color.White,
    fontSize = 120.sp,
    fontWeight = FontWeight.Bold,
    shadow = Shadow(color = Color(0x66000000), offset = Offset(0f, 4f), blurRadius = 16f),
)

/** The timer's big "3, 2, 1", each number popping in from a larger size. */
@Composable
internal fun CountdownOverlay(seconds: Int, modifier: Modifier = Modifier) {
    val scale = remember(seconds) { Animatable(1.8f) }
    val fade = remember(seconds) { Animatable(0f) }
    LaunchedEffect(seconds) {
        launch { fade.animateTo(1f, tween(durationMillis = 200)) }
        scale.animateTo(1f, tween(durationMillis = 500, easing = FastOutSlowInEasing))
    }
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = seconds.toString(),
            style = CountdownStyle,
            modifier = Modifier.graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                alpha = fade.value
            },
        )
    }
}
