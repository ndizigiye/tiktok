package com.ndizigiye.loop.ui.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope

/**
 * A coroutine scope of the hosting activity rather than the current destination, for
 * work that must outlive the screen, such as a snackbar shown while navigating back.
 */
@Composable
internal fun rememberWindowScope(): CoroutineScope {
    val view = LocalView.current
    val fallback = rememberCoroutineScope()
    return remember(view, fallback) { view.findViewTreeLifecycleOwner()?.lifecycleScope ?: fallback }
}
