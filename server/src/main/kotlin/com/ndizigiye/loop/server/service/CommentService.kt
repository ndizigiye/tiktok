package com.ndizigiye.loop.server.service

import com.ndizigiye.loop.api.ApiLimits
import com.ndizigiye.loop.api.CommentDto
import com.ndizigiye.loop.api.CreateCommentRequest
import com.ndizigiye.loop.api.NotificationType
import com.ndizigiye.loop.api.PageDto
import com.ndizigiye.loop.api.ToggleResultDto
import com.ndizigiye.loop.common.CaptionParser
import com.ndizigiye.loop.server.ApiException
import com.ndizigiye.loop.server.db.CommentLikes
import com.ndizigiye.loop.server.db.Comments
import com.ndizigiye.loop.server.db.LoopDatabase
import com.ndizigiye.loop.server.paging.KeysetOrder
import com.ndizigiye.loop.server.paging.PageRequest
import com.ndizigiye.loop.server.paging.offsetIds
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll

class CommentService(private val db: LoopDatabase) {
    private val oldestFirst = KeysetOrder(Comments.createdAt, Comments.id, descending = false)

    /** Top-level comments, most liked first, then newest. */
    suspend fun topLevel(videoId: Long, viewerId: Long?, page: PageRequest): PageDto<CommentDto> = db.tx {
        authorOf(videoId)
        val likes = CommentLikes.userId.count()
        val ids = Comments.join(CommentLikes, JoinType.LEFT, Comments.id, CommentLikes.commentId)
            .select(Comments.id, likes)
            .where { (Comments.videoId eq videoId) and Comments.parentId.isNull() }
            .groupBy(Comments.id, Comments.createdAt)
            .orderBy(likes to SortOrder.DESC, Comments.createdAt to SortOrder.DESC, Comments.id to SortOrder.DESC)
            .offsetIds(Comments.id, page)
        PageDto(DtoAssembler.comments(ids.rows, viewerId), ids.nextCursor)
    }

    /** Replies to a top-level comment, oldest first. */
    suspend fun replies(commentId: Long, viewerId: Long?, page: PageRequest): PageDto<CommentDto> = db.tx {
        requireComment(commentId)
        val ids = oldestFirst.ids(Comments, Comments.parentId eq commentId, page)
        PageDto(DtoAssembler.comments(ids.rows, viewerId), ids.nextCursor)
    }

    suspend fun create(
        userId: Long,
        videoId: Long,
        request: CreateCommentRequest,
        now: Long = System.currentTimeMillis(),
    ): CommentDto = db.tx {
        val id = insertComment(userId, videoId, request, now)
        DtoAssembler.comments(listOf(id), userId).single()
    }

    /**
     * Inserts a comment and notifies, at most once each and never the actor: the author of
     * the comment being answered (`reply`), the video author (`comment`) and mentioned users
     * (`mention`). Call inside a transaction.
     */
    internal fun insertComment(userId: Long, videoId: Long, request: CreateCommentRequest, now: Long): Long {
        val text = request.text.trim()
        if (text.isEmpty() || text.length > ApiLimits.COMMENT_MAX) {
            throw ApiException.validation("Comments are 1-${ApiLimits.COMMENT_MAX} characters.")
        }
        val videoAuthor = authorOf(videoId)
        val answered = request.parentId?.let { parentId ->
            requireComment(parentId).also {
                if (it[Comments.videoId].value != videoId) {
                    throw ApiException.validation("You can only reply to comments on the same video.")
                }
            }
        }
        val id = Comments.insertAndGetId {
            it[Comments.videoId] = videoId
            it[authorId] = userId
            it[parentId] = answered?.let { parent -> parent[Comments.parentId]?.value ?: parent[Comments.id].value }
            it[Comments.text] = text
            it[createdAt] = now
        }.value

        val recipients = LinkedHashMap<Long, NotificationType>()
        answered?.let { recipients.putIfAbsent(it[Comments.authorId].value, NotificationType.REPLY) }
        recipients.putIfAbsent(videoAuthor, NotificationType.COMMENT)
        Notifier.existingUsers(CaptionParser.mentions(text), except = userId)
            .forEach { recipients.putIfAbsent(it, NotificationType.MENTION) }
        recipients.forEach { (recipient, type) ->
            Notifier.notify(recipient, userId, type, videoId = videoId, commentId = id, text = text, now = now)
        }
        return id
    }

    /** Allowed for the comment author and the video author; replies of a top-level comment go with it. */
    suspend fun delete(userId: Long, commentId: Long) {
        db.tx {
            val comment = requireComment(commentId)
            val videoAuthor = authorOf(comment[Comments.videoId].value)
            if (userId != comment[Comments.authorId].value && userId != videoAuthor) {
                throw ApiException.forbidden("You can only delete your own comments or comments on your videos.")
            }
            Comments.deleteWhere { Comments.id eq commentId }
        }
    }

    suspend fun setLiked(userId: Long, commentId: Long, liked: Boolean): ToggleResultDto = db.tx {
        val comment = requireComment(commentId)
        val changed = toggle(Relation.commentLikes, userId, commentId, liked)
        when {
            !changed -> Unit
            liked -> Notifier.notify(
                recipient = comment[Comments.authorId].value,
                actor = userId,
                type = NotificationType.COMMENT_LIKE,
                videoId = comment[Comments.videoId].value,
                commentId = commentId,
                text = comment[Comments.text],
            )
            else -> Notifier.retract(NotificationType.COMMENT_LIKE, actor = userId, commentId = commentId)
        }
        ToggleResultDto(liked, CommentLikes.selectAll().where { CommentLikes.commentId eq commentId }.count())
    }

    private fun requireComment(commentId: Long): ResultRow =
        Comments.selectAll().where { Comments.id eq commentId }.firstOrNull()
            ?: throw ApiException.notFound("Comment not found.")
}
