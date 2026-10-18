package com.ndizigiye.loop.e2e

import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import com.ndizigiye.loop.common.formatCount
import com.ndizigiye.loop.e2e.harness.LoopDeviceTest
import com.ndizigiye.loop.e2e.harness.eventually
import com.ndizigiye.loop.e2e.harness.onCurrentVideo
import com.ndizigiye.loop.e2e.harness.onCurrentVideoPage
import com.ndizigiye.loop.e2e.harness.saveScreenshot
import com.ndizigiye.loop.e2e.harness.waitForText
import com.ndizigiye.loop.e2e.harness.waitForVideo
import com.ndizigiye.loop.e2e.harness.waitUntilExists
import com.ndizigiye.loop.e2e.harness.waitUntilPlaying
import com.ndizigiye.loop.ui.feed.videoPageTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Features 5 and 7 on a device: a double-tap like and a comment, both checked on the server. */
class InteractionsDeviceTest : LoopDeviceTest() {

    @Test
    fun doubleTapLikesThePlayingVideoOnTheServer() {
        val me = launchSignedIn()
        val videoId = compose.waitForVideo()
        compose.waitUntilPlaying()
        val likesBefore = server.video(videoId).likeCount

        compose.onCurrentVideoPage().performTouchInput { doubleClick(center) }
        saveScreenshot("like-01-double-tap-heart")
        compose.eventually { assertTrue(server.video(videoId, me).likedByMe) }
        compose.waitUntilExists(rail(videoId, "Unlike", likesBefore + 1))
        assertEquals(likesBefore + 1, server.video(videoId).likeCount)
        saveScreenshot("like-02-liked")
    }

    @Test
    fun commentsSheetPostsACommentThatReachesTheServer() {
        val me = launchSignedIn()
        val videoId = compose.waitForVideo()
        val commentsBefore = server.video(videoId).commentCount

        compose.onCurrentVideo(hasContentDescription("Comments")).performClick()
        compose.waitUntilExists(hasContentDescription("Close comments"))
        compose.waitForText(commentCountLabel(commentsBefore))
        saveScreenshot("comments-01-sheet-open")

        val text = "Posted from the emulator ${System.currentTimeMillis()}"
        compose.onNode(hasSetTextAction()).performTextInput(text)
        compose.onNode(hasContentDescription("Post comment")).performClick()
        compose.waitForText(text)
        compose.waitForText(commentCountLabel(commentsBefore + 1))
        saveScreenshot("comments-02-posted")

        compose.eventually {
            assertTrue(server.comments(videoId).any { it.text == text && it.author.id == me.user.id })
        }
        assertEquals(commentsBefore + 1, server.video(videoId).commentCount)

        compose.onNode(hasContentDescription("Close comments")).performClick()
        compose.waitUntilExists(rail(videoId, "Comments", commentsBefore + 1))
    }

    /** The rail button [description] of video [videoId]'s page, showing [count]. */
    private fun rail(videoId: Long, description: String, count: Long) =
        hasContentDescription(description) and hasText(formatCount(count)) and
            hasAnyAncestor(hasTestTag(videoPageTag(videoId)))

    private fun commentCountLabel(count: Long) = if (count == 1L) "1 comment" else "${formatCount(count)} comments"
}
