package com.ndizigiye.loop.e2e

import android.Manifest
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onChildAt
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.ndizigiye.loop.api.NotificationType
import com.ndizigiye.loop.data.upload.UploadService
import com.ndizigiye.loop.e2e.harness.ForwardingServer
import com.ndizigiye.loop.e2e.harness.LoopE2eTest
import com.ndizigiye.loop.e2e.harness.TestMedia
import com.ndizigiye.loop.e2e.harness.answerPermissionRequest
import com.ndizigiye.loop.e2e.harness.answerPickerWith
import com.ndizigiye.loop.e2e.harness.currentVideoIdOrNull
import com.ndizigiye.loop.e2e.harness.eventually
import com.ndizigiye.loop.e2e.harness.expectPermissionRequest
import com.ndizigiye.loop.e2e.harness.hasRole
import com.ndizigiye.loop.e2e.harness.onCurrentVideo
import com.ndizigiye.loop.e2e.harness.waitFor
import com.ndizigiye.loop.e2e.harness.waitForText
import com.ndizigiye.loop.e2e.harness.waitForVideo
import com.ndizigiye.loop.e2e.harness.waitUntilExists
import com.ndizigiye.loop.e2e.harness.waitUntilGone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.Shadows.shadowOf
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Feature 15 as far as it runs on the JVM: the camera's permission flow, picking a video
 * from the gallery, and posting it with a caption, hashtag and mention. Recording itself
 * (CameraX) is left to the emulator.
 */
class CreateE2eTest : LoopE2eTest() {

    @Test
    fun cameraExplainsMissingPermissionsAndAsksAgain() {
        app.signIn(server.register())
        val scenario = app.launch()
        compose.waitForVideo()

        compose.expectPermissionRequest(scenario, *CapturePermissions) {
            compose.onNode(hasContentDescription("Create")).performClick()
        }
        scenario.answerPermissionRequest(grant = false)
        compose.waitForText("Record with Loop")
        compose.waitUntilExists(hasText("Upload from gallery") and hasClickAction())

        compose.expectPermissionRequest(scenario, *CapturePermissions) {
            compose.onNode(hasText("Allow access") and hasRole(Role.Button)).performClick()
        }
        scenario.answerPermissionRequest(grant = false)
        compose.waitForText("Record with Loop")

        compose.onNode(hasContentDescription("Close")).performClick()
        compose.waitFor("the camera closes") { compose.currentVideoIdOrNull() != null }
    }

    @Test
    fun postsAPickedVideoWithProgressAndItShowsOnProfileFeedsAndItsHashtag() {
        val me = server.register("Poster")
        val follower = server.register("Follower")
        server.follow(follower, me.username)
        // The server's answer to the upload is held back, so the progress stays on screen meanwhile.
        val proxy = app.closeAfterTest(
            ForwardingServer(server.baseUrl) { request ->
                if (request.method == "POST" && request.path == "/api/videos") 2.seconds else Duration.ZERO
            },
        )
        proxy.start()
        app.container.sessionStore.setBaseUrl(proxy.url)
        app.signIn(me)
        val scenario = app.launch()
        compose.waitForVideo()

        compose.expectPermissionRequest(scenario, *CapturePermissions) {
            compose.onNode(hasContentDescription("Create")).performClick()
        }
        scenario.answerPermissionRequest(grant = false)
        compose.onNode(hasText("Upload from gallery") and hasClickAction()).performClick()
        val picker = scenario.answerPickerWith(Uri.fromFile(TestMedia.video))
        assertEquals(MediaStore.ACTION_PICK_IMAGES, picker.action)
        compose.waitForText("Original sound")

        val hashtag = "e2e" + List(8) { ('a'..'z').random() }.joinToString("")
        val captionField = compose.onNode(hasSetTextAction())
        captionField.performTextInput("Posted by the e2e suite")
        compose.onNode(hasText("# Hashtags") and hasClickAction()).performClick()
        captionField.performTextInput(hashtag)
        compose.onNode(hasText("@ Mention") and hasClickAction()).performClick()
        captionField.performTextInput(follower.username)
        val caption = "Posted by the e2e suite #$hashtag @${follower.username}"
        captionField.assert(hasText(caption))

        // Android 13+: the upload's progress notification needs permission first.
        compose.expectPermissionRequest(scenario, Manifest.permission.POST_NOTIFICATIONS) {
            compose.onNode(hasText("Post") and hasRole(Role.Button)).performClick()
        }
        scenario.answerPermissionRequest(grant = true)
        compose.waitForText("Processing...")
        compose.waitUntilExists(hasProgressBarRangeInfo(ProgressBarRangeInfo(1f, 0f..1f)))
        val service = shadowOf(app.application).nextStartedService
        assertEquals(UploadService::class.java.name, service?.component?.className)

        compose.waitForText("Posted!")
        compose.waitForText("@${me.username}")
        val thumbnail = hasContentDescription(caption) and hasClickAction()
        compose.waitUntilExists(thumbnail)

        val posted = compose.eventually { server.userVideos(me.username).single() }
        assertEquals(caption, posted.caption)
        assertEquals(listOf(hashtag), posted.hashtags)
        assertTrue(posted.durationMs > 0)
        assertTrue("in the follower's Following feed", server.followingFeed(follower).any { it.id == posted.id })
        assertTrue(
            "the follower was notified of the mention",
            server.notifications(follower).any { it.type == NotificationType.MENTION && it.video?.id == posted.id },
        )

        // The parsed hashtag links the caption to the hashtag's page, which lists the video.
        compose.onNode(thumbnail).performClick()
        compose.waitFor("the pager shows the new video") { compose.currentVideoIdOrNull() == posted.id }
        // The "Posted!" snackbar covers the caption until it times out.
        compose.waitUntilGone(hasText("Posted!"))
        // Links in a text are its child nodes, in text order: the #hashtag, then the @mention.
        compose.onCurrentVideo(hasText(caption)).onChildAt(0).performClick()
        compose.waitForText("1 video", substring = true)
        compose.waitUntilExists(hasText("#$hashtag"))
        compose.waitUntilExists(thumbnail)
    }

    private companion object {
        val CapturePermissions = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
    }
}
