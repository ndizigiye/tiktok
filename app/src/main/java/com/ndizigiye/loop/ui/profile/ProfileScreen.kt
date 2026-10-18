package com.ndizigiye.loop.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ndizigiye.loop.api.VideoDto
import com.ndizigiye.loop.data.PagedState
import com.ndizigiye.loop.ui.common.EmptyBox
import com.ndizigiye.loop.ui.common.ErrorBox
import com.ndizigiye.loop.ui.common.LoadingBox
import com.ndizigiye.loop.ui.common.LocalSnackbarHostState
import com.ndizigiye.loop.ui.common.SignedOutPrompt
import com.ndizigiye.loop.ui.common.videoGridItems
import com.ndizigiye.loop.ui.navigation.LocalAppContainer
import com.ndizigiye.loop.ui.navigation.LocalNavigator

private const val GRID_COLUMNS = 3

/**
 * A creator's profile: header, then a grid of their videos, liked videos and (on your
 * own profile) saved videos. [username] null means the signed-in user, as on the
 * bottom-bar tab.
 */
@Composable
fun ProfileScreen(username: String?, modifier: Modifier = Modifier) {
    val container = LocalAppContainer.current
    val session by container.sessionStore.session.collectAsStateWithLifecycle()
    val target = (username ?: session?.user?.username)?.lowercase()
    if (target == null) {
        SignedOutPrompt(
            title = "Profile",
            subtitle = "Sign up for an account to post videos, follow creators and save favourites",
            modifier = modifier
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding(),
        )
        return
    }
    val viewModel: ProfileViewModel = viewModel(key = "profile-$target") {
        ProfileViewModel(
            username = target,
            userRepository = container.userRepository,
            videoRepository = container.videoRepository,
            sessionStore = container.sessionStore,
        )
    }
    ProfileContent(viewModel = viewModel, showBack = username != null, modifier = modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileContent(viewModel: ProfileViewModel, showBack: Boolean, modifier: Modifier = Modifier) {
    val navigator = LocalNavigator.current
    val snackbarHostState = LocalSnackbarHostState.current
    val context = LocalContext.current
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedTab = ui.selectedTab
    val tabState by viewModel.tabState(selectedTab).collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()
    val username = viewModel.username
    val onShare: () -> Unit = remember(context, username) { { context.shareProfile(username) } }
    val onCopyUsername = rememberCopyUsername(username)

    // Every visit reloads, so a new upload, like or bookmark shows up on return.
    LaunchedEffect(viewModel) { viewModel.refresh() }
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }
    LoadMoreEffect(gridState) { viewModel.loadMore(selectedTab) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        val user = ui.user
        val error = ui.error
        if (user == null) {
            ProfileTopBar(
                username = username,
                isOwn = ui.isOwn,
                showBack = showBack,
                onShare = onShare,
                onCopyUsername = onCopyUsername,
            )
            when {
                ui.notFound -> EmptyBox(
                    title = "This account doesn't exist",
                    subtitle = "Check the username and try again.",
                    icon = Icons.Outlined.PersonOff,
                )
                error != null && !ui.isLoadingUser -> ErrorBox(message = error, onRetry = viewModel::refresh)
                else -> LoadingBox()
            }
        } else {
            PullToRefreshBox(
                isRefreshing = ui.isPullRefreshing,
                onRefresh = viewModel::pullRefresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(GRID_COLUMNS),
                    modifier = Modifier.fillMaxSize(),
                    state = gridState,
                    horizontalArrangement = Arrangement.spacedBy(1.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    fullWidthItem(key = "top-bar") {
                        ProfileTopBar(
                            username = username,
                            isOwn = ui.isOwn,
                            showBack = showBack,
                            onShare = onShare,
                            onCopyUsername = onCopyUsername,
                        )
                    }
                    fullWidthItem(key = "header") {
                        ProfileHeader(
                            user = user,
                            isOwn = ui.isOwn,
                            onFollowingClick = { navigator.openFollowList(username, showFollowers = false) },
                            onFollowersClick = { navigator.openFollowList(username, showFollowers = true) },
                            onEditProfile = navigator::openEditProfile,
                            onShare = onShare,
                        )
                    }
                    fullWidthItem(key = "tabs") {
                        ProfileTabRow(tabs = ui.tabs, selected = selectedTab, onSelect = viewModel::selectTab)
                    }
                    tabItems(
                        tab = selectedTab,
                        state = tabState,
                        isOwn = ui.isOwn,
                        onVideoClick = { index ->
                            navigator.openVideoPager(
                                videos = tabState.items,
                                startIndex = index,
                                nextCursor = tabState.nextCursor,
                                loadMore = viewModel.pageLoader(selectedTab),
                            )
                        },
                        onRetry = { viewModel.retry(selectedTab) },
                    )
                }
            }
        }
    }
}

private fun LazyGridScope.fullWidthItem(key: String, content: @Composable LazyGridItemScope.() -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }, contentType = key, content = content)
}

/** The selected tab's videos, or its loading, error or empty state. */
private fun LazyGridScope.tabItems(
    tab: ProfileTab,
    state: PagedState<VideoDto>,
    isOwn: Boolean,
    onVideoClick: (index: Int) -> Unit,
    onRetry: () -> Unit,
) {
    val error = state.error
    when {
        state.items.isEmpty() && error != null -> fullWidthItem(key = "tab-error") {
            ErrorBox(message = error, onRetry = onRetry, modifier = Modifier.padding(top = 16.dp))
        }
        state.items.isEmpty() && !state.loadedOnce -> fullWidthItem(key = "tab-loading") {
            PageProgress(modifier = Modifier.padding(vertical = 48.dp))
        }
        state.items.isEmpty() -> fullWidthItem(key = "tab-empty") {
            TabEmptyState(tab = tab, isOwn = isOwn)
        }
        else -> {
            videoGridItems(state.items, onVideoClick)
            if (state.isLoading) {
                fullWidthItem(key = "tab-loading-more") { PageProgress(modifier = Modifier.padding(16.dp)) }
            } else if (error != null) {
                fullWidthItem(key = "tab-load-more-error") { PageError(message = error, onRetry = onRetry) }
            }
        }
    }
}

@Composable
private fun TabEmptyState(tab: ProfileTab, isOwn: Boolean) {
    val modifier = Modifier.padding(top = 16.dp)
    when (tab) {
        ProfileTab.VIDEOS -> EmptyBox(
            title = "No videos yet",
            subtitle = if (isOwn) "Your videos will appear here. Tap + to create one." else null,
            icon = Icons.Outlined.VideoLibrary,
            modifier = modifier,
        )
        ProfileTab.LIKED -> EmptyBox(
            title = "No liked videos yet",
            icon = Icons.Outlined.FavoriteBorder,
            modifier = modifier,
        )
        ProfileTab.BOOKMARKS -> EmptyBox(
            title = "Saved videos appear here",
            icon = Icons.Outlined.BookmarkBorder,
            modifier = modifier,
        )
    }
}
