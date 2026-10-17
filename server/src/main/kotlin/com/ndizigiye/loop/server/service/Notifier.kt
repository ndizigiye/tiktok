package com.ndizigiye.loop.server.service

import com.ndizigiye.loop.api.NotificationType
import com.ndizigiye.loop.server.db.Notifications
import com.ndizigiye.loop.server.db.Users
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll

/** Writes and removes inbox entries as part of the caller's transaction. */
object Notifier {
    /** Stored form of a [NotificationType], equal to its JSON name. */
    val NotificationType.wireName: String
        get() = when (this) {
            NotificationType.LIKE -> "like"
            NotificationType.COMMENT -> "comment"
            NotificationType.REPLY -> "reply"
            NotificationType.COMMENT_LIKE -> "comment_like"
            NotificationType.FOLLOW -> "follow"
            NotificationType.MENTION -> "mention"
        }

    fun typeOf(wireName: String): NotificationType? = NotificationType.entries.firstOrNull { it.wireName == wireName }

    /** Adds a notification for [recipient] unless they are the [actor]. */
    fun notify(
        recipient: Long,
        actor: Long,
        type: NotificationType,
        videoId: Long? = null,
        commentId: Long? = null,
        text: String? = null,
        now: Long = System.currentTimeMillis(),
    ) {
        if (recipient == actor) return
        Notifications.insert {
            it[recipientId] = recipient
            it[actorId] = actor
            it[Notifications.type] = type.wireName
            it[Notifications.videoId] = videoId
            it[Notifications.commentId] = commentId
            it[Notifications.text] = text?.take(MAX_TEXT)
            it[read] = false
            it[createdAt] = now
        }
    }

    /** Removes the notification [actor]'s action of [type] created, matched on the given target. */
    fun retract(type: NotificationType, actor: Long, recipient: Long? = null, videoId: Long? = null, commentId: Long? = null) {
        Notifications.deleteWhere {
            listOfNotNull(
                Notifications.type eq type.wireName,
                actorId eq actor,
                recipient?.let { recipientId eq it },
                videoId?.let { Notifications.videoId eq it },
                commentId?.let { Notifications.commentId eq it },
            ).reduce<Op<Boolean>, Op<Boolean>> { a, b -> a and b }
        }
    }

    /** Ids of existing users among the lowercase [usernames], excluding [except]. */
    fun existingUsers(usernames: Collection<String>, except: Long): List<Long> {
        if (usernames.isEmpty()) return emptyList()
        return Users.selectAll().where { Users.username inList usernames }
            .map { it[Users.id].value }
            .filter { it != except }
    }

    private const val MAX_TEXT = 400
}
