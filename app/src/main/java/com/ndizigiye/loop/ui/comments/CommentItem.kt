package com.ndizigiye.loop.ui.comments

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ndizigiye.loop.api.CommentDto
import com.ndizigiye.loop.common.formatCount
import com.ndizigiye.loop.common.formatRelativeTime
import com.ndizigiye.loop.ui.common.Avatar
import com.ndizigiye.loop.ui.common.CaptionText
import com.ndizigiye.loop.ui.theme.LoopColors

private val CommentAvatarSize = 32.dp
private val ReplyAvatarSize = 24.dp
private val CommentStartPadding = 16.dp
private val CommentAvatarGap = 12.dp

/** Start offset of replies and of the replies toggle: aligned with the text of the parent comment. */
internal val ReplyIndent = CommentStartPadding + CommentAvatarSize + CommentAvatarGap

/**
 * One comment, or an indented reply when [isReply]. Tapping the row or "Reply"
 * calls [onReply]; a long press calls [onRequestDelete] when it is not null.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CommentItem(
    comment: CommentDto,
    now: Long,
    isReply: Boolean,
    onOpenProfile: (username: String) -> Unit,
    onHashtag: (name: String) -> Unit,
    onReply: () -> Unit,
    onToggleLike: () -> Unit,
    onRequestDelete: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val onLongClick: (() -> Unit)? = onRequestDelete?.let { request ->
        {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            request()
        }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClickLabel = "Reply",
                onLongClickLabel = if (onLongClick != null) "Delete comment" else null,
                onLongClick = onLongClick,
                onClick = onReply,
            )
            .padding(
                start = if (isReply) ReplyIndent else CommentStartPadding,
                end = 4.dp,
                top = 8.dp,
                bottom = 8.dp,
            ),
        verticalAlignment = Alignment.Top,
    ) {
        Avatar(
            user = comment.author,
            size = if (isReply) ReplyAvatarSize else CommentAvatarSize,
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClickLabel = "Open profile") { onOpenProfile(comment.author.username) },
        )
        Spacer(modifier = Modifier.width(if (isReply) 8.dp else CommentAvatarGap))
        Column(modifier = Modifier.weight(1f)) {
            AuthorLine(comment)
            Spacer(modifier = Modifier.height(2.dp))
            CaptionText(
                text = comment.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                onHashtag = onHashtag,
                onMention = onOpenProfile,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatRelativeTime(comment.createdAt, now),
                    fontSize = 12.sp,
                    color = LoopColors.TextMuted,
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Reply",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = LoopColors.TextSecondary,
                    modifier = Modifier.clickable(role = Role.Button, onClick = onReply),
                )
            }
        }
        LikeToggle(liked = comment.likedByMe, count = comment.likeCount, onToggle = onToggleLike)
    }
}

@Composable
private fun AuthorLine(comment: CommentDto) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = comment.author.displayName.ifBlank { comment.author.username },
            fontSize = 13.sp,
            color = LoopColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (comment.byCreator) {
            Spacer(modifier = Modifier.width(6.dp))
            CreatorLabel()
        }
    }
}

@Composable
private fun CreatorLabel() {
    Text(
        text = "Creator",
        style = MaterialTheme.typography.labelSmall,
        color = LoopColors.AccentLight,
        maxLines = 1,
        modifier = Modifier
            .background(LoopColors.Accent.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

@Composable
private fun LikeToggle(liked: Boolean, count: Long, onToggle: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .toggleable(value = liked, role = Role.Checkbox, onValueChange = { onToggle() })
            .widthIn(min = 40.dp)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = if (liked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            contentDescription = "Like",
            tint = if (liked) LoopColors.Like else LoopColors.TextMuted,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = if (count > 0) formatCount(count) else "",
            fontSize = 11.sp,
            color = LoopColors.TextMuted,
            maxLines = 1,
        )
    }
}
