package com.ndizigiye.loop.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ndizigiye.loop.ui.navigation.MainTab
import com.ndizigiye.loop.ui.theme.LoopColors

/** Height of the bottom bar, excluding the navigation-bar inset below it. */
val MainBottomBarHeight: Dp = 56.dp

private class TabSpec(val label: String, val selectedIcon: ImageVector, val icon: ImageVector)

private fun MainTab.spec(): TabSpec = when (this) {
    MainTab.HOME -> TabSpec("Home", Icons.Filled.Home, Icons.Outlined.Home)
    MainTab.DISCOVER -> TabSpec("Discover", Icons.Filled.Explore, Icons.Outlined.Explore)
    MainTab.INBOX -> TabSpec("Inbox", Icons.Filled.Inbox, Icons.Outlined.Inbox)
    MainTab.PROFILE -> TabSpec("Me", Icons.Filled.Person, Icons.Outlined.Person)
}

/**
 * Loop's bottom navigation: Home, Discover, the create button, Inbox and Me.
 * Black over the full-screen feed, surface-coloured elsewhere.
 */
@Composable
internal fun MainBottomBar(
    selectedTab: MainTab,
    unreadCount: Long,
    onSelect: (MainTab) -> Unit,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val overVideo = selectedTab == MainTab.HOME
    val background = if (overVideo) Color.Black else MaterialTheme.colorScheme.surface
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(background)
            .navigationBarsPadding(),
    ) {
        if (!overVideo) HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(MainBottomBarHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TabItem(MainTab.HOME, selectedTab, overVideo, badgeCount = 0, onSelect = onSelect)
            TabItem(MainTab.DISCOVER, selectedTab, overVideo, badgeCount = 0, onSelect = onSelect)
            CreateButton(onClick = onCreate)
            TabItem(MainTab.INBOX, selectedTab, overVideo, badgeCount = unreadCount, onSelect = onSelect)
            TabItem(MainTab.PROFILE, selectedTab, overVideo, badgeCount = 0, onSelect = onSelect)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RowScope.TabItem(
    tab: MainTab,
    selectedTab: MainTab,
    overVideo: Boolean,
    badgeCount: Long,
    onSelect: (MainTab) -> Unit,
) {
    val spec = tab.spec()
    val selected = tab == selectedTab
    val tint = when {
        selected -> Color.White
        overVideo -> LoopColors.OverlayTextMuted
        else -> LoopColors.TextMuted
    }
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false),
                role = Role.Tab,
                onClick = { onSelect(tab) },
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        BadgedBox(
            badge = {
                if (badgeCount > 0) {
                    Badge(containerColor = LoopColors.Like, contentColor = Color.White) {
                        Text(if (badgeCount > 99) "99+" else badgeCount.toString())
                    }
                }
            },
        ) {
            Icon(
                imageVector = if (selected) spec.selectedIcon else spec.icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(26.dp),
            )
        }
        Text(text = spec.label, style = MaterialTheme.typography.labelSmall, color = tint)
    }
}

/** Loop's create button: a violet rounded rectangle with a white plus. */
@Composable
private fun RowScope.CreateButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 48.dp, height = 32.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(LoopColors.Accent)
                .clickable(role = Role.Button, onClickLabel = "Create", onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = "Create",
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
