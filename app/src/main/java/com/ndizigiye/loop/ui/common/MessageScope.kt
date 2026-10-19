package com.ndizigiye.loop.ui.common

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope

/**
 * A scope for snackbars that must stay up after the current destination closes,
 * e.g. a confirmation shown while navigating back: the host activity's
 * lifecycleScope, which lives exactly as long as the app-wide [LocalSnackbarHostState].
 * A scope of the closing screen would dismiss the message with it. Requests that must
 * finish regardless of the UI belong in `AppContainer.applicationScope` instead.
 */
@Composable
fun rememberMessageScope(): CoroutineScope {
    val context = LocalContext.current
    val fallback = rememberCoroutineScope()
    return remember(context, fallback) { context.findLifecycleOwner()?.lifecycleScope ?: fallback }
}

private fun Context.findLifecycleOwner(): LifecycleOwner? {
    var current: Context? = this
    while (current != null) {
        if (current is LifecycleOwner) return current
        current = (current as? ContextWrapper)?.baseContext
    }
    return null
}
