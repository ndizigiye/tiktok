package com.ndizigiye.loop.server

import com.ndizigiye.loop.api.CountDto
import com.ndizigiye.loop.api.NotificationDto
import com.ndizigiye.loop.api.NotificationType
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotificationTest {
    private suspend fun LoopTest.inbox(user: TestUser): List<NotificationDto> = allPages("/api/notifications", user, limit = 3)

    private suspend fun LoopTest.unread(user: TestUser): Long =
        client.get("/api/notifications/unread-count") { bearerAuth(user.token) }.body<CountDto>().count

    @Test
    fun `likes and follows notify and are retracted when undone`() = loopTest {
        val creator = register("star")
        val fan = register("fan")
        val video = postVideo(creator)

        like(fan, video.id)
        like(fan, video.id)
        follow(fan, creator)
        val items = inbox(creator)
        assertEquals(listOf(NotificationType.FOLLOW, NotificationType.LIKE), items.map { it.type }, "newest first, no duplicates")
        assertEquals(fan.id, items[1].actor.id)
        assertEquals(video.id, items[1].video?.id)
        assertEquals(video.thumbnailUrl, items[1].video?.thumbnailUrl)
        assertTrue(items.none { it.read })
        assertEquals(2, unread(creator))

        unlike(fan, video.id)
        client.delete("/api/users/star/follow") { bearerAuth(fan.token) }
        assertTrue(inbox(creator).isEmpty())
        assertEquals(0, unread(creator))
    }

    @Test
    fun `comments notify the video author, the parent author and mentioned users once each`() = loopTest {
        val creator = register("maker")
        val first = register("first")
        val second = register("second")
        val mentioned = register("tagged")
        val video = postVideo(creator)

        val top = comment(first, video.id, "hello @tagged and @maker")
        assertEquals(listOf(NotificationType.COMMENT), inbox(creator).map { it.type }, "a mention of the author is not doubled")
        val mention = inbox(mentioned).single()
        assertEquals(NotificationType.MENTION, mention.type)
        assertEquals("hello @tagged and @maker", mention.text)

        comment(second, video.id, "replying", parentId = top.id)
        assertEquals(NotificationType.REPLY, inbox(first).single().type)
        assertEquals(NotificationType.COMMENT, inbox(creator).first().type)

        client.put("/api/comments/${top.id}/like") { bearerAuth(creator.token) }
        val commentLike = inbox(first).first()
        assertEquals(NotificationType.COMMENT_LIKE, commentLike.type)
        assertEquals("hello @tagged and @maker", commentLike.text)
        client.delete("/api/comments/${top.id}/like") { bearerAuth(creator.token) }
        assertEquals(listOf(NotificationType.REPLY), inbox(first).map { it.type })

        client.delete("/api/comments/${top.id}") { bearerAuth(first.token) }
        assertTrue(inbox(creator).isEmpty(), "deleting comments removes their notifications")
        assertTrue(inbox(first).isEmpty())
        assertTrue(inbox(mentioned).isEmpty())
    }

    @Test
    fun `own actions never notify`() = loopTest {
        val me = register("selfie")
        val video = postVideo(me, "hi @selfie")
        like(me, video.id)
        comment(me, video.id, "talking to myself @selfie")
        assertTrue(inbox(me).isEmpty())
    }

    @Test
    fun `caption mentions notify existing users`() = loopTest {
        val creator = register("caption")
        val friend = register("friend")
        postVideo(creator, "with @friend and @ghost")
        val mention = inbox(friend).single()
        assertEquals(NotificationType.MENTION, mention.type)
        assertEquals(creator.id, mention.actor.id)
    }

    @Test
    fun `mark all read`() = loopTest {
        val creator = register("reader")
        val fan = register("liker")
        val video = postVideo(creator)
        like(fan, video.id)
        follow(fan, creator)
        assertEquals(2, unread(creator))
        assertEquals(HttpStatusCode.NoContent, client.post("/api/notifications/read") { bearerAuth(creator.token) }.status)
        assertEquals(0, unread(creator))
        assertTrue(inbox(creator).all { it.read })
        assertFalse(inbox(creator).isEmpty())
        assertTrue(inbox(creator).first { it.type == NotificationType.FOLLOW }.actor.let { !it.isFollowing && !it.isMe })
        assertEquals(HttpStatusCode.OK, client.put("/api/users/liker/follow") { bearerAuth(creator.token) }.status)
        assertTrue(inbox(creator).first { it.type == NotificationType.FOLLOW }.actor.isFollowing)
    }

    @Test
    fun `mark read up to a notification leaves newer ones unread`() = loopTest {
        val creator = register("upto")
        val fan = register("upto_fan")
        val video = postVideo(creator)
        like(fan, video.id)
        follow(fan, creator)
        val loaded = inbox(creator)
        assertEquals(listOf(NotificationType.FOLLOW, NotificationType.LIKE), loaded.map { it.type })
        comment(fan, video.id, "arrived after the inbox was loaded")

        suspend fun markRead(user: TestUser, query: String) =
            client.post("/api/notifications/read$query") { bearerAuth(user.token) }

        assertEquals(HttpStatusCode.NoContent, markRead(creator, "?upTo=${loaded.last().id}").status)
        assertEquals(listOf(false, false, true), inbox(creator).map { it.read }, "only the oldest is read")
        assertEquals(HttpStatusCode.NoContent, markRead(creator, "?upTo=${loaded.first().id}").status)
        val inbox = inbox(creator)
        assertEquals(NotificationType.COMMENT, inbox.first().type)
        assertEquals(listOf(false, true, true), inbox.map { it.read }, "the newer comment stays unread")
        assertEquals(1, unread(creator))

        // The inbox order decides, not the id: one dated after the given notification stays
        // unread although its id is lower.
        val comment = inbox.first()
        follow(register("upto_late"), creator)
        val backdated = inbox(creator).first()
        assertTrue(backdated.id > comment.id)
        execute("UPDATE notifications SET created_at = created_at - 3600000 WHERE id = ${backdated.id}")
        assertEquals(backdated.id, inbox(creator).last().id, "dated an hour back, it is the oldest")
        assertEquals(2, unread(creator))
        assertEquals(HttpStatusCode.NoContent, markRead(creator, "?upTo=${backdated.id}").status)
        assertEquals(1, unread(creator))
        assertFalse(inbox(creator).single { it.id == comment.id }.read)

        assertError(markRead(creator, "?upTo=abc"), HttpStatusCode.BadRequest, "validation")
        assertError(markRead(creator, "?upTo=999999"), HttpStatusCode.NotFound, "not_found")
        like(creator, postVideo(fan).id)
        val othersNotification = inbox(fan).single()
        assertError(markRead(creator, "?upTo=${othersNotification.id}"), HttpStatusCode.NotFound, "not_found")
        assertFalse(inbox(fan).single().read, "another user's notification is untouched")
        assertEquals(1, unread(creator))

        assertEquals(HttpStatusCode.NoContent, markRead(creator, "?upTo=").status, "an empty upTo means all")
        assertEquals(0, unread(creator))
    }
}
