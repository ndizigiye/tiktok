package com.ndizigiye.loop.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import com.ndizigiye.loop.ui.discover.DiscoverScreen
import com.ndizigiye.loop.ui.feed.FeedScreen
import com.ndizigiye.loop.ui.inbox.InboxScreen
import com.ndizigiye.loop.ui.navigation.LocalAppContainer
import com.ndizigiye.loop.ui.navigation.LocalNavigator
import com.ndizigiye.loop.ui.navigation.MainTab
import com.ndizigiye.loop.ui.profile.ProfileScreen

/**
 * The main shell: the selected tab's screen above [MainBottomBar]. Tab state is
 * kept per tab while switching; the selection lives in `AppContainer.mainTab`.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainScreen() {
    val container = LocalAppContainer.current
    val navigator = LocalNavigator.current
    val selectedTab by container.mainTab.collectAsStateWithLifecycle()
    val unreadCount by container.inboxRepository.unreadCount.collectAsStateWithLifecycle()
    val session by container.sessionStore.session.collectAsStateWithLifecycle()
    val signedInUserId = session?.user?.id

    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val resumed = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    LaunchedEffect(selectedTab, signedInUserId, resumed) {
        if (resumed) container.inboxRepository.refreshUnreadCount()
    }

    val tabStates = rememberSaveableStateHolder()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                // The bar below already pads for the navigation bar.
                .consumeWindowInsets(WindowInsets.navigationBars),
        ) {
            tabStates.SaveableStateProvider(selectedTab.name) {
                val tabModifier = Modifier.fillMaxSize()
                when (selectedTab) {
                    MainTab.HOME -> FeedScreen(isActive = selectedTab == MainTab.HOME, modifier = tabModifier)
                    MainTab.DISCOVER -> DiscoverScreen(modifier = tabModifier)
                    MainTab.INBOX -> InboxScreen(modifier = tabModifier)
                    MainTab.PROFILE -> ProfileScreen(username = null, modifier = tabModifier)
                }
            }
        }
        MainBottomBar(
            selectedTab = selectedTab,
            unreadCount = unreadCount,
            onSelect = { container.mainTab.value = it },
            onCreate = { if (navigator.requireAuth()) navigator.openCamera() },
        )
    }
}
