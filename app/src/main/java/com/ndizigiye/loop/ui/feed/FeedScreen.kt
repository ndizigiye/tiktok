package com.ndizigiye.loop.ui.feed

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.PersonAddAlt
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ndizigiye.loop.data.FeedType
import com.ndizigiye.loop.ui.common.EmptyBox
import com.ndizigiye.loop.ui.navigation.LocalAppContainer
import com.ndizigiye.loop.ui.navigation.LocalNavigator
import com.ndizigiye.loop.ui.navigation.MainTab
import com.ndizigiye.loop.ui.theme.LoopColors
import kotlinx.coroutines.launch

private const val FOLLOWING_PAGE = 0
private const val FOR_YOU_PAGE = 1
private const val FEED_PAGE_COUNT = 2
private val FeedTopBarHeight = 48.dp
private val TopScrim = Brush.verticalGradient(listOf(Color(0x59000000), Color.Transparent))

/**
 * The Home tab: Following and For You side by side in a horizontal pager (For You
 * first), each a full-screen vertical video feed, under a tab switcher and a
 * search button. Only the visible feed plays, and only while [isActive].
 */
@Composable
fun FeedScreen(isActive: Boolean, modifier: Modifier = Modifier) {
    val container = LocalAppContainer.current
    val navigator = LocalNavigator.current
    val viewModel: FeedViewModel = viewModel {
        FeedViewModel(
            videoRepository = container.videoRepository,
            sessionStore = container.sessionStore,
            applicationScope = container.applicationScope,
        )
    }
    val session by container.sessionStore.session.collectAsStateWithLifecycle()
    val signedIn = session != null
    val tabs = rememberPagerState(initialPage = FOR_YOU_PAGE) { FEED_PAGE_COUNT }
    val scope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        HorizontalPager(
            state = tabs,
            modifier = Modifier.fillMaxSize(),
            key = { page -> page },
        ) { page ->
            val pageActive = isActive && tabs.settledPage == page
            if (page == FOLLOWING_PAGE) {
                FollowingPage(
                    viewModel = viewModel,
                    signedIn = signedIn,
                    isActive = pageActive,
                    onLogIn = navigator::openAuth,
                    onDiscover = { navigator.selectTab(MainTab.DISCOVER) },
                )
            } else {
                FeedPage(
                    viewModel = viewModel,
                    type = FeedType.FOR_YOU,
                    isActive = pageActive,
                    emptyContent = {
                        EmptyBox(
                            title = "No videos yet",
                            subtitle = "Be the first to post one.",
                            icon = Icons.Outlined.VideoLibrary,
                        )
                    },
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .background(TopScrim),
        )
        FeedTopBar(
            selectedPage = tabs.currentPage,
            onSelect = { page -> scope.launch { tabs.animateScrollToPage(page) } },
            onSearch = navigator::openSearch,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

@Composable
private fun FollowingPage(
    viewModel: FeedViewModel,
    signedIn: Boolean,
    isActive: Boolean,
    onLogIn: () -> Unit,
    onDiscover: () -> Unit,
) {
    if (!signedIn) {
        FeedMessage(
            icon = Icons.Outlined.Group,
            title = "Follow accounts to see their videos here",
            actionLabel = "Log in",
            onAction = onLogIn,
        )
    } else {
        LaunchedEffect(isActive) {
            if (isActive) viewModel.refreshFollowingIfEmpty()
        }
        FeedPage(
            viewModel = viewModel,
            type = FeedType.FOLLOWING,
            isActive = isActive,
            emptyContent = {
                FeedMessage(
                    icon = Icons.Outlined.PersonAddAlt,
                    title = "No videos from accounts you follow yet",
                    actionLabel = "Discover creators",
                    onAction = onDiscover,
                )
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeedPage(
    viewModel: FeedViewModel,
    type: FeedType,
    isActive: Boolean,
    emptyContent: @Composable () -> Unit,
) {
    val state by viewModel.state(type).collectAsStateWithLifecycle()
    val refreshState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = { viewModel.refresh(type) },
        modifier = Modifier.fillMaxSize(),
        state = refreshState,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = refreshState,
                isRefreshing = state.isRefreshing,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = FeedTopBarHeight),
            )
        },
    ) {
        VerticalVideoFeed(
            state = state,
            isActive = isActive,
            onLoadMore = { viewModel.loadMore(type) },
            onRetry = { viewModel.refresh(type) },
            onViewed = viewModel::recordView,
            onRemove = viewModel::remove,
            modifier = Modifier.fillMaxSize(),
            emptyContent = emptyContent,
        )
    }
}

@Composable
private fun FeedTopBar(
    selectedPage: Int,
    onSelect: (Int) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(FeedTopBarHeight),
    ) {
        Row(
            modifier = Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FeedTab(
                label = "Following",
                selected = selectedPage == FOLLOWING_PAGE,
                onClick = { onSelect(FOLLOWING_PAGE) },
            )
            FeedTab(
                label = "For You",
                selected = selectedPage == FOR_YOU_PAGE,
                onClick = { onSelect(FOR_YOU_PAGE) },
            )
        }
        IconButton(
            onClick = onSearch,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 4.dp),
        ) {
            ShadowedIcon(
                imageVector = Icons.Filled.Search,
                contentDescription = "Search",
                tint = LoopColors.OverlayText,
                modifier = Modifier.size(26.dp),
            )
        }
    }
}

@Composable
private fun FeedTab(label: String, selected: Boolean, onClick: () -> Unit) {
    val color by animateColorAsState(
        targetValue = if (selected) Color.White else Color.White.copy(alpha = 0.6f),
        label = "feedTabColor",
    )
    Column(
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium.copy(shadow = OverlayTextShadow),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            color = color,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .size(width = 22.dp, height = 2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(if (selected) Color.White else Color.Transparent),
        )
    }
}

/** A centred message with one action, for the Following page's signed-out and empty states. */
@Composable
private fun FeedMessage(
    icon: ImageVector,
    title: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = LoopColors.TextMuted,
            modifier = Modifier.size(56.dp),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = LoopColors.OverlayText,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onAction) {
            Text(actionLabel)
        }
    }
}
