package com.ndizigiye.loop.ui.profile

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.ndizigiye.loop.ui.common.LocalSnackbarHostState
import kotlinx.coroutines.launch

/** Opens the system share sheet with a short text about [username]'s profile. */
internal fun Context.shareProfile(username: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "@$username on Loop")
    }
    startActivity(Intent.createChooser(send, "Share profile"))
}

/** An action that copies [username] to the clipboard and confirms it where the system does not. */
@Composable
internal fun rememberCopyUsername(username: String): () -> Unit {
    val clipboard = LocalClipboardManager.current
    val snackbarHostState = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    return remember(username, clipboard, snackbarHostState, scope) {
        {
            clipboard.setText(AnnotatedString(username))
            // Android 13+ shows its own confirmation for clipboard writes.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                scope.launch { snackbarHostState.showSnackbar("Username copied") }
            }
        }
    }
}
