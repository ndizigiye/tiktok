package com.ndizigiye.loop.ui.comments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ndizigiye.loop.api.CommentDto
import com.ndizigiye.loop.api.PageDto
import com.ndizigiye.loop.api.VideoDto
import com.ndizigiye.loop.data.CommentRepository
import com.ndizigiye.loop.data.PagedState
import com.ndizigiye.loop.data.Paginator
import com.ndizigiye.loop.data.remote.userMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus

/** One-off outcomes for an open comments sheet; dropped while no sheet is collecting. */
sealed interface CommentsEvent {
    data class Posted(val comment: CommentDto) : CommentsEvent

    data class Deleted(val comment: CommentDto) : CommentsEvent

    data class Message(val text: String) : CommentsEvent
}

/**
 * Comments of one video while its sheet is open: paginated top-level comments, reply
 * threads loaded on demand, optimistic comment likes, posting and deleting. Posts,
 * deletions and likes run in [applicationScope] (on the main thread), so they finish
 * even when the sheet is closed meanwhile; loading stops with the sheet.
 */
class CommentsViewModel(
    private val video: VideoDto,
    private val repository: CommentRepository,
    applicationScope: CoroutineScope,
) : ViewModel() {

    private val requestScope = applicationScope + Dispatchers.Main.immediate

    private var firstLoad = true

    private val paginator = Paginator(viewModelScope) { cursor -> loadComments(cursor) }

    /** Top-level comments. */
    val comments: StateFlow<PagedState<CommentDto>> = paginator.state

    private val _threads = MutableStateFlow<Map<Long, ReplyThread>>(emptyMap())

    /** Reply threads by top-level comment id. */
    val threads: StateFlow<Map<Long, ReplyThread>> = _threads.asStateFlow()

    /** Parent of the reply requests, so [reload] can drop the ones in flight. */
    private val replyLoads = SupervisorJob(viewModelScope.coroutineContext.job)

    private val _isPosting = MutableStateFlow(false)
    val isPosting: StateFlow<Boolean> = _isPosting.asStateFlow()

    private val _events = MutableSharedFlow<CommentsEvent>(extraBufferCapacity = EVENT_BUFFER)
    val events: SharedFlow<CommentsEvent> = _events.asSharedFlow()

    private val likesInFlight = mutableSetOf<Long>()
    private val deletesInFlight = mutableSetOf<Long>()

    /**
     * Loads the first page, or refreshes the list once a page has loaded. A refresh also
     * collapses every thread, so replies are fetched afresh when expanded again.
     */
    fun reload() {
        if (!paginator.state.value.loadedOnce) {
            paginator.loadMore()
            return
        }
        replyLoads.cancelChildren()
        _threads.value = emptyMap()
        paginator.refresh()
    }

    fun loadMore() = paginator.loadMore()

    /** Retries after a failure below the list: the next page, or a refresh once the list was complete. */
    fun retryLoadMore() {
        if (paginator.state.value.endReached) paginator.refresh() else paginator.loadMore()
    }

    fun showReplies(parentId: Long) {
        val thread = _threads.value[parentId] ?: ReplyThread()
        _threads.update { it + (parentId to thread.copy(expanded = true)) }
        if (!thread.loadedOnce) loadReplies(parentId)
    }

    fun hideReplies(parentId: Long) = updateThread(parentId) { it.copy(expanded = false) }

    /** Loads the next page of replies, or retries the first one after an error. */
    fun loadReplies(parentId: Long) {
        val thread = _threads.value[parentId] ?: return
        if (thread.isLoading) return
        val cursor = if (thread.loadedOnce) thread.nextCursor ?: return else null
        updateThread(parentId) { it.copy(isLoading = true, error = null) }
        viewModelScope.launch(replyLoads) {
            repository.replies(parentId, cursor)
                .onSuccess { page -> updateThread(parentId) { it.withPage(page) } }
                .onFailure { error ->
                    updateThread(parentId) { it.copy(isLoading = false, error = error.userMessage()) }
                }
        }
    }

    /** Flips the like on [comment] at once and confirms or reverts it with the server's answer. */
    fun toggleLike(comment: CommentDto) {
        val before = find(comment.id) ?: comment
        if (!likesInFlight.add(before.id)) return
        val liked = !before.likedByMe
        val optimisticCount = (before.likeCount + if (liked) 1 else -1).coerceAtLeast(0)
        updateComment(before.id) { it.copy(likedByMe = liked, likeCount = optimisticCount) }
        requestScope.launch {
            try {
                repository.setLiked(before.id, liked)
                    .onSuccess { result ->
                        updateComment(before.id) { it.copy(likedByMe = result.active, likeCount = result.count) }
                    }
                    .onFailure { error ->
                        updateComment(before.id) { it.copy(likedByMe = before.likedByMe, likeCount = before.likeCount) }
                        _events.tryEmit(CommentsEvent.Message(error.userMessage()))
                    }
            } finally {
                likesInFlight.remove(before.id)
            }
        }
    }

    /** Posts [text] as a top-level comment, or as a reply when [replyTo] is set. */
    fun post(text: String, replyTo: CommentDto?) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _isPosting.value) return
        _isPosting.value = true
        requestScope.launch {
            try {
                repository.post(video, trimmed, replyTo?.id)
                    .onSuccess { comment ->
                        insertPosted(comment)
                        _events.tryEmit(CommentsEvent.Posted(comment))
                    }
                    .onFailure { error -> _events.tryEmit(CommentsEvent.Message(error.userMessage())) }
            } finally {
                _isPosting.value = false
            }
        }
    }

    /** Deletes [comment] (a top-level comment takes its replies with it). */
    fun delete(comment: CommentDto) {
        val current = find(comment.id) ?: comment
        if (!deletesInFlight.add(current.id)) return
        requestScope.launch {
            try {
                repository.delete(video, current)
                    .onSuccess {
                        removeLocally(current)
                        _events.tryEmit(CommentsEvent.Deleted(current))
                    }
                    .onFailure { error -> _events.tryEmit(CommentsEvent.Message(error.userMessage())) }
            } finally {
                deletesInFlight.remove(current.id)
            }
        }
    }

    /** The first load takes the page prefetched while the video played, when there is a fresh one. */
    private suspend fun loadComments(cursor: String?): Result<PageDto<CommentDto>> {
        val prefetched = if (cursor == null && firstLoad) repository.takePrefetchedFirstPage(video.id) else null
        firstLoad = false
        return prefetched?.let { Result.success(it) } ?: repository.comments(video.id, cursor)
    }

    private fun insertPosted(comment: CommentDto) {
        val parentId = comment.parentId
        if (parentId == null) {
            paginator.update { list -> listOf(comment) + list.filterNot { it.id == comment.id } }
            return
        }
        val parent = paginator.state.value.items.firstOrNull { it.id == parentId }
        // A parent without replies needs no request: the new reply is the whole thread.
        val thread = (_threads.value[parentId] ?: ReplyThread(loadedOnce = parent?.replyCount == 0L))
            .withPosted(comment)
            .copy(expanded = true)
        _threads.update { it + (parentId to thread) }
        paginator.update { list ->
            list.map { if (it.id == parentId) it.copy(replyCount = it.replyCount + 1) else it }
        }
        if (!thread.loadedOnce) loadReplies(parentId)
    }

    private fun removeLocally(comment: CommentDto) {
        val parentId = comment.parentId
        if (parentId == null) {
            paginator.update { list -> list.filterNot { it.id == comment.id } }
            _threads.update { it - comment.id }
        } else {
            updateThread(parentId) { it.without(comment.id) }
            paginator.update { list ->
                list.map {
                    if (it.id == parentId) it.copy(replyCount = (it.replyCount - 1).coerceAtLeast(0)) else it
                }
            }
        }
    }

    private fun find(id: Long): CommentDto? =
        paginator.state.value.items.firstOrNull { it.id == id }
            ?: _threads.value.values.firstNotNullOfOrNull { it.find(id) }

    /** Applies [transform] to the comment or reply with [id] wherever it is shown. */
    private fun updateComment(id: Long, transform: (CommentDto) -> CommentDto) {
        val apply: (CommentDto) -> CommentDto = { if (it.id == id) transform(it) else it }
        paginator.update { list -> list.map(apply) }
        _threads.update { threads -> threads.mapValues { (_, thread) -> thread.mapReplies(apply) } }
    }

    /** Updates an existing thread; a thread removed with its parent stays removed. */
    private fun updateThread(parentId: Long, transform: (ReplyThread) -> ReplyThread) {
        _threads.update { threads ->
            val thread = threads[parentId] ?: return@update threads
            threads + (parentId to transform(thread))
        }
    }

    private companion object {
        const val EVENT_BUFFER = 8
    }
}
