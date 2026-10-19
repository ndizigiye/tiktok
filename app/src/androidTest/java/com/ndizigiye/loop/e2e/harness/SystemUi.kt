package com.ndizigiye.loop.e2e.harness

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.UiAutomation
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

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

private const val SYSTEM_UI = "com.android.systemui"

/**
 * How long a picture-in-picture window's menu may take to show after a tap: on a
 * software-emulated device its activity takes several seconds to start (and it hides itself
 * again a few seconds later).
 */
private val PipMenuTimeout = 15.seconds

private const val PIP_MENU_ATTEMPTS = 4

/** Settings.Secure.ACCESSIBILITY_INTERACTIVE_UI_TIMEOUT_MS: "Time to take action" in the accessibility settings. */
private const val INTERACTIVE_UI_TIMEOUT = "accessibility_interactive_ui_timeout_ms"

private val PipMenuTimeToTakeAction = 30.seconds

/** Restores the app from its picture-in-picture window as a user does: a tap opens the window's menu, whose middle button goes full screen. */
fun expandPictureInPicture() = pressPictureInPictureMenuButton("expand_button", "full-screen")

/** Closes the app's picture-in-picture window as a user does: a tap opens the window's menu, which has a close button. */
fun closePictureInPicture() = pressPictureInPictureMenuButton("dismiss", "close")

/**
 * Taps the picture-in-picture window and presses the button [buttonId] of the system UI's
 * menu that opens, with an accessibility click, as TalkBack would, until the app's task is
 * no longer pinned; when the menu came and went first, it taps the window again.
 *
 * The menu hides itself a few seconds after it was asked to show, which on a
 * software-emulated device leaves it on screen for a second or two: meanwhile the
 * accessibility setting "Time to take action" (which that delay follows) is raised. And
 * UiAutomator's searches walk every window's whole tree, the app's too, which takes longer
 * than that; this asks each window for the button by its id instead.
 */
private fun pressPictureInPictureMenuButton(buttonId: String, name: String) {
    val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
    automation.serviceInfo = automation.serviceInfo.apply { flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS }
    val timeToTakeAction = shell("settings get secure $INTERACTIVE_UI_TIMEOUT").trim()
    shell("settings put secure $INTERACTIVE_UI_TIMEOUT ${PipMenuTimeToTakeAction.inWholeMilliseconds}")
    try {
        pressPictureInPictureMenuButton(automation, "$SYSTEM_UI:id/$buttonId", name)
    } finally {
        if (timeToTakeAction.isEmpty() || timeToTakeAction == "null") {
            shell("settings delete secure $INTERACTIVE_UI_TIMEOUT")
        } else {
            shell("settings put secure $INTERACTIVE_UI_TIMEOUT $timeToTakeAction")
        }
    }
}

private fun pressPictureInPictureMenuButton(automation: UiAutomation, viewId: String, name: String) {
    repeat(PIP_MENU_ATTEMPTS) {
        val window = onMainThread { visibleMainActivityBounds() } ?: throw AssertionError("The app has no window on screen")
        uiDevice.click(window.centerX(), window.top + window.height() * 4 / 5)
        val tapped = TimeSource.Monotonic.markNow()
        while (tapped.elapsedNow() < PipMenuTimeout) {
            val button = automation.windows
                .mapNotNull { it.root }
                .filter { it.packageName == SYSTEM_UI }
                .flatMap { it.findAccessibilityNodeInfosByViewId(viewId) }
                .firstOrNull { it.isVisibleToUser && it.isEnabled }
            if (button != null && button.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                // A press as the menu fades out does nothing; then the window is still pinned.
                val pressed = TimeSource.Monotonic.markNow()
                while (pressed.elapsedNow() < PipMenuTimeout) {
                    if (pinnedAppTask() == null) return
                    Thread.sleep(200)
                }
                break
            }
            Thread.sleep(50)
        }
        // Let a menu that is still on its way show and hide again before the next tap.
        uiDevice.wait(Until.gone(By.res(SYSTEM_UI, "menu_container")), PipMenuTimeout.inWholeMilliseconds)
    }
    throw AssertionError("The picture-in-picture menu's $name button could not be pressed in $PIP_MENU_ATTEMPTS attempts")
}
