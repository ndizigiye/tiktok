package com.ndizigiye.loop.e2e

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.ndizigiye.loop.api.VideoDto
import com.ndizigiye.loop.e2e.harness.LoopE2eTest
import com.ndizigiye.loop.e2e.harness.TestAccount
import com.ndizigiye.loop.e2e.harness.clipboardText
import com.ndizigiye.loop.e2e.harness.currentPlayer
import com.ndizigiye.loop.e2e.harness.currentVideoId
import com.ndizigiye.loop.e2e.harness.currentVideoIdOrNull
import com.ndizigiye.loop.e2e.harness.eventually
import com.ndizigiye.loop.e2e.harness.hasRole
import com.ndizigiye.loop.e2e.harness.onCurrentVideo
import com.ndizigiye.loop.e2e.harness.onCurrentVideoPage
import com.ndizigiye.loop.e2e.harness.openBottomTab
import com.ndizigiye.loop.e2e.harness.waitFor
import com.ndizigiye.loop.e2e.harness.waitForText
import com.ndizigiye.loop.e2e.harness.waitForVideo
import com.ndizigiye.loop.e2e.harness.waitUntilExists
import com.ndizigiye.loop.e2e.harness.waitUntilGone
import com.ndizigiye.loop.ui.feed.FLOATING_HEART_TAG
import com.ndizigiye.loop.ui.feed.videoPageTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Features 5, 6, 8 and 9: like, bookmark, share and the long-press options of a video. */
class VideoActionsE2eTest : LoopE2eTest() {

    @Test
    fun likeButtonLikesAndUnlikesAndTheLikeIsKeptOnTheServer() {
        val video = freshVideo()
        val me = launchSignedIn(openVideo = video.id)
        compose.waitForVideo()

        railButton("Like").assert(hasText("0")).performClick()
        compose.waitUntilExists(hasContentDescription("Unlike") and hasText("1"))
        compose.eventually {
            val liked = server.video(video.id, me)
            assertTrue(liked.likedByMe)
            assertEquals(1, liked.likeCount)
        }

        // Reloaded from the server: the video is on the viewer's Liked tab, still liked.
        openOwnProfileTab(me, "Liked videos")
        openGridVideo(video)
        railButton("Unlike").assert(hasText("1")).performClick()
        compose.waitUntilExists(hasContentDescription("Like") and hasText("0"))
        compose.eventually {
            val unliked = server.video(video.id, me)
            assertFalse(unliked.likedByMe)
            assertEquals(0, unliked.likeCount)
        }
    }

    @Test
    fun doubleTapLikesWithAHeartWithoutUnliking() {
        val me = launchSignedIn()
        val videoId = compose.waitForVideo()
        val likesBefore = server.video(videoId).likeCount

        doubleTapShowingAHeart()
        compose.eventually { assertTrue(server.video(videoId, me).likedByMe) }
        railButton("Unlike").assert(hasText("${likesBefore + 1}"))

        // A second double tap shows the heart again but keeps the like.
        doubleTapShowingAHeart()
        railButton("Unlike").assert(hasText("${likesBefore + 1}"))
        val after = server.video(videoId, me)
        assertTrue(after.likedByMe)
        assertEquals(likesBefore + 1, after.likeCount)
    }

    @Test
    fun bookmarkShowsInOwnSavedTabAndCanBeRemoved() {
        val video = freshVideo()
        val me = launchSignedIn(openVideo = video.id)
        compose.waitForVideo()

        railButton("Save").performClick()
        compose.waitUntilExists(hasContentDescription("Remove from saved") and hasText("1"))
        compose.eventually { assertEquals(listOf(video.id), server.bookmarks(me).map { it.id }) }

        openOwnProfileTab(me, "Saved videos")
        openGridVideo(video)
        railButton("Remove from saved").performClick()
        compose.waitUntilExists(hasContentDescription("Save") and hasText("0"))
        compose.eventually { assertTrue(server.bookmarks(me).isEmpty()) }
    }

