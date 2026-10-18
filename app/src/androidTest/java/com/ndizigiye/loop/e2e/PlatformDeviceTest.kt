package com.ndizigiye.loop.e2e

import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import com.ndizigiye.loop.e2e.harness.LoopDeviceTest
import com.ndizigiye.loop.e2e.harness.currentVideoIdOrNull
import com.ndizigiye.loop.e2e.harness.exists
import com.ndizigiye.loop.e2e.harness.onCurrentVideo
import com.ndizigiye.loop.e2e.harness.onMainThread
import com.ndizigiye.loop.e2e.harness.saveScreenshot
import com.ndizigiye.loop.e2e.harness.shell
import com.ndizigiye.loop.e2e.harness.uiDevice
import com.ndizigiye.loop.e2e.harness.visibleMainActivity
import com.ndizigiye.loop.e2e.harness.waitFor
import com.ndizigiye.loop.e2e.harness.waitForText
import com.ndizigiye.loop.e2e.harness.waitForVideo
import com.ndizigiye.loop.e2e.harness.waitUntilPlaying
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Features 19 and 20 on a device: picture-in-picture on leaving the app, and `loop://video/{id}` links from the shell. */
class PlatformDeviceTest : LoopDeviceTest() {

    @Test
    fun deepLinkFromTheShellOpensTheVideoAndPlaysIt() {
        val video = server.userVideos("mira.makes").first()

        openLink("loop://video/${video.id}")
        assertEquals(video.id, compose.waitForVideo())
        compose.onCurrentVideo(hasText("@mira.makes")).assertIsDisplayed()
        compose.onCurrentVideo(hasText(video.caption)).assertIsDisplayed()
        compose.waitUntilPlaying()
        saveScreenshot("deeplink-01-cold-start-video")

        // Back leads to the app's home feed.
        compose.onNode(hasContentDescription("Back")).performClick()
        compose.waitFor("the home feed shows another video") { compose.currentVideoIdOrNull().let { it != null && it != video.id } }
        compose.waitForText("For You")
    }

    @Test
    fun deepLinkReachesTheRunningApp() {
        val video = server.userVideos("tobi_trails").first()
        app.launch()
        val first = compose.waitForVideo()

        // MainActivity is singleTop: the link arrives as a new intent.
        openLink("loop://video/${video.id}")
        compose.waitFor("the linked video is shown") { compose.currentVideoIdOrNull() == video.id }
        compose.onCurrentVideo(hasText(video.caption)).assertIsDisplayed()
        saveScreenshot("deeplink-02-running-app-video")

        compose.onNode(hasContentDescription("Back")).performClick()
        compose.waitFor("the feed is back") { compose.currentVideoIdOrNull() == first }
    }

    @Test
    fun leavingTheAppWhileAVideoPlaysContinuesItInPictureInPicture() {
        val packageManager = app.application.packageManager
        assumeTrue(
            "The device has no picture-in-picture (android.software.picture_in_picture)",
            packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE),
        )
        // Picture-in-picture belongs to the full-screen pager, not the home feed with its tabs.
        val video = server.userVideos("nightowl.beats").first()
        app.launch(deepLink = Uri.parse("loop://video/${video.id}"))
        assertEquals(video.id, compose.waitForVideo())
        compose.waitUntilPlaying()

        uiDevice.pressHome()
        compose.waitFor("the activity is in picture-in-picture") {
            onMainThread { visibleMainActivity()?.isInPictureInPictureMode == true }
        }
        // Only the video shows, and it keeps playing.
        compose.waitFor("the overlays are hidden") { !compose.exists(hasContentDescription("Comments")) }
        compose.waitUntilPlaying()
        saveScreenshot("pip-01-playing-in-window")
    }

    /** Opens [link] as another app (or `adb shell am start`) would. */
    private fun openLink(link: String) {
        shell("am start -W -a android.intent.action.VIEW -d $link com.ndizigiye.loop")
    }
}
