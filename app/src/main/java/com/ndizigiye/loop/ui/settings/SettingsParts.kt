package com.ndizigiye.loop.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ndizigiye.loop.BuildConfig
import com.ndizigiye.loop.ui.theme.LoopColors

@Composable
internal fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
    )
}

@Composable
internal fun settingsListItemColors(): ListItemColors =
    ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background)

/** "Clear video cache" with the space currently used. */
@Composable
internal fun StorageSection(storage: StorageState, onClear: () -> Unit, modifier: Modifier = Modifier) {
    val usedBytes = storage.usedBytes
    ListItem(
        headlineContent = { Text("Clear video cache") },
        supportingContent = {
            Text(
                if (usedBytes != null) {
                    "Videos you've watched use ${formatBytes(usedBytes)}"
                } else {
                    "Videos you've watched are kept for faster replays"
                },
            )
        },
        leadingContent = { Icon(imageVector = Icons.Outlined.DeleteSweep, contentDescription = null) },
        trailingContent = if (storage.clearing) {
            @Composable {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.dp,
                )
            }
        } else {
            null
        },
        modifier = modifier.clickable(enabled = !storage.clearing, onClick = onClear),
        colors = settingsListItemColors(),
    )
}

@Composable
internal fun AboutSection(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Text(
            text = "Loop ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "An original open-source short-video app built from scratch with Jetpack Compose, Media3 and CameraX.",
            style = MaterialTheme.typography.bodyMedium,
            color = LoopColors.TextSecondary,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
