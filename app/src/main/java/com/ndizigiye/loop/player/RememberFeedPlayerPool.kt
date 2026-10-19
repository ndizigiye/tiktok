package com.ndizigiye.loop.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ndizigiye.loop.ui.navigation.LocalAppContainer

/**
 * A [FeedPlayerPool] bound to the calling composable: all players pause when the
 * lifecycle owner (the navigation destination) stops, and are released when the
 * composable leaves the composition.
 */
@Composable
fun rememberFeedPlayerPool(): FeedPlayerPool {
    val context = LocalContext.current
    val videoCache = LocalAppContainer.current.videoCache
    val pool = remember(videoCache) { FeedPlayerPool(context, videoCache) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(pool, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) pool.pauseAll()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(pool) {
        onDispose { pool.release() }
    }
    return pool
}
