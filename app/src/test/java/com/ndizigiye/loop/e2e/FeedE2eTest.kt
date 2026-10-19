package com.ndizigiye.loop.e2e

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import com.ndizigiye.loop.api.ApiLimits
import com.ndizigiye.loop.e2e.harness.LoopE2eTest
import com.ndizigiye.loop.e2e.harness.currentVideoId
import com.ndizigiye.loop.e2e.harness.currentVideoIdOrNull
import com.ndizigiye.loop.e2e.harness.eventually
import com.ndizigiye.loop.e2e.harness.hasRole
import com.ndizigiye.loop.e2e.harness.onCurrentVideo
import com.ndizigiye.loop.e2e.harness.onCurrentVideoPage
import com.ndizigiye.loop.e2e.harness.swipeToNextVideo
import com.ndizigiye.loop.e2e.harness.swipeToPreviousVideo
import com.ndizigiye.loop.e2e.harness.waitFor
import com.ndizigiye.loop.e2e.harness.waitForText
import com.ndizigiye.loop.e2e.harness.waitForVideo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Features 2 and 3: the For You and Following feeds. */
class FeedE2eTest : LoopE2eTest() {

    @Test
    fun forYouShowsSeededVideosAndSwipesBetweenThem() {
        launchSignedIn()
        val first = compose.waitForVideo()
        assertShowsVideo(first)

        val second = compose.swipeToNextVideo()
        assertNotEquals(first, second)
        assertShowsVideo(second)

        assertEquals(first, compose.swipeToPreviousVideo())
        assertShowsVideo(first)
    }

    @Test
    fun forYouLoadsTheNextPageNearTheEnd() {
        launchSignedIn()
        val seen = mutableListOf(compose.waitForVideo())
        // One page holds ApiLimits.DEFAULT_PAGE_SIZE videos; seeing more means the next page loaded.
        repeat(ApiLimits.DEFAULT_PAGE_SIZE + 2) { seen += compose.swipeToNextVideo() }
        assertTrue("distinct videos seen: $seen", seen.distinct().size > ApiLimits.DEFAULT_PAGE_SIZE)
    }

    @Test
    fun pullToRefreshReloadsForYou() {
        val me = launchSignedIn()
        val first = compose.waitForVideo()
        // Hidden on the server, so the refreshed list can't start with it again.
        server.notInterested(me, first)

        compose.onCurrentVideoPage().performTouchInput { swipeDown(startY = height * 0.2f, endY = height * 0.8f) }
        compose.waitFor("the refreshed feed shows another video") {
            compose.currentVideoIdOrNull().let { it != null && it != first }
        }
        assertShowsVideo(compose.currentVideoId())
    }

    @Test
    fun followingAsksSignedOutViewersToLogIn() {
        app.launch()
        compose.waitForVideo()
        openFollowingTab()
        compose.waitForText("Follow accounts to see their videos here")
        compose.onNode(hasText("Log in") and hasRole(Role.Button)).performClick()
        compose.waitForText("Watch, make and share short videos.")
    }

    @Test
    fun followingIsEmptyUntilTheViewerFollowsACreator() {
        val me = launchSignedIn()
        val videoId = compose.waitForVideo()
        val creator = server.video(videoId).author

        openFollowingTab()
        compose.waitForText("No videos from accounts you follow yet")
        compose.onNode(hasText("For You") and hasRole(Role.Tab)).performClick()
        compose.waitFor("For You is shown again") { compose.currentVideoIdOrNull() != null }

        // The "+" badge under the author's avatar on the rail.
        compose.onCurrentVideo(hasContentDescription("Follow")).performClick()
        compose.eventually { assertTrue(server.following(me.username).any { it.id == creator.id }) }

        openFollowingTab()
        val followingVideo = compose.waitForVideo()
        assertEquals(creator.id, server.video(followingVideo).author.id)
        compose.onCurrentVideo(hasText("@${creator.username}")).assertIsDisplayed()
    }

    private fun openFollowingTab() {
        compose.onNode(hasText("Following") and hasRole(Role.Tab)).performClick()
        compose.waitForIdle()
    }

    /** The page on screen shows [videoId]'s author and caption as the server has them. */
    private fun assertShowsVideo(videoId: Long) {
        assertEquals(videoId, compose.currentVideoId())
        val video = server.video(videoId)
        compose.onCurrentVideo(hasText("@${video.author.username}")).assertIsDisplayed()
        compose.onCurrentVideo(hasText(video.caption)).assertIsDisplayed()
    }
}
