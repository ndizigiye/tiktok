package com.ndizigiye.loop.ui.create

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ndizigiye.loop.common.formatDuration
import com.ndizigiye.loop.ui.theme.LoopColors
import com.ndizigiye.loop.ui.theme.LoopViolet

/**
 * Bottom of the recorder: recorded time above the record button, "Upload" on the
 * left before anything is recorded, and "Retake" plus the continue button on the
 * right while a take is paused or finished.
 */
@Composable
internal fun CaptureBottomBar(
    state: CameraUiState,
    recordEnabled: Boolean,
    onRecord: () -> Unit,
    onUpload: () -> Unit,
    onRetake: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val counting = state.countdown != null
    val showUpload = !state.hasTake && !counting
    val showFinish = state.phase == CapturePhase.FINALIZING || state.phase == CapturePhase.FINISHED ||
        (state.phase == CapturePhase.PAUSED && state.recordedMs > 0 && !counting)

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(modifier = Modifier.height(28.dp), contentAlignment = Alignment.Center) {
            if (state.hasTake) RecordingTime(recordedMs = state.recordedMs)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                if (showUpload) UploadButton(onClick = onUpload)
            }
            RecordButton(
                recording = state.phase == CapturePhase.RECORDING && !counting,
                enabled = recordEnabled,
                onClick = onRecord,
            )
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showFinish) {
                    LabelledIconButton(
                        icon = Icons.Filled.Replay,
                        label = "Retake",
                        onClick = onRetake,
                        enabled = state.phase == CapturePhase.PAUSED || state.phase == CapturePhase.FINISHED,
                    )
                    FinishButton(finalizing = state.phase == CapturePhase.FINALIZING, onClick = onFinish)
                }
            }
        }
    }
}

@Composable
private fun RecordingTime(recordedMs: Long, modifier: Modifier = Modifier) {
    Text(
        text = formatDuration(recordedMs),
        style = MaterialTheme.typography.labelLarge,
        color = LoopColors.OverlayText,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(LoopColors.OverlayScrim)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
private fun UploadButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .border(width = 2.dp, color = LoopColors.OverlayText, shape = RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.PhotoLibrary,
                contentDescription = null,
                tint = LoopColors.OverlayText,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(text = "Upload", style = MaterialTheme.typography.labelSmall, color = LoopColors.OverlayText)
    }
}

/** Violet check that finishes the take; shows progress while the file is finalised. */
@Composable
private fun FinishButton(finalizing: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(LoopViolet)
            .clickable(enabled = !finalizing, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (finalizing) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = LoopColors.OverlayText,
                strokeWidth = 2.dp,
            )
        } else {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "Next",
                tint = LoopColors.OverlayText,
            )
        }
    }
}
