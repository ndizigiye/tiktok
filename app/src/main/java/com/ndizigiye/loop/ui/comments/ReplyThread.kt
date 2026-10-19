package com.ndizigiye.loop.ui.comments

import com.ndizigiye.loop.api.CommentDto
import com.ndizigiye.loop.api.PageDto

/** The replies of one top-level comment as far as they have been loaded or posted here. */
data class ReplyThread(
    /** Replies from the server, oldest first, without duplicates. */
    val loaded: List<CommentDto> = emptyList(),
    /** Replies posted in this sheet that the loaded pages do not contain (yet). */
    val posted: List<CommentDto> = emptyList(),
    val nextCursor: String? = null,
    val loadedOnce: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val expanded: Boolean = false,
) {
    val replies: List<CommentDto> get() = loaded + posted

    /** True when another server page can be requested. */
    val hasMore: Boolean get() = loadedOnce && nextCursor != null

    fun find(id: Long): CommentDto? = loaded.firstOrNull { it.id == id } ?: posted.firstOrNull { it.id == id }

    fun withPage(page: PageDto<CommentDto>): ReplyThread {
        val merged = (if (loadedOnce) loaded + page.items else page.items).distinctBy { it.id }
        val mergedIds = merged.mapTo(HashSet()) { it.id }
        return copy(
            loaded = merged,
            posted = posted.filterNot { it.id in mergedIds },
            nextCursor = page.nextCursor,
            loadedOnce = true,
            isLoading = false,
            error = null,
        )
    }

    fun withPosted(reply: CommentDto): ReplyThread =
        if (find(reply.id) != null) this else copy(posted = posted + reply)

    fun mapReplies(transform: (CommentDto) -> CommentDto): ReplyThread =
        copy(loaded = loaded.map(transform), posted = posted.map(transform))

    fun without(id: Long): ReplyThread =
        copy(loaded = loaded.filterNot { it.id == id }, posted = posted.filterNot { it.id == id })
}
