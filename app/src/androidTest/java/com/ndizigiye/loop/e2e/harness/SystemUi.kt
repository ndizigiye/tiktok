package com.ndizigiye.loop.e2e.harness

import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiObject2

// The system's own screens, driven with UiAutomator.

private const val PERMISSION_CONTROLLER = "com.android.permissioncontroller"

private val AllowWhileInUse = By.res(PERMISSION_CONTROLLER, "permission_allow_foreground_only_button")
private val PermissionMessage = By.res(PERMISSION_CONTROLLER, "permission_message")

/**
 * Answers [count] runtime-permission dialogs of the system in a row with "While using the
 * app", taking a screenshot named [screenshotPrefix]-N of each. The app keeps running
 * meanwhile (its screen may still have to open and ask), though Compose can't see its
 * windows while the dialog has the focus.
 */
fun ComposeTestRule.allowPermissionDialogs(count: Int, screenshotPrefix: String) {
    repeat(count) { index ->
        var allow: UiObject2? = null
        waitFor("permission dialog ${index + 1} of $count appears") {
            allow = uiDevice.findObject(AllowWhileInUse)
            allow != null
        }
        val message = uiDevice.findObject(PermissionMessage)?.text
        saveScreenshot("$screenshotPrefix-${index + 1}")
        allow!!.click()
        // The next request reuses the dialog with another message.
        waitFor("permission dialog ${index + 1} is answered") {
            message == null || !uiDevice.hasObject(By.text(message))
        }
    }
}
