package com.ndizigiye.loop.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.People
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ndizigiye.loop.api.UserDto
import com.ndizigiye.loop.data.PagedState
import com.ndizigiye.loop.ui.common.EmptyBox
import com.ndizigiye.loop.ui.common.ErrorBox
import com.ndizigiye.loop.ui.common.LoadMoreEffect
import com.ndizigiye.loop.ui.common.LoadMoreErrorFooter
import com.ndizigiye.loop.ui.common.LoadingBox
import com.ndizigiye.loop.ui.common.LoadingMoreFooter
import com.ndizigiye.loop.ui.common.LoopTopBar
import com.ndizigiye.loop.ui.navigation.LocalAppContainer
import com.ndizigiye.loop.ui.navigation.LocalNavigator
import com.ndizigiye.loop.ui.theme.LoopColors
import kotlinx.coroutines.launch

/** Who [username] follows and who follows them, as two swipeable tabs. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowListScreen(username: String, showFollowers: Boolean) {
    val container = LocalAppContainer.current
    val name = username.lowercase()
    val viewModel: FollowListViewModel = viewModel(key = "follow-list-$name") {
        FollowListViewModel(username = name, userRepository = container.userRepository)
    }
    val tabs = FollowListTab.entries
    val initialTab = if (showFollowers) FollowListTab.FOLLOWERS else FollowListTab.FOLLOWING
    val pagerState = rememberPagerState(initialPage = tabs.indexOf(initialTab)) { tabs.size }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        LoopTopBar(title = "@$name")
        PrimaryTabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
            divider = { HorizontalDivider(thickness = 0.5.dp, color = LoopColors.Divider) },
        ) {
            tabs.forEachIndexed { index, tab ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = { Text(tab.title()) },
                    selectedContentColor = MaterialTheme.colorScheme.onBackground,
                    unselectedContentColor = LoopColors.TextMuted,
                )
            }
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            key = { page -> tabs[page].name },
        ) { page ->
            FollowListPage(viewModel = viewModel, tab = tabs[page])
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FollowListPage(viewModel: FollowListViewModel, tab: FollowListTab) {
    val navigator = LocalNavigator.current
    val state by viewModel.state(tab).collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(viewModel, tab) { viewModel.ensureLoaded(tab) }
    LoadMoreEffect(listState, enabled = state.error == null) { viewModel.loadMore(tab) }

    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = { viewModel.refresh(tab) },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = WindowInsets.navigationBars.asPaddingValues(),
        ) {
            userItems(
                tab = tab,
                state = state,
                onUserClick = { user -> navigator.openProfile(user.username) },
                onRetry = { viewModel.retry(tab) },
            )
        }
    }
}

/** The users of [state], or its loading, error or empty state filling the page. */
private fun LazyListScope.userItems(
    tab: FollowListTab,
    state: PagedState<UserDto>,
    onUserClick: (UserDto) -> Unit,
    onRetry: () -> Unit,
) {
    val error = state.error
    val users = state.items
    when {
        users.isEmpty() && error != null -> item(key = "error") {
            ErrorBox(message = error, onRetry = onRetry, modifier = Modifier.fillParentMaxSize())
        }
        users.isEmpty() && !state.loadedOnce -> item(key = "loading") {
            LoadingBox(modifier = Modifier.fillParentMaxSize())
        }
        users.isEmpty() -> item(key = "empty") {
            EmptyBox(
                title = tab.emptyTitle(),
                icon = Icons.Outlined.People,
                modifier = Modifier.fillParentMaxSize(),
            )
        }
        else -> {
            val keys = uniqueUserKeys(users)
            items(count = users.size, key = { index -> keys[index] }, contentType = { "user" }) { index ->
                val user = users[index]
                FollowUserRow(user = user, onClick = { onUserClick(user) })
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

private fun FollowListTab.title(): String = when (this) {
    FollowListTab.FOLLOWING -> "Following"
    FollowListTab.FOLLOWERS -> "Followers"
}

private fun FollowListTab.emptyTitle(): String = when (this) {
    FollowListTab.FOLLOWING -> "Not following anyone yet"
    FollowListTab.FOLLOWERS -> "No followers yet"
}

/** "u:<id>" for the first occurrence of an id, "u:<id>:<n>" for later ones (lists can shift between pages). */
private fun uniqueUserKeys(users: List<UserDto>): List<String> {
    val seen = HashMap<Long, Int>(users.size)
    return users.map { user ->
        val occurrence = seen[user.id] ?: 0
        seen[user.id] = occurrence + 1
        if (occurrence == 0) "u:${user.id}" else "u:${user.id}:$occurrence"
    }
}