    @Test
    fun copyLinkSharesThePublicPageAndCountsTheShare() {
        val video = freshVideo()
        launchSignedIn(openVideo = video.id)
        compose.waitForVideo()

        railButton("Share").performClick()
        compose.waitForText("Share to")
        compose.onNode(hasText("Copy link") and hasClickAction()).performClick()
        compose.waitForText("Link copied")

        val link = "${server.baseUrl}v/${video.id}"
        assertEquals(link, clipboardText())
        compose.eventually { assertEquals(1, server.video(video.id).shareCount) }
        compose.waitUntilExists(hasContentDescription("Share") and hasText("1"))

        val (status, html) = server.webPage("v/${video.id}")
        assertEquals(200, status)
        assertTrue("share page has the app link", html.contains("Open in Loop") && html.contains("loop://video/${video.id}"))
    }

    @Test
    fun optionsChangeThePlaybackSpeed() {
        app.signIn(server.register())
        val scenario = app.launch()
        compose.waitForVideo()

        openOptions()
        compose.onNode(hasText("1x")).assertIsSelected()
        assertEquals(1f, scenario.currentPlayer().playbackParameters.speed)
        compose.onNode(hasText("2x")).performClick()
        compose.waitUntilGone(hasText("Playback speed"))
        // The video on screen now plays at that speed.
        assertEquals(2f, scenario.currentPlayer().playbackParameters.speed)

        openOptions()
        compose.onNode(hasText("2x")).assertIsSelected()
        compose.onNode(hasText("Report")).performClick()
        compose.waitForText("Thanks for letting us know")
    }

    @Test
    fun notInterestedRemovesTheVideoAndExcludesItFromForYou() {
        val me = launchSignedIn()
        val videoId = compose.waitForVideo()

        openOptions()
        compose.onNode(hasText("Not interested")).performClick()
        compose.waitForText("Got it. You'll see fewer videos like this.")
        compose.waitFor("the feed moves past the video") { compose.currentVideoIdOrNull().let { it != null && it != videoId } }

        val forYou = server.forYouPass(me).map { it.id }
        assertTrue(forYou.isNotEmpty())
        assertFalse("For You still contains $videoId", videoId in forYou)
    }

    private fun railButton(description: String) = compose.onCurrentVideo(hasContentDescription(description))

    /**
     * Double-taps the video on screen and checks that a heart pops up over it and then goes
     * away. The heart lives for under a second, which the test clock would otherwise skip
     * while the tap is handled, so the clock waits for the first frame after the tap.
     */
    private fun doubleTapShowingAHeart() {
        val heart = hasTestTag(FLOATING_HEART_TAG) and hasAnyAncestor(hasTestTag(videoPageTag(compose.currentVideoId())))
        compose.mainClock.autoAdvance = false
        try {
            compose.onCurrentVideoPage().performTouchInput { doubleClick(center) }
            compose.mainClock.advanceTimeByFrame()
            compose.onNode(heart, useUnmergedTree = true).assertExists()
        } finally {
            compose.mainClock.autoAdvance = true
        }
        compose.waitUntilGone(heart)
    }

    private fun openOptions() {
        compose.onCurrentVideoPage().performTouchInput { longClick(center) }
        compose.waitForText("Playback speed")
    }

    /** Leaves the pager for the Me tab and selects one of its grids. */
    private fun openOwnProfileTab(me: TestAccount, tab: String) {
        compose.onNode(hasContentDescription("Back")).performClick()
        compose.openBottomTab("Me")
        compose.waitForText("@${me.username}")
        compose.onNode(hasContentDescription(tab) and hasRole(Role.Tab)).performClick()
    }

    private fun openGridVideo(video: VideoDto) {
        val thumbnail = hasContentDescription(video.caption) and hasClickAction()
        compose.waitUntilExists(thumbnail)
        compose.onNode(thumbnail).performClick()
        compose.waitFor("the pager shows ${video.id}") { compose.currentVideoIdOrNull() == video.id }
        assertEquals(video.id, compose.currentVideoId())
    }
}
