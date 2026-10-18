package com.ndizigiye.loop.data

import com.ndizigiye.loop.api.CommentDto
import com.ndizigiye.loop.api.CreateCommentRequest
import com.ndizigiye.loop.api.PageDto
import com.ndizigiye.loop.api.ToggleResultDto
import com.ndizigiye.loop.api.VideoDto
import com.ndizigiye.loop.data.remote.LoopApi
import com.ndizigiye.loop.data.remote.apiCall
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class CommentRepository(
    private val api: LoopApi,
    private val interactions: InteractionStore,
) {
    /** Prefetched first pages by video id, least recently used first. Guarded by itself. */
    private val prefetched = object : LinkedHashMap<Long, PrefetchedPage>(PREFETCH_CAPACITY, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, PrefetchedPage>?): Boolean =
            size > PREFETCH_CAPACITY
    }

    /** A token per prefetch in flight; invalidating a video drops its token so the stale page is not kept. */
    private val prefetchTokens = mutableMapOf<Long, Any>()

    /** Top-level comments, most liked first. */
    suspend fun comments(videoId: Long, cursor: String?): Result<PageDto<CommentDto>> =
        apiCall { api.comments(videoId, cursor) }
            .onSuccess { page -> interactions.rememberUsers(page.items.map { it.author }) }

    /**
     * Loads the first page of [videoId]'s comments ahead of the comments sheet and
     * keeps it briefly for [takePrefetchedFirstPage] (see docs/feature-analysis.md,
     * section 4). Does nothing while a fresh page is kept or a prefetch for it runs;
     * failures are ignored.
     */
    suspend fun prefetchFirstPage(videoId: Long) {
        val token = Any()
        synchronized(prefetched) {
            if (freshPage(videoId) != null || videoId in prefetchTokens) return
            prefetchTokens[videoId] = token
        }
        try {
            val page = comments(videoId, null).getOrNull() ?: return
            synchronized(prefetched) {
                if (prefetchTokens[videoId] === token) {
                    prefetched[videoId] = PrefetchedPage(page, TimeSource.Monotonic.markNow())
                }
            }
        } finally {
            synchronized(prefetched) {
                if (prefetchTokens[videoId] === token) prefetchTokens.remove(videoId)
            }
        }
    }

    /**
     * The first page of [videoId]'s comments from [prefetchFirstPage] if it is still
     * fresh, else null. It is handed out once: later loads go to the server.
     */
    fun takePrefetchedFirstPage(videoId: Long): PageDto<CommentDto>? = synchronized(prefetched) {
        freshPage(videoId)?.also { prefetched.remove(videoId) }
    }

    /** Replies to a top-level comment, oldest first. */
    suspend fun replies(commentId: Long, cursor: String?): Result<PageDto<CommentDto>> =
        apiCall { api.replies(commentId, cursor) }
            .onSuccess { page -> interactions.rememberUsers(page.items.map { it.author }) }

    /** Posts a comment (or a reply when [parentId] is set) and bumps the video's comment count. */
    suspend fun post(video: VideoDto, text: String, parentId: Long?): Result<CommentDto> =
        apiCall { api.postComment(video.id, CreateCommentRequest(text.trim(), parentId)) }
            .onSuccess {
                invalidatePrefetched(video.id)
                interactions.update(video) { it.copy(commentCount = it.commentCount + 1) }
            }

    suspend fun setLiked(commentId: Long, liked: Boolean): Result<ToggleResultDto> =
        apiCall { if (liked) api.likeComment(commentId) else api.unlikeComment(commentId) }

    /** Deletes [comment] (and its replies) and lowers the video's comment count accordingly. */
    suspend fun delete(video: VideoDto, comment: CommentDto): Result<Unit> =
        apiCall { api.deleteComment(comment.id) }
            .onSuccess {
                invalidatePrefetched(video.id)
                interactions.update(video) {
                    it.copy(commentCount = (it.commentCount - 1 - comment.replyCount).coerceAtLeast(0))
                }
            }

    /** Call while holding the lock on [prefetched]. */
    private fun freshPage(videoId: Long): PageDto<CommentDto>? {
        val entry = prefetched[videoId] ?: return null
        if (entry.fetchedAt.elapsedNow() < PREFETCH_FRESH_FOR) return entry.page
        prefetched.remove(videoId)
        return null
    }

    private fun invalidatePrefetched(videoId: Long) {
        synchronized(prefetched) {
            prefetched.remove(videoId)
            prefetchTokens.remove(videoId)
        }
    }

    private class PrefetchedPage(val page: PageDto<CommentDto>, val fetchedAt: TimeSource.Monotonic.ValueTimeMark)

    private companion object {
        const val PREFETCH_CAPACITY = 20
        val PREFETCH_FRESH_FOR = 60.seconds
    }
}
