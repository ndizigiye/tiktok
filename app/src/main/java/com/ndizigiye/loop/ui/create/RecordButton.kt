package com.ndizigiye.loop.ui.create

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ndizigiye.loop.ui.theme.LoopViolet

/**
 * The 80 dp record button: a white ring around a violet circle that shrinks into a
 * rounded square while [recording].
 */
@Composable
internal fun RecordButton(
    recording: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val innerSize by animateDpAsState(
        targetValue = if (recording) 34.dp else 64.dp,
        label = "recordInnerSize",
    )
    val innerCorner by animateDpAsState(
        targetValue = if (recording) 8.dp else 32.dp,
        label = "recordInnerCorner",
    )
    val alpha = if (enabled) 1f else 0.5f
    Box(
        modifier = modifier
            .size(80.dp)
            .clip(CircleShape)
            .border(width = 5.dp, color = Color.White.copy(alpha = alpha), shape = CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = if (recording) "Pause recording" else "Record" },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(innerSize)
                .clip(RoundedCornerShape(innerCorner))
                .background(LoopViolet.copy(alpha = alpha)),
        )
    }
}
