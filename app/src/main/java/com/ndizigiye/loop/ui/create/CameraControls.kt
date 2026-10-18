package com.ndizigiye.loop.ui.create

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ndizigiye.loop.ui.theme.LoopColors

private val DisabledOverlay = Color.White.copy(alpha = 0.38f)

/** Close button on the left and the selected sound centred above the preview. */
@Composable
internal fun CameraTopBar(
    onClose: () -> Unit,
    closeEnabled: Boolean,
    soundLabel: String,
    onSoundClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 4.dp),
    ) {
        IconButton(
            onClick = onClose,
            enabled = closeEnabled,
            modifier = Modifier.align(Alignment.CenterStart),
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = LoopColors.OverlayText,
                disabledContentColor = DisabledOverlay,
            ),
        ) {
            Icon(imageVector = Icons.Filled.Close, contentDescription = "Close")
        }
        SoundPill(
            label = soundLabel,
            onClick = onSoundClick,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

/** A pill with a music note and [label]; tappable only when [onClick] is set. */
@Composable
private fun SoundPill(label: String, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    val clickable = if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier
    Row(
        modifier = modifier
            .widthIn(max = 220.dp)
            .clip(RoundedCornerShape(50))
            .background(LoopColors.OverlayScrim)
            .then(clickable)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.MusicNote,
            contentDescription = null,
            tint = LoopColors.OverlayText,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = LoopColors.OverlayText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

/** The vertical toolbar on the right: flip camera, 3-second timer and torch. */
@Composable
internal fun CameraToolbar(
    canFlip: Boolean,
    onFlip: () -> Unit,
    timerEnabled: Boolean,
    onToggleTimer: () -> Unit,
    flashAvailable: Boolean,
    torchOn: Boolean,
    onToggleTorch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LabelledIconButton(
            icon = Icons.Filled.Cameraswitch,
            label = "Flip",
            onClick = onFlip,
            enabled = canFlip,
        )
        LabelledIconButton(
            icon = Icons.Filled.Timer,
            label = if (timerEnabled) "3s" else "Timer",
            onClick = onToggleTimer,
            active = timerEnabled,
        )
        LabelledIconButton(
            icon = if (torchOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
            label = "Flash",
            onClick = onToggleTorch,
            enabled = flashAvailable,
            active = torchOn,
        )
    }
}

/** An overlay icon with a small caption underneath; [active] tints it as switched on. */
@Composable
internal fun LabelledIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    active: Boolean = false,
) {
    val tint = when {
        !enabled -> DisabledOverlay
        active -> LoopColors.AccentLight
        else -> LoopColors.OverlayText
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(28.dp),
        )
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = tint)
    }
}
