package com.ndizigiye.loop.ui.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ndizigiye.loop.api.NotificationDto
import com.ndizigiye.loop.data.PagedState
import com.ndizigiye.loop.ui.common.EmptyBox
import com.ndizigiye.loop.ui.common.ErrorBox
import com.ndizigiye.loop.ui.common.LoadMoreEffect
import com.ndizigiye.loop.ui.common.LoadMoreErrorFooter
import com.ndizigiye.loop.ui.common.LoadingBox
import com.ndizigiye.loop.ui.common.LoadingMoreFooter
import com.ndizigiye.loop.ui.common.LoopTopBar
import com.ndizigiye.loop.ui.common.SignedOutPrompt
import com.ndizigiye.loop.ui.navigation.LocalAppContainer
import com.ndizigiye.loop.ui.navigation.LocalNavigator
import com.ndizigiye.loop.ui.theme.LoopColors
import kotlinx.coroutines.delay

private const val CLOCK_TICK_MS = 60_000L

/** The Inbox tab: likes, comments, replies, mentions and new followers, newest first. */
@Composable
fun InboxScreen(modifier: Modifier = Modifier) {
    val container = LocalAppContainer.current
    val session by container.sessionStore.session.collectAsStateWithLifecycle()
    val userId = session?.user?.id
    if (userId == null) {
        SignedOutPrompt(
            title = "Inbox",
            subtitle = "See likes, comments and new followers here",
            modifier = modifier.background(MaterialTheme.colorScheme.background),
        )
    } else {
        key(userId) {
            SignedInInbox(userId = userId, modifier = modifier)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SignedInInbox(userId: Long, modifier: Modifier = Modifier) {
    val container = LocalAppContainer.current
    val navigator = LocalNavigator.current
    val viewModel: InboxViewModel = viewModel(key = "inbox-$userId") {
        InboxViewModel(container.inboxRepository)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isPullRefreshing by viewModel.isPullRefreshing.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val nowMillis = rememberNowMillis()

    // Reload (and mark everything read) whenever the tab appears and when the app returns to the foreground.
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val resumed = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    LaunchedEffect(viewModel, resumed) {
        if (resumed) viewModel.reload()
    }
    LoadMoreEffect(state = listState, enabled = state.error == null, onLoadMore = viewModel::loadMore)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        LoopTopBar(title = "Inbox", onBack = null)
        HorizontalDivider(thickness = 0.5.dp, color = LoopColors.Divider)
        PullToRefreshBox(
            isRefreshing = isPullRefreshing,
            onRefresh = viewModel::pullToRefresh,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
            ) {
                notificationItems(
                    state = state,
                    nowMillis = nowMillis,
                    onOpenProfile = navigator::openProfile,
                    onOpenVideo = navigator::openVideo,
                    onRetry = viewModel::retry,
                )
            }
        }
    }
}

/** The notifications of [state], or its loading, error or empty state filling the list. */
private fun LazyListScope.notificationItems(
    state: PagedState<NotificationDto>,
    nowMillis: Long,
    onOpenProfile: (String) -> Unit,
    onOpenVideo: (Long) -> Unit,
    onRetry: () -> Unit,
) {
    val error = state.error
    val notifications = state.items
    when {
        notifications.isEmpty() && error != null -> item(key = "error") {
            ErrorBox(message = error, onRetry = onRetry, modifier = Modifier.fillParentMaxSize())
        }
        notifications.isEmpty() && !state.loadedOnce -> item(key = "loading") {
            LoadingBox(modifier = Modifier.fillParentMaxSize())
        }
        notifications.isEmpty() -> item(key = "empty") {
            EmptyBox(
                title = "No activity yet",
                subtitle = "Likes, comments, mentions and new followers will show up here.",
                icon = Icons.Outlined.Notifications,
                modifier = Modifier.fillParentMaxSize(),
            )
        }
        else -> {
            val keys = uniqueNotificationKeys(notifications)
            items(
                count = notifications.size,
                key = { index -> keys[index] },
                contentType = { "notification" },
            ) { index ->
                NotificationRow(
                    notification = notifications[index],
                    nowMillis = nowMillis,
                    onOpenProfile = onOpenProfile,
                    onOpenVideo = onOpenVideo,
                )
            }
            if (state.isLoading) {
                item(key = "loading-more", contentType = "footer") { LoadingMoreFooter() }
            } else if (error != null) {
                item(key = "load-more-error", contentType = "footer") {
                    LoadMoreErrorFooter(message = error, onRetry = onRetry)
                }
            }
        }
    }
}

/** The current time, updated every minute so relative timestamps stay fresh. */
@Composable
private fun rememberNowMillis(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(CLOCK_TICK_MS)
            now = System.currentTimeMillis()
        }
    }
    return now
}

/** "n:<id>" for the first occurrence of an id, "n:<id>:<k>" for later ones (pages can overlap). */
private fun uniqueNotificationKeys(notifications: List<NotificationDto>): List<String> {
    val seen = HashMap<Long, Int>(notifications.size)
    return notifications.map { notification ->
        val occurrence = seen[notification.id] ?: 0
        seen[notification.id] = occurrence + 1
        if (occurrence == 0) "n:${notification.id}" else "n:${notification.id}:$occurrence"
    }
}
