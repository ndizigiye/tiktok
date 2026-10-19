package com.ndizigiye.loop.e2e.harness

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.ndizigiye.loop.MainActivity
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowActivity

// Stand-ins for the system UI that Robolectric has no screens for: permission dialogs,
// the photo picker and the clipboard.

/** The runtime-permission request MainActivity made last, or null. */
private fun ActivityScenario<MainActivity>.lastPermissionRequest(): ShadowActivity.PermissionsRequest? {
    var request: ShadowActivity.PermissionsRequest? = null
    onActivity { activity -> request = shadowOf(activity).lastRequestedPermission }
    return request
}

/** Runs [action] and waits until MainActivity asks for exactly [permissions] in response. */
fun ComposeTestRule.expectPermissionRequest(
    scenario: ActivityScenario<MainActivity>,
    vararg permissions: String,
    action: () -> Unit,
) {
    val before = scenario.lastPermissionRequest()
    action()
    waitFor("${permissions.joinToString()} is requested") {
        val request = scenario.lastPermissionRequest()
        request !== before && request?.requestedPermissions?.toList() == permissions.toList()
    }
}

/** Answers MainActivity's latest runtime-permission request as the system dialog would. */
@Suppress("DEPRECATION") // Activity.onRequestPermissionsResult is how the result reaches ActivityResultRegistry.
fun ActivityScenario<MainActivity>.answerPermissionRequest(grant: Boolean) {
    onActivity { activity ->
        val request = shadowOf(activity).lastRequestedPermission ?: throw AssertionError("No permission was requested")
        val permissions = request.requestedPermissions
        if (grant) shadowOf(activity.application).grantPermissions(*permissions)
        val result = if (grant) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED
        activity.onRequestPermissionsResult(request.requestCode, permissions, IntArray(permissions.size) { result })
    }
}

/**
 * Answers the activity MainActivity started for a result last (e.g. the photo picker) with
 * [picked], as if the user chose it; returns the intent that was started.
 */
fun ActivityScenario<MainActivity>.answerPickerWith(picked: Uri): Intent {
    lateinit var started: Intent
    onActivity { activity ->
        val shadow = shadowOf(activity)
        val request = shadow.nextStartedActivityForResult ?: throw AssertionError("No activity was started for a result")
        started = request.intent
        shadow.receiveResult(request.intent, Activity.RESULT_OK, Intent().setData(picked))
    }
    return started
}

/** The text on the clipboard, or null. */
fun clipboardText(): String? {
    val clipboard = ApplicationProvider.getApplicationContext<Context>().getSystemService(ClipboardManager::class.java)
    return clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
}
