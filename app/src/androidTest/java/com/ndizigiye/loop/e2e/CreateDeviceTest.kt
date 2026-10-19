package com.ndizigiye.loop.e2e

import android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK
import android.hardware.camera2.CameraCharacteristics.LENS_FACING_FRONT
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.ndizigiye.loop.api.NotificationType
import com.ndizigiye.loop.e2e.harness.ForwardingServer
import com.ndizigiye.loop.e2e.harness.LoopDeviceTest
import com.ndizigiye.loop.e2e.harness.allowPermissionDialogs
import com.ndizigiye.loop.e2e.harness.eventually
import com.ndizigiye.loop.e2e.harness.exists
import com.ndizigiye.loop.e2e.harness.hasRole
import com.ndizigiye.loop.e2e.harness.onMainThread
import com.ndizigiye.loop.e2e.harness.openCameraFacings
import com.ndizigiye.loop.e2e.harness.saveScreenshot
import com.ndizigiye.loop.e2e.harness.shell
import com.ndizigiye.loop.e2e.harness.uiDevice
import com.ndizigiye.loop.e2e.harness.visibleMainActivity
import com.ndizigiye.loop.e2e.harness.waitFor
import com.ndizigiye.loop.e2e.harness.waitForText
import com.ndizigiye.loop.e2e.harness.waitForVideo
import com.ndizigiye.loop.e2e.harness.waitUntilExists
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Feature 15 and 16 on a device: the camera's permission dialogs, a lens flip, a take
 * recorded from the (emulated) camera in two segments, the first after the timer's
 * countdown, posted with a caption, hashtag and mention; the upload carries on in its
 * foreground service while the app is in the background, and the video lands on the
 * profile and on the server.
 *
 * Expects the camera and microphone permissions not to be granted yet, as after installing.
 */
class CreateDeviceTest : LoopDeviceTest() {

    @Test
    fun recordsTwoSegmentsAndPostsThemWhileTheAppIsInTheBackground() {
        val me = server.register("Device Creator")
        val friend = server.register("Mentioned Friend")
        server.follow(friend, me.username)
        // The server's answer to the upload is held back, so the upload is still running
        // (being processed) while the app is in the background.
        val proxy = app.closeAfterTest(
            ForwardingServer(server.baseUrl) { request ->
                if (request.method == "POST" && request.path == "/api/videos") UploadHold else Duration.ZERO
            },
        )
        proxy.start()
        app.container.sessionStore.setBaseUrl(proxy.url)
        app.signIn(me)
        app.launch()
        compose.waitForVideo()

        // The recorder opens and asks for the camera, then for the microphone.
        compose.onNode(hasContentDescription("Create")).performClick()
        compose.allowPermissionDialogs(count = 2, screenshotPrefix = "create-01-permission-dialog")
        compose.waitUntilExists(record, CameraTimeout)
        compose.waitFor("the front camera is open", CameraTimeout) { openCameraFacings() == setOf(LENS_FACING_FRONT) }
        saveScreenshot("create-02-camera-front")

        // The emulator has a front and a back camera.
        val flip = hasText("Flip") and hasClickAction() and isEnabled()
        compose.waitUntilExists(flip)
        compose.onNode(flip).performClick()
        compose.waitFor("the back camera is open", CameraTimeout) { openCameraFacings() == setOf(LENS_FACING_BACK) }
        compose.waitUntilExists(record, CameraTimeout)
        saveScreenshot("create-03-camera-flipped")

        // First segment after the 3-second timer.
        compose.onNode(hasText("Timer") and hasClickAction()).performClick()
        compose.waitForText("3s")
        recordAfterCountdown()
        recordSegment()
        saveScreenshot("create-05-first-segment-paused")

        // Second segment of the same take, timer off.
        compose.onNode(hasText("3s") and hasClickAction()).performClick()
        compose.waitForText("Timer")
        compose.onNode(record).performClick()
        recordSegment()
        saveScreenshot("create-06-second-segment-paused")

        compose.onNode(hasContentDescription("Next") and hasClickAction()).performClick()
        compose.waitForText("Original sound", timeout = CameraTimeout)

        val hashtag = "device" + List(8) { ('a'..'z').random() }.joinToString("")
        val captionField = compose.onNode(hasSetTextAction())
        captionField.performTextInput("Recorded on the emulator")
        compose.onNode(hasText("# Hashtags") and hasClickAction()).performClick()
        captionField.performTextInput(hashtag)
        compose.onNode(hasText("@ Mention") and hasClickAction()).performClick()
        captionField.performTextInput(friend.username)
        val caption = "Recorded on the emulator #$hashtag @${friend.username}"
        saveScreenshot("create-07-post-screen")

        compose.onNode(hasText("Post") and hasRole(Role.Button)).performClick()
        // The whole file is sent; the (held) server answer is pending.
        compose.waitForText("Processing...")
        saveScreenshot("create-08-upload-processing")
        assertTrue("the upload service runs in the foreground", uploadServiceRecord()?.contains("isForeground=true") == true)

        uiDevice.pressHome()
        compose.waitFor("the app is in the background") { onMainThread { visibleMainActivity() } == null }
        saveScreenshot("create-09-home-while-uploading")
        val inBackground = uploadServiceRecord()
        assertTrue("the upload service is still in the foreground: $inBackground", inBackground?.contains("isForeground=true") == true)

        val posted = compose.eventually(UploadHold + CameraTimeout) { server.userVideos(me.username).single() }
        compose.waitFor("the upload service has stopped") { uploadServiceRecord() == null }
        assertEquals(caption, posted.caption)
        assertEquals(listOf(hashtag), posted.hashtags)
        assertTrue("the take has a duration (${posted.durationMs} ms)", posted.durationMs > 0)
        assertTrue("in the follower's Following feed", server.followingFeed(friend).any { it.id == posted.id })
        assertTrue(
            "the mentioned account was notified",
            server.notifications(friend).any { it.type == NotificationType.MENTION && it.video?.id == posted.id },
        )

        // Back in the app, the post screen has handed over to the profile, which lists the video.
        shell("am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n com.ndizigiye.loop/.MainActivity")
        compose.waitForText("@${me.username}")
        compose.waitUntilExists(hasContentDescription(caption) and hasClickAction())
        saveScreenshot("create-10-profile-with-new-video")
    }

