package com.ndizigiye.loop.e2e

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.ndizigiye.loop.e2e.harness.LoopDeviceTest
import com.ndizigiye.loop.e2e.harness.awaitRepeat
import com.ndizigiye.loop.e2e.harness.currentPlayback
import com.ndizigiye.loop.e2e.harness.currentPlayer
import com.ndizigiye.loop.e2e.harness.onCurrentVideoPage
import com.ndizigiye.loop.e2e.harness.onMainThread
import com.ndizigiye.loop.e2e.harness.saveScreenshot
import com.ndizigiye.loop.e2e.harness.swipeToNextVideo
import com.ndizigiye.loop.e2e.harness.swipeToPreviousVideo
import com.ndizigiye.loop.e2e.harness.waitFor
import com.ndizigiye.loop.e2e.harness.waitForVideo
import com.ndizigiye.loop.e2e.harness.waitUntilExists
import com.ndizigiye.loop.e2e.harness.waitUntilGone
import com.ndizigiye.loop.e2e.harness.waitUntilPaused
import com.ndizigiye.loop.e2e.harness.waitUntilPlaying
import com.ndizigiye.loop.ui.feed.VIDEO_THUMBNAIL_TAG
import com.ndizigiye.loop.ui.feed.videoPageTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Feature 4 (and the playing half of 2): real video playback in the For You feed. */
class PlaybackDeviceTest : LoopDeviceTest() {

    @Test
    fun forYouVideoPlaysInPlaceOfItsThumbnailAndLoops() {
        app.launch()
        val videoId = compose.waitForVideo()
        saveScreenshot("playback-01-for-you-launched")

        val playing = compose.waitUntilPlaying()
        assertEquals(Player.REPEAT_MODE_ONE, playing.repeatMode)
        assertTrue("the video has a duration", playing.durationMs > 0)
        // The thumbnail gives way to the video once its first frame is on screen.
        compose.waitFor("the thumbnail of video $videoId is gone") {
            compose.onAllNodes(thumbnailOf(videoId), useUnmergedTree = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty()
        }

        // Two looks at the picture a few seconds apart, for comparing the frames.
        saveScreenshot("playback-02-playing-first-look")
        val firstLook = currentPlayback()!!
        Thread.sleep(FRAME_GAP_MS)
        saveScreenshot("playback-03-playing-second-look")
        val secondLook = currentPlayback()!!
        assertTrue(
            "new frames were rendered in between ($firstLook, $secondLook)",
            secondLook.renderedFrames > firstLook.renderedFrames,
        )

        val player = onMainThread { currentPlayer() } ?: throw AssertionError("No player on screen")
        assertTrue("the video starts over at its end", awaitRepeat(player))
        assertEquals(videoId, compose.waitForVideo())
        compose.waitUntilPlaying()
    }

    @Test
    fun tapPausesAndResumesAndTheProgressBarScrubs() {
        app.launch()
        compose.waitForVideo()
        compose.waitUntilPlaying()

        compose.onCurrentVideoPage().performTouchInput { click(center) }
        compose.waitUntilExists(hasContentDescription("Paused"))
        val paused = compose.waitUntilPaused()
        saveScreenshot("playback-04-tap-paused")

        // A horizontal drag along the progress line at the bottom of the page seeks; the
        // page sits right above the bottom bar, which takes the navigation bar's inset.
        compose.onCurrentVideoPage().performTouchInput {
            val y = bottom - 12.dp.toPx()
            swipe(Offset(width * 0.1f, y), Offset(width * 0.8f, y), durationMillis = 1_000)
        }
        compose.waitFor("the player seeks to about 80 %") {
            val position = currentPlayback()?.positionMs ?: 0L
            position in (paused.durationMs * 6 / 10)..(paused.durationMs * 95 / 100)
        }
        assertFalse("still paused after scrubbing", currentPlayback()!!.isPlaying)
        saveScreenshot("playback-05-scrubbed-while-paused")

        compose.onCurrentVideoPage().performTouchInput { click(center) }
        compose.waitUntilGone(hasContentDescription("Paused"))
        compose.waitUntilPlaying()
        saveScreenshot("playback-06-tap-resumed")
    }

    @Test
    fun swipingToTheNextVideoPlaysItAndPausesThePreviousOne() {
        app.launch()
        val first = compose.waitForVideo()
        compose.waitUntilPlaying()
        val firstPlayer = onMainThread { currentPlayer() }

        val second = compose.swipeToNextVideo()
        assertTrue(second != first)
        compose.waitUntilPlaying()
        assertNotSame(firstPlayer, onMainThread { currentPlayer() })
        assertFalse("the previous video is paused", onMainThread { firstPlayer!!.playWhenReady })
        saveScreenshot("playback-07-next-video-playing")

        assertEquals(first, compose.swipeToPreviousVideo())
        compose.waitUntilPlaying()
        saveScreenshot("playback-08-previous-video-playing")
    }

    private fun thumbnailOf(videoId: Long) = hasTestTag(VIDEO_THUMBNAIL_TAG) and hasAnyAncestor(hasTestTag(videoPageTag(videoId)))

    private companion object {
        const val FRAME_GAP_MS = 3_000L
    }
}
