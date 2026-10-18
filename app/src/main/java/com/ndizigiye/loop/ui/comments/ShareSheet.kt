package com.ndizigiye.loop.ui.comments

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.ndizigiye.loop.api.VideoDto
import com.ndizigiye.loop.ui.common.LocalSnackbarHostState
import com.ndizigiye.loop.ui.common.rememberResolvedUrl
import com.ndizigiye.loop.ui.navigation.LocalAppContainer
import com.ndizigiye.loop.ui.theme.LoopColors
import kotlinx.coroutines.launch

/**
 * "Share to" sheet for [video]: copy its link, send it through another app, or
 * save the clip. Each completed action counts as a share on the server.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareSheet(video: VideoDto, onDismiss: () -> Unit) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val snackbarHostState = LocalSnackbarHostState.current
    // The destination's scope outlives the sheet, so the share count and the
    // snackbar (shown by the app once the sheet is gone) are not cut short.
    val hostScope = LocalLifecycleOwner.current.lifecycleScope
    val baseUrl by container.sessionStore.baseUrl.collectAsStateWithLifecycle()
    val videoUrl = rememberResolvedUrl(video.videoUrl)
    val link = shareLinkOf(baseUrl, video.id)

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sheetScope = rememberCoroutineScope()
    val close: () -> Unit = {
        sheetScope.launch { sheetState.hide() }.invokeOnCompletion {
            if (!sheetState.isVisible) onDismiss()
        }
    }
    val announce: (String) -> Unit = { message ->
        hostScope.launch { snackbarHostState.showSnackbar(message) }
    }
    val completed: (message: String?) -> Unit = { message ->
        hostScope.launch { container.videoRepository.share(video) }
        if (message != null) announce(message)
        close()
    }
    val failed: (message: String) -> Unit = { message ->
        announce(message)
        close()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Share to",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                ShareActionButton(
                    icon = Icons.Rounded.Link,
                    label = "Copy link",
                    background = MaterialTheme.colorScheme.surfaceContainerHighest,
                    onClick = {
                        clipboard.setText(AnnotatedString(link))
                        completed("Link copied")
                    },
                )
                ShareActionButton(
                    icon = Icons.Rounded.Share,
                    label = "Share",
                    background = LoopColors.Accent,
                    onClick = {
                        if (context.launchShareChooser(video.caption, link)) {
                            completed(null)
                        } else {
                            failed("No app on this device can share it.")
                        }
                    },
                )
                ShareActionButton(
                    icon = Icons.Rounded.Download,
                    label = "Save video",
                    background = MaterialTheme.colorScheme.surfaceContainerHighest,
                    onClick = {
                        val url = videoUrl
                        if (url != null && context.enqueueVideoDownload(url, video.id)) {
                            completed("Downloading...")
                        } else {
                            failed("Couldn't start the download.")
                        }
                    },
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider(thickness = 0.5.dp, color = LoopColors.Divider)
            TextButton(
                onClick = close,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text(text = "Cancel", color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun ShareActionButton(
    icon: ImageVector,
    label: String,
    background: Color,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(72.dp)
            .clickable(role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(background),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = LoopColors.TextSecondary,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}
