package com.ndizigiye.loop.e2e

import android.app.Activity
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.ndizigiye.loop.MainActivity
import com.ndizigiye.loop.api.VideoDto
import com.ndizigiye.loop.e2e.harness.LoopDeviceTest
import com.ndizigiye.loop.e2e.harness.closePictureInPicture
import com.ndizigiye.loop.e2e.harness.currentPlayback
import com.ndizigiye.loop.e2e.harness.currentPlayer
import com.ndizigiye.loop.e2e.harness.currentVideoIdOrNull
import com.ndizigiye.loop.e2e.harness.exists
import com.ndizigiye.loop.e2e.harness.expandPictureInPicture
import com.ndizigiye.loop.e2e.harness.mainActivityWindowConfiguration
import com.ndizigiye.loop.e2e.harness.onBottomTab
import com.ndizigiye.loop.e2e.harness.onCurrentVideo
import com.ndizigiye.loop.e2e.harness.onMainThread
import com.ndizigiye.loop.e2e.harness.pinnedAppTask
import com.ndizigiye.loop.e2e.harness.saveScreenshot
import com.ndizigiye.loop.e2e.harness.shell
import com.ndizigiye.loop.e2e.harness.uiDevice
import com.ndizigiye.loop.e2e.harness.visibleMainActivity
import com.ndizigiye.loop.e2e.harness.visibleMainActivityNodes
import com.ndizigiye.loop.e2e.harness.waitFor
import com.ndizigiye.loop.e2e.harness.waitForText
import com.ndizigiye.loop.e2e.harness.waitForVideo
import com.ndizigiye.loop.e2e.harness.waitUntilPlayedFor
import com.ndizigiye.loop.e2e.harness.waitUntilPlaying
import com.ndizigiye.loop.ui.feed.PLAYBACK_PROGRESS_TAG
import com.ndizigiye.loop.ui.feed.VIDEO_PAGE_TAG_PREFIX
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.time.Duration.Companion.seconds

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
        assumePictureInPicture()
        // Picture-in-picture belongs to the full-screen pager, not the home feed with its tabs.
        val video = server.userVideos("nightowl.beats").first()
        app.launch(deepLink = Uri.parse("loop://video/${video.id}"))
        assertEquals(video.id, compose.waitForVideo())
        compose.waitUntilPlaying()
        assertOverlaysShown(video)
        assertFalse(onMainThread { visibleMainActivity()!!.isInPictureInPictureMode })
        assertNull("no task is pinned yet", pinnedAppTask())
        saveScreenshot("pip-01-pager-playing")

        // Home: the video moves into a picture-in-picture window.
        uiDevice.pressHome()
        val pinned = waitUntilInPictureInPicture()
        Log.i(TAG, "in picture-in-picture: $pinned; window: ${mainActivityWindowConfiguration()}")
        // Only the video shows, and it keeps playing.
        assertOnlyTheVideoShows(video)
        val played = compose.waitUntilPlayedFor(PlayedInWindow)
        Log.i(TAG, "played $played in the window: ${currentPlayback()}")
        assertTrue("still in picture-in-picture", onMainThread { visibleMainActivity()?.isInPictureInPictureMode == true })
        compose.waitUntilPlaying()
        saveScreenshot("pip-02-playing-in-window")

        // Its menu's full-screen button brings the pager back, overlays and all, still playing.
        expandPictureInPicture()
        compose.waitFor("the activity is full screen and resumed again") {
            onMainThread { visibleMainActivity()?.let { !it.isInPictureInPictureMode && it.isResumedNow() } == true }
        }
        compose.waitFor("no task is pinned any more") { pinnedAppTask() == null }
        Log.i(TAG, "restored; window: ${mainActivityWindowConfiguration()}")
        compose.waitFor("the overlays are back") { compose.exists(hasContentDescription("Back")) }
        assertOverlaysShown(video)
        compose.waitUntilPlaying()
        saveScreenshot("pip-03-restored")

        // Leaving again and closing the window stops the video.
        uiDevice.pressHome()
        waitUntilInPictureInPicture()
        assertOnlyTheVideoShows(video)
        compose.waitUntilPlaying()
        val player = onMainThread { currentPlayer() } ?: throw AssertionError("No player in the window")
        saveScreenshot("pip-04-in-window-again")
        closePictureInPicture()
        compose.waitFor("the window is closed and the activity is no longer visible") {
            pinnedAppTask() == null && onMainThread { visibleMainActivity() } == null
        }
        val stopped = waitUntilStopped(player)
        Log.i(TAG, "closed; the player stopped at ${stopped}ms, playWhenReady=${onMainThread { player.playWhenReady }}")
        saveScreenshot("pip-05-window-closed")
    }

    @Test
    fun leavingTheHomeFeedDoesNotEnterPictureInPicture() {
        assumePictureInPicture()
        app.launch()
        compose.waitForVideo()
        // The home feed with its tabs, not the full-screen pager.
        compose.waitForText("For You")
        compose.onBottomTab("Home").assertIsDisplayed()
        assertFalse("the pager's back button shows", compose.exists(hasContentDescription("Back")))
        compose.waitUntilPlaying()
        val player = onMainThread { currentPlayer() } ?: throw AssertionError("No player on screen")
        saveScreenshot("pip-06-home-feed-playing")

        uiDevice.pressHome()
        compose.waitFor("the activity is stopped or in picture-in-picture") {
            onMainThread { visibleMainActivity().let { it == null || it.isInPictureInPictureMode } }
        }
        // The app decides before its activity pauses; still, no window may show up a while later either.
        val until = System.currentTimeMillis() + NO_LATE_WINDOW_MS
        while (System.currentTimeMillis() < until) {
            assertNull("the app's task is pinned", pinnedAppTask())
            assertFalse("in picture-in-picture", onMainThread { startedMainActivity()?.isInPictureInPictureMode == true })
            Thread.sleep(500)
        }
        Log.i(TAG, "home feed left; window: ${mainActivityWindowConfiguration()}")
        waitUntilStopped(player)
        saveScreenshot("pip-07-home-feed-left")
    }

    private fun assumePictureInPicture() {
        assumeTrue(
            "The device has no picture-in-picture (android.software.picture_in_picture)",
            app.application.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE),
        )
    }

    /** Waits until the activity is in picture-in-picture, as it and the window manager see it; returns the pinned task. */
    private fun waitUntilInPictureInPicture(): String {
        compose.waitFor("the activity is in picture-in-picture") {
            onMainThread { visibleMainActivity()?.isInPictureInPictureMode == true }
        }
        var pinned: String? = null
        compose.waitFor("the app's task is pinned") {
            pinned = pinnedAppTask()
            pinned != null
        }
        val window = mainActivityWindowConfiguration()
        assertTrue("the window is pinned: $window", window?.contains("mWindowingMode=pinned") == true)
        return pinned!!
    }

    /**
     * The full-screen pager's overlays on [video] show: the back button, the action rail, the
     * author and caption, and the progress line. Checked with Compose's test API and with
     * [overlaysInWindow], which must find each of them here to be trusted where it finds none.
     */
    private fun assertOverlaysShown(video: VideoDto) {
        compose.onNode(hasContentDescription("Back")).assertIsDisplayed()
        compose.onCurrentVideo(hasContentDescription("Comments")).assertIsDisplayed()
        compose.onCurrentVideo(hasContentDescription("Share")).assertIsDisplayed()
        compose.onCurrentVideo(hasText("@${video.author.username}")).assertIsDisplayed()
        compose.onCurrentVideo(hasText(video.caption)).assertIsDisplayed()
        compose.onCurrentVideo(hasTestTag(PLAYBACK_PROGRESS_TAG)).assertIsDisplayed()
        val found = overlaysInWindow(video)
        Log.i(TAG, "full screen; overlays in the window: $found")
        assertEquals(overlays(video).keys, found)
    }

    /**
     * In the picture-in-picture window, [video]'s page fills the window and none of the
     * overlays exist on any page, nor anything else with a text or a description. Compose's
     * test API sees nothing of a paused activity, so this reads the window's semantics itself.
     */
    private fun assertOnlyTheVideoShows(video: VideoDto) {
        compose.waitFor("the overlays are hidden") { overlaysInWindow(video).isEmpty() }
        assertEquals("the page filling the window", video.id, onMainThread { videoPageFillingWindow() })
        val labelled = onMainThread { visibleMainActivityNodes(hasAnyTextOrDescription).map { it.config.toString() } }
        assertEquals("nodes with a text or a description in the window", emptyList<String>(), labelled)
        Log.i(TAG, "in the window: page ${video.id} fills it; overlays: ${overlaysInWindow(video)}; labelled nodes: $labelled")
    }

    private fun overlays(video: VideoDto): Map<String, SemanticsMatcher> = linkedMapOf(
        "back button" to hasContentDescription("Back"),
        "comments button" to hasContentDescription("Comments"),
        "share button" to hasContentDescription("Share"),
        "author" to hasText("@${video.author.username}"),
        "caption" to hasText(video.caption),
        "progress line" to hasTestTag(PLAYBACK_PROGRESS_TAG),
    )

    /** The names of [video]'s overlays laid out in the app's window, on any page. */
    private fun overlaysInWindow(video: VideoDto): Set<String> = onMainThread {
        overlays(video).filterValues { visibleMainActivityNodes(it).isNotEmpty() }.keys
    }

    /** The id of the video whose page fills the app's window; main thread only. */
    private fun videoPageFillingWindow(): Long? =
        visibleMainActivityNodes(isVideoPage)
            .filter { abs(it.positionInRoot.x) < 1f && abs(it.positionInRoot.y) < 1f && it.size.height > 0 }
            .map { it.config[SemanticsProperties.TestTag].removePrefix(VIDEO_PAGE_TAG_PREFIX).toLong() }
            .singleOrNull()

    /** Waits until [player] is paused and its position stays put; returns that position. */
    private fun waitUntilStopped(player: ExoPlayer): Long {
        var earlier: Long? = null
        var position = 0L
        compose.waitFor("the video is paused") {
            val (playing, wanted, now) = onMainThread { Triple(player.isPlaying, player.playWhenReady, player.currentPosition) }
            val before = earlier
            earlier = now
            position = now
            !playing && !wanted && before == now
        }
        // And stays paused.
        Thread.sleep(STAYS_PAUSED_MS)
        assertFalse("playing again", onMainThread { player.isPlaying })
        assertEquals("the position moved on", position, onMainThread { player.currentPosition })
        return position
    }

    private fun Activity.isResumedNow(): Boolean =
        ActivityLifecycleMonitorRegistry.getInstance().getLifecycleStageOf(this) == Stage.RESUMED

    /** The MainActivity that is started, resumed, paused or stopped; main thread only. */
    private fun startedMainActivity(): MainActivity? {
        val monitor = ActivityLifecycleMonitorRegistry.getInstance()
        return listOf(Stage.RESUMED, Stage.PAUSED, Stage.STOPPED)
            .flatMap { monitor.getActivitiesInStage(it) }
            .filterIsInstance<MainActivity>()
            .firstOrNull()
    }

    /** Opens [link] as another app (or `adb shell am start`) would. */
    private fun openLink(link: String) {
        shell("am start -W -a android.intent.action.VIEW -d $link com.ndizigiye.loop")
    }

    private companion object {
        const val TAG = "PlatformDeviceTest"
        const val STAYS_PAUSED_MS = 2_000L
        const val NO_LATE_WINDOW_MS = 10_000L
        val PlayedInWindow = 3.seconds
        val isVideoPage = SemanticsMatcher("is a video page") {
            it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(VIDEO_PAGE_TAG_PREFIX) == true
        }
        val hasAnyTextOrDescription = SemanticsMatcher("has a text or a content description") {
            SemanticsProperties.Text in it.config || SemanticsProperties.ContentDescription in it.config ||
                SemanticsProperties.EditableText in it.config
        }
    }
}
