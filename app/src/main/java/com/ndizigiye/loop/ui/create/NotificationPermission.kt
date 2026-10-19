package com.ndizigiye.loop.ui.create

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Wraps [action] so that on Android 13+ it first asks, once per screen, for permission
 * to post notifications (the upload's progress and result). [action] runs whatever the
 * answer: without the permission the upload still works, just without a notification.
 */
@Composable
internal fun rememberAfterNotificationPermission(action: () -> Unit): () -> Unit {
    val context = LocalContext.current
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { action() }
    return {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || asked ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            action()
        } else {
            asked = true
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
