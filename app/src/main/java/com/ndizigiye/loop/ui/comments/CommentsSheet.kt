package com.ndizigiye.loop.ui.comments

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ndizigiye.loop.api.CommentDto
import com.ndizigiye.loop.api.VideoDto
import com.ndizigiye.loop.common.formatCount
import com.ndizigiye.loop.ui.common.EmptyBox
import com.ndizigiye.loop.ui.common.ErrorBox
import com.ndizigiye.loop.ui.common.LoadingBox
import com.ndizigiye.loop.ui.common.LocalSnackbarHostState
import com.ndizigiye.loop.ui.common.rememberVideoStats
import com.ndizigiye.loop.ui.navigation.LocalAppContainer
import com.ndizigiye.loop.ui.navigation.LocalNavigator
import com.ndizigiye.loop.ui.theme.LoopColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val SHEET_HEIGHT_FRACTION = 0.72f
private const val CLOCK_TICK_MS = 60_000L
private val SheetShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)

/**
 * Comments of [video] in a modal bottom sheet: the list with replies, likes and
 * deletion, and a composer pinned at the bottom. The sheet opens fully expanded;
 * [onDismiss] is called once it should be removed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsSheet(video: VideoDto, onDismiss: () -> Unit) {
    val container = LocalAppContainer.current
    val viewModel: CommentsViewModel = viewModel(key = "comments-${video.id}") {
        CommentsViewModel(video, container.commentRepository)
    }
    LaunchedEffect(viewModel) { viewModel.reload() }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val close: () -> Unit = {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            if (!sheetState.isVisible) onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = SheetShape,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = null,
        // CommentInput pads for the keyboard and the navigation bar itself.
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        CommentsSheetContent(video = video, viewModel = viewModel, onClose = close, onLeave = onDismiss)
    }
}

/**
 * The sheet's content. Keyboard and focus locals are read here, inside the sheet's
 * own window, so they act on the comment field.
 */
@Composable
private fun CommentsSheetContent(
    video: VideoDto,
    viewModel: CommentsViewModel,
    onClose: () -> Unit,
    onLeave: () -> Unit,
) {
    val container = LocalAppContainer.current
    val navigator = LocalNavigator.current
    val snackbarHostState = LocalSnackbarHostState.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    val state by viewModel.comments.collectAsStateWithLifecycle()
    val threads by viewModel.threads.collectAsStateWithLifecycle()
    val isPosting by viewModel.isPosting.collectAsStateWithLifecycle()
    val session by container.sessionStore.session.collectAsStateWithLifecycle()
    val stats = rememberVideoStats(video)
    val me = session?.user

    var text by rememberSaveable { mutableStateOf("") }
    var replyTarget by remember { mutableStateOf<CommentDto?>(null) }
    var pendingDelete by remember { mutableStateOf<CommentDto?>(null) }
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    val now by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            delay(CLOCK_TICK_MS)
            value = System.currentTimeMillis()
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is CommentsEvent.Posted -> {
                    text = ""
                    replyTarget = null
                    keyboard?.hide()
                    focusManager.clearFocus()
                    if (event.comment.parentId == null) launch { listState.animateScrollToItem(0) }
                }
                is CommentsEvent.Message -> launch { snackbarHostState.showSnackbar(event.text) }
            }
        }
    }

    // Leaving the sheet for another screen (or the log-in flow) closes it, so it
    // does not stay on top of the destination.
    val requireAuth: () -> Boolean = {
        navigator.requireAuth().also { signedIn -> if (!signedIn) onLeave() }
    }
    val openProfile: (String) -> Unit = { username ->
        onLeave()
        navigator.openProfile(username)
    }
    val openHashtag: (String) -> Unit = { name ->
        onLeave()
        navigator.openHashtag(name)
    }
    val startReply: (CommentDto) -> Unit = { comment ->
        if (requireAuth() && session != null) {
            replyTarget = comment
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }
    val myId = me?.id
    val canDelete: (CommentDto) -> Boolean = { comment ->
        myId != null && (comment.author.id == myId || video.author.id == myId)
    }

    val sheetHeight = LocalConfiguration.current.screenHeightDp.dp * SHEET_HEIGHT_FRACTION
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(sheetHeight),
    ) {
        CommentsHeader(count = stats.commentCount, onClose = onClose)
        HorizontalDivider(thickness = 0.5.dp, color = LoopColors.Divider)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            val comments = remember(state.items) { state.items.distinctBy { it.id } }
            val error = state.error
            when {
                comments.isEmpty() && error != null -> ErrorBox(message = error, onRetry = viewModel::reload)
                comments.isEmpty() && (!state.loadedOnce || state.isLoading) -> LoadingBox()
                comments.isEmpty() -> EmptyBox(
                    title = "No comments yet.",
                    subtitle = "Start the conversation.",
                    icon = Icons.Outlined.ChatBubbleOutline,
                )
                else -> CommentList(
                    comments = comments,
                    threads = threads,
                    now = now,
                    isLoadingMore = state.isLoading,
                    loadMoreError = error,
                    listState = listState,
                    canDelete = canDelete,
                    onLoadMore = viewModel::loadMore,
                    onRetryLoadMore = viewModel::retryLoadMore,
                    onOpenProfile = openProfile,
                    onHashtag = openHashtag,
                    onReply = startReply,
                    onToggleLike = { comment -> if (requireAuth()) viewModel.toggleLike(comment) },
                    onRequestDelete = { comment -> pendingDelete = comment },
                    onShowReplies = viewModel::showReplies,
                    onLoadMoreReplies = viewModel::loadReplies,
                    onHideReplies = viewModel::hideReplies,
                )
            }
            // The app-wide host sits under this sheet's window; mirror it here so messages stay visible.
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
        CommentInput(
            me = me,
            text = text,
            onTextChange = { text = it },
            replyTarget = replyTarget,
            onCancelReply = { replyTarget = null },
            isPosting = isPosting,
            onSend = { viewModel.post(text, replyTarget) },
            onSignedOutTap = { requireAuth() },
            focusRequester = focusRequester,
        )
    }

    pendingDelete?.let { target ->
        DeleteCommentDialog(
            withReplies = target.parentId == null && target.replyCount > 0,
            onConfirm = {
                pendingDelete = null
                viewModel.delete(target)
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

@Composable
private fun CommentsHeader(count: Long, onClose: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
    ) {
        Text(
            text = if (count == 1L) "1 comment" else "${formatCount(count)} comments",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.align(Alignment.Center),
        )
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 4.dp),
        ) {
            Icon(imageVector = Icons.Rounded.Close, contentDescription = "Close comments")
        }
    }
}
