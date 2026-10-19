package com.ndizigiye.loop.e2e

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import com.ndizigiye.loop.api.NotificationType
import com.ndizigiye.loop.e2e.harness.LoopE2eTest
import com.ndizigiye.loop.e2e.harness.TestMedia
import com.ndizigiye.loop.e2e.harness.eventually
import com.ndizigiye.loop.e2e.harness.hasRole
import com.ndizigiye.loop.e2e.harness.onBottomTab
import com.ndizigiye.loop.e2e.harness.openBottomTab
import com.ndizigiye.loop.e2e.harness.waitForText
import com.ndizigiye.loop.e2e.harness.waitUntilExists
import com.ndizigiye.loop.e2e.harness.waitUntilGone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Feature 17: notifications created by a second user, the unread badge, marking read and following back. */
class InboxE2eTest : LoopE2eTest() {

    @Test
    fun showsWhatASecondUserDidMarksItReadAndFollowsBack() {
        val me = server.register("Inbox Owner")
        val myVideo = server.upload(me, TestMedia.video, "My clip for the inbox ${System.nanoTime()}")
        val otherVideo = freshVideo()
        val myComment = server.comment(me, otherVideo.id, "My two cents")

        val second = server.register("Second Tester")
        server.like(second, myVideo.id)
        server.comment(second, myVideo.id, "Nice clip!")
        server.comment(second, otherVideo.id, "Fair point", parentId = myComment.id)
        server.likeComment(second, myComment.id)
        server.follow(second, me.username)
        server.comment(second, otherVideo.id, "Look at this @${me.username}")
        val expectedTypes = setOf(
            NotificationType.LIKE, NotificationType.COMMENT, NotificationType.REPLY,
            NotificationType.COMMENT_LIKE, NotificationType.FOLLOW, NotificationType.MENTION,
        )
        assertEquals(expectedTypes, server.notifications(me).map { it.type }.toSet())
        assertEquals(6, server.unreadCount(me))

        app.signIn(me)
        app.launch()
        val unreadBadge = hasText("Inbox") and hasRole(Role.Tab) and hasText("6")
        compose.waitUntilExists(unreadBadge)

        compose.openBottomTab("Inbox")
        for (message in listOf(
            "Second Tester liked your video",
            "Second Tester commented: Nice clip!",
            "Second Tester replied to your comment: Fair point",
            "Second Tester liked your comment: My two cents",
            "Second Tester started following you",
            "Second Tester mentioned you: Look at this @${me.username}",
        )) {
            compose.waitForText(message)
        }

        // Opening the inbox marks everything read.
        compose.eventually { assertEquals(0, server.unreadCount(me)) }
        compose.waitUntilGone(unreadBadge)
        compose.onBottomTab("Inbox").assertExists()

        val followRow = hasText("Second Tester started following you") and hasClickAction()
        compose.onNode(hasText("Follow") and hasAnyAncestor(followRow)).performClick()
        compose.waitUntilExists(hasText("Following") and hasAnyAncestor(followRow))
        compose.eventually { assertTrue(server.following(me.username).any { it.id == second.user.id }) }
    }
}
