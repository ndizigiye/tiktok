package com.ndizigiye.loop.ui.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.ndizigiye.loop.api.NotificationDto
import com.ndizigiye.loop.api.NotificationType
import com.ndizigiye.loop.api.VideoSummaryDto
import com.ndizigiye.loop.common.formatRelativeTime
import com.ndizigiye.loop.ui.common.Avatar
import com.ndizigiye.loop.ui.common.FollowButton
import com.ndizigiye.loop.ui.common.rememberResolvedUrl
import com.ndizigiye.loop.ui.theme.LoopColors

/**
 * One notification: the actor's avatar (opens their profile), what they did and
 * when, and a follow button or the video's thumbnail. Tapping the row opens the
 * video, or the actor's profile for a new follower. Rows that were unread when
 * loaded get a raised background and a violet dot.
 */
@Composable
internal fun NotificationRow(
    notification: NotificationDto,
    nowMillis: Long,
    onOpenProfile: (username: String) -> Unit,
    onOpenVideo: (videoId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val actor = notification.actor
    val video = notification.video
    val unread = !notification.read
    val message = remember(notification) { notificationMessage(notification) }
    val openTarget: () -> Unit = {
        if (notification.type == NotificationType.FOLLOW || video == null) {
            onOpenProfile(actor.username)
        } else {
            onOpenVideo(video.id)
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(if (unread) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
            .clickable(onClick = openTarget)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(
            user = actor,
            size = 48.dp,
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClickLabel = "Open profile") { onOpenProfile(actor.username) },
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatRelativeTime(notification.createdAt, nowMillis),
                    style = MaterialTheme.typography.bodySmall,
                    color = LoopColors.TextMuted,
                )
                if (unread) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(LoopColors.Accent),
                    )
                }
            }
        }
        when (notification.type) {
            NotificationType.FOLLOW -> {
                Spacer(modifier = Modifier.width(12.dp))
                FollowButton(user = actor, compact = true)
            }
            NotificationType.LIKE,
            NotificationType.COMMENT,
            NotificationType.REPLY,
            NotificationType.COMMENT_LIKE,
            NotificationType.MENTION,
            -> if (video != null) {
                Spacer(modifier = Modifier.width(12.dp))
                VideoThumbnail(video = video, onClick = { onOpenVideo(video.id) })
            }
        }
    }
}

@Composable
private fun VideoThumbnail(video: VideoSummaryDto, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val thumbnailUrl = rememberResolvedUrl(video.thumbnailUrl)
    Box(
        modifier = modifier
            .size(width = 44.dp, height = 60.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(LoopColors.Placeholder)
            .clickable(onClickLabel = "Open video", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (thumbnailUrl != null) {
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = "Video",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.Videocam,
                contentDescription = "Video",
                tint = LoopColors.TextMuted,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
