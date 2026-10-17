package com.ndizigiye.loop.data

import com.ndizigiye.loop.api.CommentDto
import com.ndizigiye.loop.api.CreateCommentRequest
import com.ndizigiye.loop.api.PageDto
import com.ndizigiye.loop.api.ToggleResultDto
import com.ndizigiye.loop.api.VideoDto
import com.ndizigiye.loop.data.remote.LoopApi
import com.ndizigiye.loop.data.remote.apiCall

class CommentRepository(
    private val api: LoopApi,
    private val interactions: InteractionStore,
) {
    /** Top-level comments, most liked first. */
    suspend fun comments(videoId: Long, cursor: String?): Result<PageDto<CommentDto>> =
        apiCall { api.comments(videoId, cursor) }
            .onSuccess { page -> interactions.rememberUsers(page.items.map { it.author }) }

    /** Replies to a top-level comment, oldest first. */
    suspend fun replies(commentId: Long, cursor: String?): Result<PageDto<CommentDto>> =
        apiCall { api.replies(commentId, cursor) }
            .onSuccess { page -> interactions.rememberUsers(page.items.map { it.author }) }

    /** Posts a comment (or a reply when [parentId] is set) and bumps the video's comment count. */
    suspend fun post(video: VideoDto, text: String, parentId: Long?): Result<CommentDto> =
        apiCall { api.postComment(video.id, CreateCommentRequest(text.trim(), parentId)) }
            .onSuccess { interactions.update(video) { it.copy(commentCount = it.commentCount + 1) } }

    suspend fun setLiked(commentId: Long, liked: Boolean): Result<ToggleResultDto> =
        apiCall { if (liked) api.likeComment(commentId) else api.unlikeComment(commentId) }

    /** Deletes [comment] (and its replies) and lowers the video's comment count accordingly. */
    suspend fun delete(video: VideoDto, comment: CommentDto): Result<Unit> =
        apiCall { api.deleteComment(comment.id) }
            .onSuccess {
                interactions.update(video) {
                    it.copy(commentCount = (it.commentCount - 1 - comment.replyCount).coerceAtLeast(0))
                }
            }
}