    /**
     * Presses Record with the timer on and checks that the countdown shows instead of a
     * recording. The countdown runs in real time, quicker than a slow device settles, so
     * the test clock holds the screen at the first frame after the press.
     */
    private fun recordAfterCountdown() {
        compose.mainClock.autoAdvance = false
        try {
            compose.onNode(record).performClick()
            compose.mainClock.advanceTimeByFrame()
            saveScreenshot("create-04-timer-countdown")
            assertTrue("the timer counts down", compose.exists(hasText("3") or hasText("2") or hasText("1")))
            assertFalse("no recording during the countdown", compose.exists(hasContentDescription("Pause recording")))
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    /** Records for a few seconds, then pauses; the take can then be finished. */
    private fun recordSegment() {
        compose.waitUntilExists(hasContentDescription("Pause recording"), CameraTimeout)
        Thread.sleep(SEGMENT_MS)
        compose.onNode(hasContentDescription("Pause recording")).performClick()
        compose.waitUntilExists(hasContentDescription("Next") and hasClickAction(), CameraTimeout)
    }

    /** The activity manager's record of the upload service while it runs (`dumpsys`), else null. */
    private fun uploadServiceRecord(): String? {
        // The dump also shows other services' records, e.g. the last one that was not responding.
        val lines = shell("dumpsys activity services $UPLOAD_SERVICE").lines()
        val start = lines.indexOfFirst { "ServiceRecord{" in it && it.trimEnd().endsWith("$UPLOAD_SERVICE}") }
        if (start < 0) return null
        val details = lines.drop(start + 1).takeWhile { it.isNotBlank() && "ServiceRecord{" !in it }
        return (listOf(lines[start]) + details).joinToString("\n")
    }

    private val record = hasContentDescription("Record") and isEnabled()

    private companion object {
        const val SEGMENT_MS = 4_000L
        const val UPLOAD_SERVICE = "com.ndizigiye.loop/.data.upload.UploadService"
        val CameraTimeout = 120.seconds
        // Below the app's 30-second read timeout, with room for the background checks.
        val UploadHold = 20.seconds
    }
}
