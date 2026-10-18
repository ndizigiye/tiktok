package com.ndizigiye.loop.ui.create

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ndizigiye.loop.ui.theme.LoopColors

private val CapturePermissions = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)

/** Camera and microphone access for recording. */
internal class CapturePermissionState(
    /** Both permissions are granted. */
    val granted: Boolean,
    /** The system prompt was shown and access is still missing. */
    val showRationale: Boolean,
    /** Shows the system prompt again (Android stops showing it after repeated denials). */
    val request: () -> Unit,
)

/**
 * Asks for camera and microphone access on first entry and re-checks it on every
 * resume, so access granted in the system settings is picked up on return.
 */
@Composable
internal fun rememberCapturePermissionState(): CapturePermissionState {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(context.hasCapturePermissions()) }
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = context.hasCapturePermissions()
    }

    LaunchedEffect(launcher) {
        if (!granted && !asked) {
            asked = true
            launcher.launch(CapturePermissions)
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) granted = context.hasCapturePermissions()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return CapturePermissionState(
        granted = granted,
        showRationale = !granted && asked,
        request = { launcher.launch(CapturePermissions) },
    )
}

internal fun Context.hasCapturePermissions(): Boolean =
    CapturePermissions.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }

/** Opens this app's page in the system settings; false when there is no such screen. */
internal fun Context.openAppSettings(): Boolean {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
    return try {
        startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}

/** Explains why the recorder needs access, with ways to grant it or upload instead. */
@Composable
internal fun CapturePermissionRationale(
    onAllow: () -> Unit,
    onOpenSettings: () -> Unit,
    onUpload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Filled.Videocam,
            contentDescription = null,
            tint = LoopColors.OverlayText,
            modifier = Modifier.size(56.dp),
        )
        Text(
            text = "Record with Loop",
            style = MaterialTheme.typography.titleLarge,
            color = LoopColors.OverlayText,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Allow access to your camera and microphone to record videos. " +
                "You can also upload a video from your gallery.",
            style = MaterialTheme.typography.bodyMedium,
            color = LoopColors.OverlayTextMuted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onAllow,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            Text("Allow access")
        }
        OutlinedButton(
            onClick = onOpenSettings,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = LoopColors.OverlayText),
        ) {
            Text("Open settings")
        }
        TextButton(onClick = onUpload) {
            Text("Upload from gallery", color = LoopColors.AccentLight)
        }
    }
}
