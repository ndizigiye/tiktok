package com.ndizigiye.loop.server

import com.ndizigiye.loop.api.CommentDto
import com.ndizigiye.loop.api.CreateCommentRequest
import com.ndizigiye.loop.api.ToggleResultDto
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CommentTest {
    @Test
    fun `comments, replies and reply-to-reply threading`() = loopTest {
        val creator = register("creator")
        val ann = register("ann")
        val bob = register("bob")
        val video = postVideo(creator, "thread me")

        val top = comment(ann, video.id, "  first!  ")
        assertEquals("first!", top.text)
        assertNull(top.parentId)
        assertFalse(top.byCreator)

        val reply = comment(creator, video.id, "thanks", parentId = top.id)
        assertEquals(top.id, reply.parentId)
        assertTrue(reply.byCreator)

        val replyToReply = comment(bob, video.id, "@creator agreed", parentId = reply.id)
        assertEquals(top.id, replyToReply.parentId, "a reply to a reply attaches to the top-level comment")

        val topLevel = page<CommentDto>("/api/videos/${video.id}/comments")
        assertEquals(listOf(top.id), topLevel.items.map { it.id })
        assertEquals(2, topLevel.items.single().replyCount)
        val replies = allPages<CommentDto>("/api/comments/${top.id}/replies", limit = 1)
        assertEquals(listOf(reply.id, replyToReply.id), replies.map { it.id }, "replies are oldest first")
        assertEquals(3, video(video.id).commentCount, "commentCount includes replies")
    }

    @Test
    fun `comments are validated`() = loopTest {
        val creator = register("val")
        val other = register("other")
        val video = postVideo(creator)
        val elsewhere = postVideo(creator)
        val foreign = comment(other, elsewhere.id, "on another video")

        suspend fun post(text: String, parentId: Long? = null) = client.post("/api/videos/${video.id}/comments") {
            bearerAuth(other.token)
            contentType(ContentType.Application.Json)
            setBody(CreateCommentRequest(text, parentId))
        }
        assertError(post("   "), HttpStatusCode.BadRequest, "validation")
        assertError(post("x".repeat(301)), HttpStatusCode.BadRequest, "validation")
        assertError(post("wrong video", parentId = foreign.id), HttpStatusCode.BadRequest, "validation")
        assertError(post("missing parent", parentId = 12345), HttpStatusCode.NotFound, "not_found")
        assertEquals(HttpStatusCode.Created, post("x".repeat(300)).status)
        assertError(client.get("/api/videos/999/comments"), HttpStatusCode.NotFound, "not_found")
    }

    @Test
    fun `top-level comments are ordered by likes, then newest`() = loopTest {
        val creator = register("ord")
        val fans = listOf(register("fan1"), register("fan2"))
        val video = postVideo(creator)
        val old = comment(fans[0], video.id, "old")
        val liked = comment(fans[0], video.id, "liked")
        val newest = comment(fans[1], video.id, "newest")
        client.put("/api/comments/${liked.id}/like") { bearerAuth(fans[1].token) }

        val ordered = allPages<CommentDto>("/api/videos/${video.id}/comments", limit = 2)
        assertEquals(listOf(liked.id, newest.id, old.id), ordered.map { it.id })
    }

    @Test
    fun `deleting comments follows the permission rules and cascades`() = loopTest {
        val creator = register("owner")
        val author = register("writer")
        val stranger = register("stranger")
        val video = postVideo(creator)
        val top = comment(author, video.id, "top")
        comment(stranger, video.id, "reply 1", parentId = top.id)
        val reply2 = comment(creator, video.id, "reply 2", parentId = top.id)
        val other = comment(stranger, video.id, "another")
        assertEquals(4, video(video.id).commentCount)

        assertError(client.delete("/api/comments/${top.id}") { bearerAuth(stranger.token) }, HttpStatusCode.Forbidden, "forbidden")
        assertEquals(HttpStatusCode.NoContent, client.delete("/api/comments/${reply2.id}") { bearerAuth(creator.token) }.status)
        assertEquals(3, video(video.id).commentCount)

        assertEquals(HttpStatusCode.NoContent, client.delete("/api/comments/${top.id}") { bearerAuth(author.token) }.status)
        assertEquals(1, video(video.id).commentCount, "replies are deleted with their top-level comment")
        assertError(client.get("/api/comments/${top.id}/replies"), HttpStatusCode.NotFound, "not_found")

        assertEquals(HttpStatusCode.NoContent, client.delete("/api/comments/${other.id}") { bearerAuth(creator.token) }.status)
        assertEquals(0, video(video.id).commentCount, "the video author can delete any comment on it")
        assertError(client.delete("/api/comments/${other.id}") { bearerAuth(creator.token) }, HttpStatusCode.NotFound, "not_found")
    }

    @Test
    fun `comment likes are idempotent`() = loopTest {
        val creator = register("clara")
        val fan = register("clarafan")
        val video = postVideo(creator)
        val c = comment(creator, video.id, "like me")
        repeat(2) {
            assertEquals(ToggleResultDto(true, 1), client.put("/api/comments/${c.id}/like") { bearerAuth(fan.token) }.body())
        }
        val seen = page<CommentDto>("/api/videos/${video.id}/comments", fan).items.single()
        assertEquals(1, seen.likeCount)
        assertTrue(seen.likedByMe)
        repeat(2) {
            assertEquals(ToggleResultDto(false, 0), client.delete("/api/comments/${c.id}/like") { bearerAuth(fan.token) }.body())
        }
        assertFalse(page<CommentDto>("/api/videos/${video.id}/comments", fan).items.single().likedByMe)
    }
}
