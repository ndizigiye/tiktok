package com.ndizigiye.loop.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ndizigiye.loop.api.UserDto
import com.ndizigiye.loop.api.VideoDto
import com.ndizigiye.loop.data.VideoStats
import com.ndizigiye.loop.ui.navigation.LocalAppContainer
import com.ndizigiye.loop.ui.navigation.LocalNavigator

/** [path] resolved against the configured server URL; follows changes of that URL. */
@Composable
fun rememberResolvedUrl(path: String?): String? {
    val sessionStore = LocalAppContainer.current.sessionStore
    val baseUrl by sessionStore.baseUrl.collectAsStateWithLifecycle()
    return remember(path, baseUrl) { sessionStore.resolveUrl(path) }
}

/** Live like/bookmark/comment/share state of [video] from the InteractionStore. */
@Composable
fun rememberVideoStats(video: VideoDto): VideoStats {
    val videos = LocalAppContainer.current.interactions.videos.collectAsStateWithLifecycle()
    val stats by remember(video) {
        derivedStateOf { videos.value[video.id] ?: VideoStats.from(video) }
    }
    return stats
}

/** Whether the signed-in user follows [user], kept in sync across screens. */
@Composable
fun rememberIsFollowing(user: UserDto): Boolean {
    val following = LocalAppContainer.current.interactions.following.collectAsStateWithLifecycle()
    val isFollowing by remember(user.id, user.isFollowing) {
        derivedStateOf { following.value[user.id] ?: user.isFollowing }
    }
    return isFollowing
}

/**
 * A back action for on-screen back buttons. It ignores taps once the destination
 * has started leaving (e.g. a double tap during the exit animation), so it never
 * pops the screen underneath.
 */
@Composable
fun rememberBackAction(): () -> Unit {
    val navigator = LocalNavigator.current
    val lifecycleOwner = LocalLifecycleOwner.current
    return remember(navigator, lifecycleOwner) {
        {
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) navigator.back()
        }
    }
}
