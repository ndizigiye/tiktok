package com.ndizigiye.loop.ui.profile

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.ndizigiye.loop.ui.theme.LoopColors

/** Icon-only tabs that switch the grid below a profile header. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfileTabRow(
    tabs: List<ProfileTab>,
    selected: ProfileTab,
    onSelect: (ProfileTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    PrimaryTabRow(
        selectedTabIndex = tabs.indexOf(selected).coerceAtLeast(0),
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        divider = { HorizontalDivider(thickness = 0.5.dp, color = LoopColors.Divider) },
    ) {
        tabs.forEach { tab ->
            Tab(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                icon = { Icon(imageVector = tab.icon(), contentDescription = tab.label()) },
                selectedContentColor = MaterialTheme.colorScheme.onBackground,
                unselectedContentColor = LoopColors.TextMuted,
            )
        }
    }
}

private fun ProfileTab.icon(): ImageVector = when (this) {
    ProfileTab.VIDEOS -> Icons.Filled.GridOn
    ProfileTab.LIKED -> Icons.Filled.FavoriteBorder
    ProfileTab.BOOKMARKS -> Icons.Filled.BookmarkBorder
}

private fun ProfileTab.label(): String = when (this) {
    ProfileTab.VIDEOS -> "Videos"
    ProfileTab.LIKED -> "Liked videos"
    ProfileTab.BOOKMARKS -> "Saved videos"
}
