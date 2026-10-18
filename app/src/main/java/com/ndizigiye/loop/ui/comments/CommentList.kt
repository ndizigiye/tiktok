package com.ndizigiye.loop.ui.comments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ndizigiye.loop.api.CommentDto
import com.ndizigiye.loop.ui.theme.LoopColors
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

private const val LOAD_MORE_THRESHOLD = 3

/**
 * Top-level [comments] (distinct ids) with their expanded reply [threads].
 * Calls [onLoadMore] when the user scrolls near the end, and again each time
 * the list grows while still near the end.
 */
@Composable
internal fun CommentList(
    comments: List<CommentDto>,
    threads: Map<Long, ReplyThread>,
    now: Long,
    isLoadingMore: Boolean,
    loadMoreError: String?,
    listState: LazyListState,
    canDelete: (CommentDto) -> Boolean,
    onLoadMore: () -> Unit,
    onRetryLoadMore: () -> Unit,
    onOpenProfile: (username: String) -> Unit,
    onHashtag: (name: String) -> Unit,
    onReply: (CommentDto) -> Unit,
    onToggleLike: (CommentDto) -> Unit,
    onRequestDelete: (CommentDto) -> Unit,
    onShowReplies: (parentId: Long) -> Unit,
    onLoadMoreReplies: (parentId: Long) -> Unit,
    onHideReplies: (parentId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    @Composable
    fun Comment(comment: CommentDto, isReply: Boolean) {
        CommentItem(
            comment = comment,
            now = now,
            isReply = isReply,
            onOpenProfile = onOpenProfile,
            onHashtag = onHashtag,
            onReply = { onReply(comment) },
            onToggleLike = { onToggleLike(comment) },
            onRequestDelete = if (canDelete(comment)) ({ onRequestDelete(comment) }) else null,
        )
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        for (comment in comments) {
            val thread = threads[comment.id]
            item(key = "comment:${comment.id}", contentType = "comment") {
                Comment(comment, isReply = false)
            }
            if (thread != null && thread.expanded) {
                items(
                    items = thread.replies,
                    key = { reply -> "reply:${reply.id}" },
                    contentType = { "reply" },
                ) { reply ->
                    Comment(reply, isReply = true)
                }
            }
            val shownReplies = thread?.replies?.size?.toLong() ?: 0L
            if (comment.replyCount > 0 || shownReplies > 0) {
                item(key = "replies:${comment.id}", contentType = "replies-toggle") {
                    RepliesToggle(
                        replyCount = maxOf(comment.replyCount, shownReplies),
                        thread = thread,
                        onShow = { onShowReplies(comment.id) },
                        onLoadMore = { onLoadMoreReplies(comment.id) },
                        onHide = { onHideReplies(comment.id) },
                    )
                }
            }
        }
        if (isLoadingMore) {
            item(key = "footer:loading", contentType = "footer") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp,
                    )
                }
            }
        } else if (loadMoreError != null) {
            item(key = "footer:error", contentType = "footer") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = loadMoreError,
                        style = MaterialTheme.typography.bodySmall,
                        color = LoopColors.TextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onRetryLoadMore) { Text("Try again") }
                }
            }
        }
    }

    val currentOnLoadMore by rememberUpdatedState(onLoadMore)
    LaunchedEffect(listState) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            val total = layoutInfo.totalItemsCount
            if (total > 0 && lastVisible >= total - LOAD_MORE_THRESHOLD) total else -1
        }
            .distinctUntilChanged()
            .filter { it >= 0 }
            .collect { currentOnLoadMore() }
    }
}
