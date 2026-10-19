package com.ndizigiye.loop.e2e.harness

import android.app.Activity
import android.graphics.Rect
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.test.SemanticsMatcher
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.UiDevice
import com.ndizigiye.loop.MainActivity

// The device around the app: shell commands (as the shell user, like adb shell), screenshots
// of the whole display, UiAutomator for system UI, the cameras in use, and the activity on
// screen however it was started.

private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

val uiDevice: UiDevice get() = UiDevice.getInstance(instrumentation)

/** Runs [command] as the shell user and returns its standard output. */
fun shell(command: String): String {
    val output = instrumentation.uiAutomation.executeShellCommand(command)
    return ParcelFileDescriptor.AutoCloseInputStream(output).bufferedReader().use { it.readText() }
}

/** Log tag of [saveScreenshot]'s markers; scripts/device-screenshots.sh captures the display for each. */
const val SCREENSHOT_LOG_TAG = "LoopE2eScreenshot"

/**
 * Where scripts/device-screenshots.sh says it is watching (file `.watching`) and confirms each
 * screenshot it took (a file with the screenshot's name).
 */
private const val SCREENSHOT_ACK_DIR = "/data/local/tmp/loop-e2e-screenshots"

private const val SCREENSHOT_ACK_TIMEOUT_MS = 20_000L

/**
 * Asks for a screenshot named [name] of the display as it is now, video surfaces included.
 * It is taken from the host by scripts/device-screenshots.sh, which follows the log, because
 * capturing on a software-emulated device takes over a minute while video plays. While the
 * script runs, this waits until it confirms the picture, so the screen is captured before the
 * test moves on; without it, this only logs the step.
 */
fun saveScreenshot(name: String) {
    val ack = "$SCREENSHOT_ACK_DIR/$name"
    val watching = deviceFileExists("$SCREENSHOT_ACK_DIR/.watching")
    if (watching) shell("rm -f $ack")
    Log.i(SCREENSHOT_LOG_TAG, name)
    if (!watching) return
    val deadline = System.currentTimeMillis() + SCREENSHOT_ACK_TIMEOUT_MS
    while (!deviceFileExists(ack) && System.currentTimeMillis() < deadline) Thread.sleep(100)
}

/** Whether [path] exists on the device, as the shell user sees it. */
private fun deviceFileExists(path: String): Boolean = shell("ls $path").trim() == path

/**
 * The lens facing ([CameraCharacteristics.LENS_FACING] values) of each camera that is open
 * right now, e.g. by the recorder, as the camera service reports it.
 */
fun openCameraFacings(): Set<Int> {
    val cameras = instrumentation.targetContext.getSystemService(CameraManager::class.java)
    val open = mutableSetOf<String>()
    val callback = object : CameraManager.AvailabilityCallback() {
        override fun onCameraAvailable(cameraId: String) {
            open -= cameraId
        }

        override fun onCameraUnavailable(cameraId: String) {
            open += cameraId
        }
    }
    // Registering reports each camera's current state at once, here with a direct executor.
    cameras.registerAvailabilityCallback(Runnable::run, callback)
    cameras.unregisterAvailabilityCallback(callback)
    return open.mapTo(mutableSetOf()) { id -> cameras.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING)!! }
}

/** Runs [block] on the main thread and returns its result. */
fun <T> onMainThread(block: () -> T): T {
    var result: Result<T>? = null
    instrumentation.runOnMainSync { result = runCatching(block) }
    return result!!.getOrThrow()
}

/** The MainActivity that is resumed, or paused (as in picture-in-picture); main thread only. */
fun visibleMainActivity(): MainActivity? {
    val monitor = ActivityLifecycleMonitorRegistry.getInstance()
    return (monitor.getActivitiesInStage(Stage.RESUMED) + monitor.getActivitiesInStage(Stage.PAUSED))
        .filterIsInstance<MainActivity>()
        .firstOrNull()
}

/**
 * The semantics nodes of the visible MainActivity's window that match [matcher] and are laid
 * out, read straight from its Compose root. Compose's test API only looks at windows whose
 * lifecycle is resumed, so it sees nothing at all of an activity that is paused in
 * picture-in-picture; this does. Main thread only.
 */
fun visibleMainActivityNodes(matcher: SemanticsMatcher, useUnmergedTree: Boolean = true): List<SemanticsNode> {
    val activity = visibleMainActivity() ?: return emptyList()
    return activity.findViews<ViewRootForTest>()
        .flatMap { root -> root.semanticsOwner.getAllSemanticsNodes(mergingEnabled = !useUnmergedTree) }
        .filter { node -> matcher.matches(node) && node.isPlaced }
}

/** A pager keeps pages it no longer shows for reuse, unplaced. */
private val SemanticsNode.isPlaced: Boolean
    get() = generateSequence(layoutInfo) { it.parentInfo }.all { it.isPlaced && !it.isDeactivated }

/** Where the visible MainActivity's window is on the display; main thread only. */
fun visibleMainActivityBounds(): Rect? {
    val decor = visibleMainActivity()?.window?.decorView ?: return null
    val location = IntArray(2).also { decor.getLocationOnScreen(it) }
    return Rect(location[0], location[1], location[0] + decor.width, location[1] + decor.height)
}

private const val APP_PACKAGE = "com.ndizigiye.loop"

/**
 * The app's task as `dumpsys activity activities` lists it (its `Task{...}` line) while the
 * window manager runs it in the pinned windowing mode, i.e. in picture-in-picture; otherwise null.
 */
fun pinnedAppTask(): String? =
    shell("dumpsys activity activities").lineSequence()
        .map { it.trim() }
        .firstOrNull { line -> "Task{" in line && "mode=pinned" in line && ":$APP_PACKAGE " in line }

/**
 * The window configuration of MainActivity's window as `dumpsys window windows` reports it
 * (`winConfig={ mBounds=... mWindowingMode=... }`), or null when it has no window.
 */
fun mainActivityWindowConfiguration(): String? {
    val dump = shell("dumpsys window windows")
    val window = dump.indexOf("$APP_PACKAGE/$APP_PACKAGE.MainActivity}:")
    if (window < 0) return null
    val start = dump.indexOf("winConfig={", window)
    if (start < 0) return null
    return dump.substring(start, dump.indexOf('}', start) + 1)
}

/** Every view of [activity]'s window that is a [T], depth first. */
inline fun <reified T> Activity.findViews(): List<T> {
    val found = mutableListOf<T>()
    val pending = ArrayDeque<android.view.View>().apply { add(window.decorView) }
    while (pending.isNotEmpty()) {
        val view = pending.removeFirst()
        if (view is T) found += view
        if (view is android.view.ViewGroup) for (i in 0 until view.childCount) pending += view.getChildAt(i)
    }
    return found
}
