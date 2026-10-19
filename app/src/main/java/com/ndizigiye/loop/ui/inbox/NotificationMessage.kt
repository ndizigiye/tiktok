package com.ndizigiye.loop.ui.inbox

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.ndizigiye.loop.api.NotificationDto
import com.ndizigiye.loop.api.NotificationType

private val WhitespaceRun = Regex("\\s+")

/** "**Ada Lovelace** commented: nice!" — the actor's name in bold, then what they did. */
internal fun notificationMessage(notification: NotificationDto): AnnotatedString {
    val actor = notification.actor
    return buildAnnotatedString {
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
            append(actor.displayName.ifBlank { actor.username })
        }
        append(' ')
        append(actionText(notification))
    }
}

private fun actionText(notification: NotificationDto): String {
    val quote = notification.text?.replace(WhitespaceRun, " ")?.trim()?.takeIf { it.isNotEmpty() }

    fun quoted(action: String): String = if (quote != null) "$action: $quote" else action

    return when (notification.type) {
        NotificationType.LIKE -> "liked your video"
        NotificationType.COMMENT -> quoted("commented")
        NotificationType.REPLY -> quoted("replied to your comment")
        NotificationType.COMMENT_LIKE -> quoted("liked your comment")
        NotificationType.FOLLOW -> "started following you"
        NotificationType.MENTION -> quoted("mentioned you")
    }
}
