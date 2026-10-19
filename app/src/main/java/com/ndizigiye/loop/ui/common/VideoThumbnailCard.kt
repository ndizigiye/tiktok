package com.ndizigiye.loop.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.ndizigiye.loop.api.VideoDto
import com.ndizigiye.loop.common.formatCount
import com.ndizigiye.loop.ui.theme.LoopColors

private val BottomShade = Brush.verticalGradient(listOf(Color.Transparent, Color(0x99000000)))

/** A 9:16 video thumbnail for grids, with the view count in the bottom-left corner. */
@Composable
fun VideoThumbnailCard(
    video: VideoDto,
    modifier: Modifier = Modifier,
    showViews: Boolean = true,
    onClick: () -> Unit,
) {
    val thumbnailUrl = rememberResolvedUrl(video.thumbnailUrl)
    Box(
        modifier = modifier
            .aspectRatio(9f / 16f)
            .background(LoopColors.Placeholder)
            .clickable(onClick = onClick),
    ) {
        if (thumbnailUrl != null) {
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = video.caption.ifBlank { null },
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.Videocam,
                contentDescription = null,
                tint = LoopColors.TextMuted,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(28.dp),
            )
        }
        if (showViews) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(BottomShade)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.PlayArrow,
                    contentDescription = null,
                    tint = LoopColors.OverlayText,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = formatCount(video.viewCount),
                    style = MaterialTheme.typography.labelMedium,
                    color = LoopColors.OverlayText,
                )
            }
        }
    }
}
