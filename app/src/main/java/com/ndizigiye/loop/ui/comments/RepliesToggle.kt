package com.ndizigiye.loop.ui.comments

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ndizigiye.loop.common.formatCount
import com.ndizigiye.loop.ui.theme.LoopColors

/**
 * The row under a comment that has replies: "View N replies" while collapsed;
 * "View more" (or a retry after an error) and "Hide" while expanded.
 */
@Composable
internal fun RepliesToggle(
    replyCount: Long,
    thread: ReplyThread?,
    onShow: () -> Unit,
    onLoadMore: () -> Unit,
    onHide: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = ReplyIndent, end = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .width(20.dp)
                .height(1.dp)
                .background(LoopColors.Divider),
        )
        if (thread == null || !thread.expanded) {
            val label = if (replyCount == 1L) "View 1 reply" else "View ${formatCount(replyCount)} replies"
            ToggleAction(text = label, icon = Icons.Rounded.ExpandMore, onClick = onShow)
        } else {
            when {
                thread.isLoading -> CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.dp,
                )
                thread.error != null -> ToggleAction(text = "Couldn't load replies. Retry", icon = null, onClick = onLoadMore)
                thread.hasMore -> ToggleAction(text = "View more", icon = Icons.Rounded.ExpandMore, onClick = onLoadMore)
            }
            ToggleAction(text = "Hide", icon = Icons.Rounded.ExpandLess, onClick = onHide)
        }
    }
}

@Composable
private fun ToggleAction(text: String, icon: ImageVector?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = LoopColors.TextSecondary,
        )
        if (icon != null) {
            Spacer(modifier = Modifier.width(2.dp))
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = LoopColors.TextSecondary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
