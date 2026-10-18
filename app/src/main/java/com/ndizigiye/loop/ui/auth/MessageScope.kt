package com.ndizigiye.loop.ui.auth

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
 * A scope for snackbars that must stay up after this destination closes: the host
 * activity's lifecycleScope, which lives exactly as long as the app-wide
 * SnackbarHostState. A scope of the closing screen would dismiss the message with it.
 */
@Composable
internal fun rememberMessageScope(): CoroutineScope {
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
