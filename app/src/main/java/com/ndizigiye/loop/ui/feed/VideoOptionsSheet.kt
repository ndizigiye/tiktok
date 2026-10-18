package com.ndizigiye.loop.ui.feed

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.HeartBroken
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ndizigiye.loop.api.VideoDto
import com.ndizigiye.loop.ui.navigation.LocalAppContainer
import com.ndizigiye.loop.ui.theme.LoopColors
import kotlinx.coroutines.launch

private val PlaybackSpeeds = listOf(0.5f, 1f, 1.5f, 2f)

/**
 * Long-press options for [video]: playback speed, "Not interested" (others'
 * videos) or "Delete video" (own videos, after a confirmation dialog), and
 * "Report". The action callbacks run once the sheet has slid away; [onDismiss]
 * ends the whole flow and is always called last.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VideoOptionsSheet(
    video: VideoDto,
    speed: Float,
    onSpeedChange: (Float) -> Unit,
    onNotInterested: () -> Unit,
    onDelete: () -> Unit,
    onReport: () -> Unit,
    onDismiss: () -> Unit,
) {
    val session by LocalAppContainer.current.sessionStore.session.collectAsStateWithLifecycle()
    val isOwnVideo = video.author.isMe || session?.user?.id == video.author.id
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var confirmingDelete by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    val hideThen: (() -> Unit) -> Unit = { action ->
        if (!closing) {
            closing = true
            scope.launch { sheetState.hide() }.invokeOnCompletion { action() }
        }
    }

    if (confirmingDelete) {
        DeleteVideoDialog(
            onConfirm = {
                onDelete()
                onDismiss()
            },
            onDismiss = onDismiss,
        )
    } else {
        OptionsSheetContent(
            isOwnVideo = isOwnVideo,
            speed = speed,
            sheetState = sheetState,
            onSpeedChange = { option ->
                onSpeedChange(option)
                hideThen(onDismiss)
            },
            onNotInterested = {
                hideThen {
                    onNotInterested()
                    onDismiss()
                }
            },
            onDelete = { hideThen { confirmingDelete = true } },
            onReport = {
                hideThen {
                    onReport()
                    onDismiss()
                }
            },
            onDismiss = onDismiss,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OptionsSheetContent(
    isOwnVideo: Boolean,
    speed: Float,
    sheetState: SheetState,
    onSpeedChange: (Float) -> Unit,
    onNotInterested: () -> Unit,
    onDelete: () -> Unit,
    onReport: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Text(
            text = "Playback speed",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        Row(
            modifier = Modifier.padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (option in PlaybackSpeeds) {
                FilterChip(
                    selected = option == speed,
                    onClick = { onSpeedChange(option) },
                    label = { Text(speedLabel(option)) },
                )
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        if (isOwnVideo) {
            OptionRow(
                icon = Icons.Outlined.Delete,
                label = "Delete video",
                color = MaterialTheme.colorScheme.error,
                onClick = onDelete,
            )
        } else {
            OptionRow(icon = Icons.Outlined.HeartBroken, label = "Not interested", onClick = onNotInterested)
        }
        OptionRow(icon = Icons.Outlined.Flag, label = "Report", onClick = onReport)
    }
}

@Composable
private fun OptionRow(
    icon: ImageVector,
    label: String,
    color: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(text = label, color = color) },
        leadingContent = { Icon(imageVector = icon, contentDescription = null, tint = color) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun DeleteVideoDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete this video?") },
        text = {
            Text(
                text = "It will be removed from your profile and from everyone's feeds. This can't be undone.",
                color = LoopColors.TextSecondary,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = "Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private fun speedLabel(speed: Float): String =
    if (speed % 1f == 0f) "${speed.toInt()}x" else "${speed}x"
