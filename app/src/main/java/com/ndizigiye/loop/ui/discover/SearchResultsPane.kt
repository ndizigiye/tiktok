package com.ndizigiye.loop.ui.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ndizigiye.loop.api.HashtagDto
import com.ndizigiye.loop.api.SearchResultDto
import com.ndizigiye.loop.api.UserDto
import com.ndizigiye.loop.ui.common.EmptyBox
import com.ndizigiye.loop.ui.common.ErrorBox
import com.ndizigiye.loop.ui.common.LoadingBox
import com.ndizigiye.loop.ui.common.fullWidthItem
import com.ndizigiye.loop.ui.common.videoGridItems
import com.ndizigiye.loop.ui.navigation.LocalNavigator
import com.ndizigiye.loop.ui.theme.LoopColors

private const val TOP_USER_COUNT = 3

/**
 * Results for a non-blank query: the tabs once results arrived, otherwise a
 * loading or error state. [onOpenResult] runs before a result is opened;
 * [onScrollStarted] when the user starts scrolling the results.
 */
@Composable
internal fun SearchResultsPane(
    state: SearchUiState,
    onRetry: () -> Unit,
    onSelectTab: (SearchTab) -> Unit,
    onOpenResult: () -> Unit,
    onScrollStarted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val results = state.results
    val error = state.error
    when {
        results != null -> SearchResultTabs(
            results = results,
            selectedTab = state.selectedTab,
            isLoading = state.isLoading,
            onSelectTab = onSelectTab,
            onOpenResult = onOpenResult,
            onScrollStarted = onScrollStarted,
            modifier = modifier,
        )
        error != null -> ErrorBox(message = error, onRetry = onRetry, modifier = modifier)
        else -> LoadingBox(modifier = modifier)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchResultTabs(
    results: SearchResults,
    selectedTab: SearchTab,
    isLoading: Boolean,
    onSelectTab: (SearchTab) -> Unit,
    onOpenResult: () -> Unit,
    onScrollStarted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val navigator = LocalNavigator.current
    val result = results.result
    val emptyTitle = "No results for \"${results.query}\""
    val openUser: (UserDto) -> Unit = { user ->
        onOpenResult()
        navigator.openProfile(user.username)
    }
    val openHashtag: (HashtagDto) -> Unit = { hashtag ->
        onOpenResult()
        navigator.openHashtag(hashtag.name)
    }
    val openVideo: (Int) -> Unit = { index ->
        onOpenResult()
        navigator.openVideoPager(result.videos, index)
    }

    if (results.isEmpty) {
        Box(modifier = modifier.fillMaxSize()) {
            EmptyBox(title = emptyTitle, icon = Icons.Outlined.SearchOff)
            SearchProgressBar(visible = isLoading, modifier = Modifier.align(Alignment.TopCenter))
        }
    } else {
        Column(modifier = modifier.fillMaxSize()) {
            PrimaryTabRow(
                selectedTabIndex = selectedTab.ordinal,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onBackground,
                divider = { HorizontalDivider(thickness = 0.5.dp, color = LoopColors.Divider) },
            ) {
                SearchTab.entries.forEach { tab ->
                    Tab(
                        selected = tab == selectedTab,
                        onClick = { onSelectTab(tab) },
                        text = { Text(text = tab.title(), maxLines = 1) },
                        selectedContentColor = MaterialTheme.colorScheme.onBackground,
                        unselectedContentColor = LoopColors.TextMuted,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                when (selectedTab) {
                    SearchTab.TOP -> TopResults(
                        result = result,
                        onSeeAllUsers = { onSelectTab(SearchTab.USERS) },
                        onUserClick = openUser,
                        onHashtagClick = openHashtag,
                        onVideoClick = openVideo,
                        onScrollStarted = onScrollStarted,
                    )
                    SearchTab.USERS -> UserResults(
                        users = result.users,
                        emptyTitle = emptyTitle,
                        onUserClick = openUser,
                        onScrollStarted = onScrollStarted,
                    )
                    SearchTab.VIDEOS -> VideoResults(
                        result = result,
                        emptyTitle = emptyTitle,
                        onVideoClick = openVideo,
                        onScrollStarted = onScrollStarted,
                    )
                    SearchTab.HASHTAGS -> HashtagResults(
                        hashtags = result.hashtags,
                        emptyTitle = emptyTitle,
                        onHashtagClick = openHashtag,
                        onScrollStarted = onScrollStarted,
                    )
                }
                SearchProgressBar(visible = isLoading, modifier = Modifier.align(Alignment.TopCenter))
            }
        }
    }
}

/** Up to three users, then the videos; the hashtags when there are neither. */
@Composable
private fun TopResults(
    result: SearchResultDto,
    onSeeAllUsers: () -> Unit,
    onUserClick: (UserDto) -> Unit,
    onHashtagClick: (HashtagDto) -> Unit,
    onVideoClick: (index: Int) -> Unit,
    onScrollStarted: () -> Unit,
) {
    val gridState = rememberLazyGridState()
    DismissOnScroll(isScrolling = gridState.isScrollInProgress, onScrollStarted = onScrollStarted)
    val users = result.users.take(TOP_USER_COUNT)
    val hashtags = result.hashtags
    LazyVerticalGrid(
        columns = GridCells.Fixed(GRID_COLUMNS),
        modifier = Modifier.fillMaxSize(),
        state = gridState,
        horizontalArrangement = Arrangement.spacedBy(1.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        if (users.isNotEmpty()) {
            fullWidthItem("top:users-title") {
                ResultSectionTitle(
                    title = "Users",
                    action = if (result.users.size > users.size) "See all" else null,
                    onAction = onSeeAllUsers,
                )
            }
            val keys = uniqueKeys("top-user", users.map { it.id })
            items(
                count = users.size,
                key = { index -> keys[index] },
                span = { GridItemSpan(maxLineSpan) },
                contentType = { "user" },
            ) { index ->
                val user = users[index]
                UserResultRow(user = user, onClick = { onUserClick(user) })
            }
        }
        if (result.videos.isNotEmpty()) {
            if (users.isNotEmpty()) {
                fullWidthItem("top:videos-title") { ResultSectionTitle(title = "Videos") }
            }
            videoGridItems(result.videos, onVideoClick)
        } else if (users.isEmpty()) {
            items(
                count = hashtags.size,
                key = { index -> "top-hashtag:$index:${hashtags[index].name}" },
                span = { GridItemSpan(maxLineSpan) },
                contentType = { "hashtag" },
            ) { index ->
                val hashtag = hashtags[index]
                HashtagResultRow(hashtag = hashtag, onClick = { onHashtagClick(hashtag) })
            }
        }
    }
}

@Composable
private fun UserResults(
    users: List<UserDto>,
    emptyTitle: String,
    onUserClick: (UserDto) -> Unit,
    onScrollStarted: () -> Unit,
) {
    if (users.isEmpty()) {
        EmptyBox(title = emptyTitle, icon = Icons.Outlined.SearchOff)
    } else {
        val listState = rememberLazyListState()
        DismissOnScroll(isScrolling = listState.isScrollInProgress, onScrollStarted = onScrollStarted)
        val keys = remember(users) { uniqueKeys("user", users.map { it.id }) }
        LazyColumn(modifier = Modifier.fillMaxSize(), state = listState) {
            items(count = users.size, key = { index -> keys[index] }, contentType = { "user" }) { index ->
                val user = users[index]
                UserResultRow(user = user, onClick = { onUserClick(user) })
            }
        }
    }
}

@Composable
private fun VideoResults(
    result: SearchResultDto,
    emptyTitle: String,
    onVideoClick: (index: Int) -> Unit,
    onScrollStarted: () -> Unit,
) {
    if (result.videos.isEmpty()) {
        EmptyBox(title = emptyTitle, icon = Icons.Outlined.SearchOff)
    } else {
        val gridState = rememberLazyGridState()
        DismissOnScroll(isScrolling = gridState.isScrollInProgress, onScrollStarted = onScrollStarted)
        LazyVerticalGrid(
            columns = GridCells.Fixed(GRID_COLUMNS),
            modifier = Modifier.fillMaxSize(),
            state = gridState,
            horizontalArrangement = Arrangement.spacedBy(1.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            videoGridItems(result.videos, onVideoClick)
        }
    }
}

@Composable
private fun HashtagResults(
    hashtags: List<HashtagDto>,
    emptyTitle: String,
    onHashtagClick: (HashtagDto) -> Unit,
    onScrollStarted: () -> Unit,
) {
    if (hashtags.isEmpty()) {
        EmptyBox(title = emptyTitle, icon = Icons.Outlined.SearchOff)
    } else {
        val listState = rememberLazyListState()
        DismissOnScroll(isScrolling = listState.isScrollInProgress, onScrollStarted = onScrollStarted)
        LazyColumn(modifier = Modifier.fillMaxSize(), state = listState) {
            items(
                count = hashtags.size,
                key = { index -> "hashtag:$index:${hashtags[index].name}" },
                contentType = { "hashtag" },
            ) { index ->
                val hashtag = hashtags[index]
                HashtagResultRow(hashtag = hashtag, onClick = { onHashtagClick(hashtag) })
            }
        }
    }
}

@Composable
private fun ResultSectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 8.dp)
            .heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        if (action != null) {
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
private fun SearchProgressBar(visible: Boolean, modifier: Modifier = Modifier) {
    if (visible) {
        LinearProgressIndicator(
            modifier = modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary,
            trackColor = Color.Transparent,
        )
    }
}

/** Runs [onScrollStarted] each time a scroll gesture or fling starts. */
@Composable
private fun DismissOnScroll(isScrolling: Boolean, onScrollStarted: () -> Unit) {
    val currentOnScrollStarted by rememberUpdatedState(onScrollStarted)
    LaunchedEffect(isScrolling) {
        if (isScrolling) currentOnScrollStarted()
    }
}

private fun SearchTab.title(): String = when (this) {
    SearchTab.TOP -> "Top"
    SearchTab.USERS -> "Users"
    SearchTab.VIDEOS -> "Videos"
    SearchTab.HASHTAGS -> "Hashtags"
}
