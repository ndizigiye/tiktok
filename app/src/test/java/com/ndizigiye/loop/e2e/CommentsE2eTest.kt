package com.ndizigiye.loop.e2e

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import com.ndizigiye.loop.e2e.harness.LoopE2eTest
import com.ndizigiye.loop.e2e.harness.TestMedia
import com.ndizigiye.loop.e2e.harness.eventually
import com.ndizigiye.loop.e2e.harness.onCurrentVideo
import com.ndizigiye.loop.e2e.harness.waitForText
import com.ndizigiye.loop.e2e.harness.waitForVideo
import com.ndizigiye.loop.e2e.harness.waitUntilExists
import com.ndizigiye.loop.e2e.harness.waitUntilGone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Feature 7: the comments sheet. */
class CommentsE2eTest : LoopE2eTest() {

    @Test
    fun postsACommentAndAReplyThenDeletesThemWithTheCounts() {
        val video = freshVideo()
        val me = launchSignedIn(openVideo = video.id)
        compose.waitForVideo()

        openComments()
        compose.waitForText("0 comments")
        compose.waitForText("No comments yet.")

        post("First! from the e2e suite")
        compose.waitForText("First! from the e2e suite")
        compose.waitForText("1 comment")
        val comment = compose.eventually {
            server.comments(video.id).single().also { assertEquals(me.user.id, it.author.id) }
        }

        replyButtonOf("First! from the e2e suite").performClick()
        compose.waitForText("Replying to @${me.username}")
        post("And a reply to myself")
        compose.waitForText("And a reply to myself")
        compose.waitForText("2 comments")
        compose.eventually { assertEquals(listOf("And a reply to myself"), server.replies(comment.id).map { it.text }) }

        // Deleting a top-level comment takes its replies along.
        compose.onNode(commentRow("First! from the e2e suite")).performTouchInput { longClick() }
        compose.waitForText("The comment and its replies will be removed for everyone.")
        compose.onNode(hasText("Delete") and hasAnyAncestor(isDialog()) and hasClickAction()).performClick()
        compose.waitUntilGone(hasText("First! from the e2e suite"))
        compose.waitForText("0 comments")
        compose.eventually { assertTrue(server.comments(video.id).isEmpty()) }

        compose.onNode(hasContentDescription("Close comments")).performClick()
        compose.waitUntilGone(hasText("0 comments"))
        compose.onCurrentVideo(hasContentDescription("Comments")).assert(hasText("0"))
    }

    @Test
    fun answersAReplyInTheSameThreadAndLikesIt() {
        val creator = server.register("Clip Maker")
        val video = server.upload(creator, TestMedia.video, "Thread me ${System.nanoTime()}")
        val me = server.register("Thread Starter")
        val myComment = server.comment(me, video.id, "Top-level from me")
        val creatorReply = server.comment(creator, video.id, "Creator answers", parentId = myComment.id)
        app.signIn(me)
        app.launch(deepLink = android.net.Uri.parse("loop://video/${video.id}"))
        compose.waitForVideo()
        compose.onCurrentVideo(hasContentDescription("Comments")).assert(hasText("2"))

        openComments()
        compose.waitForText("2 comments")
        compose.onNode(hasText("View 1 reply") and hasClickAction()).performClick()
        compose.waitForText("Creator answers")

        compose.onNode(isToggleable() and hasAnyAncestor(commentRow("Creator answers"))).performClick()
        compose.eventually {
            val reply = server.replies(myComment.id, me).single { it.id == creatorReply.id }
            assertTrue(reply.likedByMe)
            assertEquals(1, reply.likeCount)
        }

        // Answering a reply mentions its author; the server files it under the top-level comment.
        replyButtonOf("Creator answers").performClick()
        compose.waitForText("Replying to @${creator.username}")
        compose.onNode(hasSetTextAction()).assert(hasText("@${creator.username} "))
        post("thanks!")
        compose.waitForText("@${creator.username} thanks!")
        compose.waitForText("3 comments")
        compose.eventually {
            val replies = server.replies(myComment.id)
            assertEquals(listOf("Creator answers", "@${creator.username} thanks!"), replies.map { it.text })
            assertTrue(replies.all { it.parentId == myComment.id })
        }
        compose.eventually { assertEquals(3, server.video(video.id).commentCount) }
    }

    private fun openComments() {
        compose.onCurrentVideo(hasContentDescription("Comments")).performClick()
        compose.waitUntilExists(hasContentDescription("Close comments"))
    }

    private fun post(text: String) {
        compose.onNode(hasSetTextAction()).performTextInput(text)
        compose.onNode(hasContentDescription("Post comment")).performClick()
    }

    /** A comment or reply row, which opens a reply on tap and offers deletion on long press. */
    private fun commentRow(text: String) = hasText(text) and hasClickAction()

    private fun replyButtonOf(text: String): SemanticsNodeInteraction =
        compose.onNode(hasText("Reply") and hasClickAction() and hasAnyAncestor(commentRow(text)))
}
