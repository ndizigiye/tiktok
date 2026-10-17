package com.ndizigiye.loop.server

import com.ndizigiye.loop.api.CountDto
import com.ndizigiye.loop.api.NotificationDto
import com.ndizigiye.loop.api.ToggleResultDto
import com.ndizigiye.loop.api.UserDto
import com.ndizigiye.loop.api.VideoDto
import com.ndizigiye.loop.api.ViewEventRequest
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VideoTest {
    @Test
    fun `upload stores the file and returns the video`() = loopTest {
        val ana = register("ana", "Ana")
        val video = postVideo(ana, "  Morning light #Sunrise #calm #sunrise ")
        assertEquals("Morning light #Sunrise #calm #sunrise", video.caption)
        assertEquals(listOf("sunrise", "calm"), video.hashtags)
        assertEquals(ana.id, video.author.id)
        assertEquals("original sound - Ana", video.sound.title)
        assertEquals("Ana", video.sound.authorName)
        assertEquals(1, video.sound.videoCount)
        assertTrue(video.videoUrl.startsWith("/media/videos/") && video.videoUrl.endsWith(".mp4"))
        assertTrue(File(config.dataDir, video.videoUrl.removePrefix("/media/")).isFile)
        if (TestMedia.hasFfmpeg) {
            assertNotNull(video.thumbnailUrl)
            assertEquals(90, video.width)
            assertEquals(160, video.height)
            assertTrue(video.durationMs in 1_000..1_500, "duration ${video.durationMs}")
        }

        val fetched = video(video.id)
        assertEquals(video.copy(author = fetched.author), fetched)
        assertFalse(fetched.author.isMe)
        assertTrue(video(video.id, ana).author.isMe)
        assertEquals(1, client.get("/api/users/ana").body<UserDto>().videoCount)
    }

    @Test
    fun `arbitrary bytes with a video content type are accepted`() = loopTest {
        val ben = register("ben")
        val response = upload(ben, "raw bytes", bytes = ByteArray(2048) { it.toByte() })
        assertEquals(HttpStatusCode.Created, response.status)
        val video = response.body<VideoDto>()
        assertEquals(0, video.durationMs)
        assertNull(video.thumbnailUrl)
    }

    @Test
    fun `uploads are validated`() = loopTest {
        val cy = register("cyd")
        assertError(upload(cy, "text file", contentType = "text/plain"), HttpStatusCode.BadRequest, "validation")
        assertError(upload(cy, "x".repeat(301)), HttpStatusCode.BadRequest, "validation")
        assertError(upload(cy, "no such sound", soundId = 999), HttpStatusCode.NotFound, "not_found")
        val missingFile = client.submitFormWithBinaryData("/api/videos", formData { append("caption", "nothing") }) {
            bearerAuth(cy.token)
        }
        assertError(missingFile, HttpStatusCode.BadRequest, "validation")
        assertEquals(0, File(config.dataDir, "videos").listFiles()!!.size, "rejected uploads leave no files")
    }

    @Test
    fun `uploads can reuse a sound`() = loopTest {
        val dee = register("dee")
        val eli = register("eli")
        val original = postVideo(dee, "first")
        val remix = postVideo(eli, "remix", soundId = original.sound.id)
        assertEquals(original.sound.id, remix.sound.id)
        assertEquals(2, video(original.id).sound.videoCount)
    }

    @Test
    fun `missing videos are 404`() = loopTest {
        assertError(client.get("/api/videos/42"), HttpStatusCode.NotFound, "not_found")
        assertError(client.get("/api/videos/abc"), HttpStatusCode.NotFound, "not_found")
    }

    @Test
    fun `only the author can delete a video and everything goes with it`() = loopTest {
        val fay = register("fay")
        val gus = register("gus")
        val video = postVideo(fay, "bye soon #temporary")
        like(gus, video.id)
        comment(gus, video.id, "nice")
        client.put("/api/videos/${video.id}/bookmark") { bearerAuth(gus.token) }

        assertError(client.delete("/api/videos/${video.id}") { bearerAuth(gus.token) }, HttpStatusCode.Forbidden, "forbidden")
        assertEquals(HttpStatusCode.NoContent, client.delete("/api/videos/${video.id}") { bearerAuth(fay.token) }.status)
        assertError(client.get("/api/videos/${video.id}"), HttpStatusCode.NotFound, "not_found")
        assertFalse(File(config.dataDir, video.videoUrl.removePrefix("/media/")).exists())
        video.thumbnailUrl?.let { assertFalse(File(config.dataDir, it.removePrefix("/media/")).exists()) }
        assertTrue(page<VideoDto>("/api/me/bookmarks", gus).items.isEmpty())
        assertTrue(page<NotificationDto>("/api/notifications", fay).items.isEmpty())
        assertError(client.get("/api/hashtags/temporary"), HttpStatusCode.NotFound, "not_found")
        assertError(client.get("/api/sounds/${video.sound.id}"), HttpStatusCode.NotFound, "not_found")
    }

    @Test
    fun `likes are idempotent and counted`() = loopTest {
        val hal = register("hal")
        val ivy = register("ivy")
        val jo = register("joe")
        val video = postVideo(hal)

        assertEquals(ToggleResultDto(true, 1), like(ivy, video.id))
        assertEquals(ToggleResultDto(true, 1), like(ivy, video.id))
        assertEquals(ToggleResultDto(true, 2), like(jo, video.id))
        val seenByIvy = video(video.id, ivy)
        assertEquals(2, seenByIvy.likeCount)
        assertTrue(seenByIvy.likedByMe)
        assertFalse(video(video.id).likedByMe)
        assertEquals(2, client.get("/api/users/hal").body<UserDto>().likeCount)

        assertEquals(ToggleResultDto(false, 1), unlike(ivy, video.id))
        assertEquals(ToggleResultDto(false, 1), unlike(ivy, video.id))
        assertFalse(video(video.id, ivy).likedByMe)
        assertEquals(1, video(video.id).likeCount)
        assertError(client.put("/api/videos/999/like") { bearerAuth(ivy.token) }, HttpStatusCode.NotFound, "not_found")
    }

    @Test
    fun `bookmarks are idempotent, counted and listed newest first`() = loopTest {
        val kim = register("kim")
        val lee = register("lee")
        val first = postVideo(kim, "one")
        val second = postVideo(kim, "two")

        repeat(2) {
            val result = client.put("/api/videos/${first.id}/bookmark") { bearerAuth(lee.token) }.body<ToggleResultDto>()
            assertEquals(ToggleResultDto(true, 1), result)
        }
        client.put("/api/videos/${second.id}/bookmark") { bearerAuth(lee.token) }
        assertTrue(video(first.id, lee).bookmarkedByMe)
        assertEquals(1, video(first.id).bookmarkCount)
        assertEquals(listOf(second.id, first.id), page<VideoDto>("/api/me/bookmarks", lee).items.map { it.id })

        val removed = client.delete("/api/videos/${first.id}/bookmark") { bearerAuth(lee.token) }.body<ToggleResultDto>()
        assertEquals(ToggleResultDto(false, 0), removed)
        assertEquals(listOf(second.id), page<VideoDto>("/api/me/bookmarks", lee).items.map { it.id })
    }

    @Test
    fun `shares and views are counted`() = loopTest {
        val max = register("max")
        val video = postVideo(max)
        assertEquals(CountDto(1), client.post("/api/videos/${video.id}/share").body<CountDto>())
        assertEquals(CountDto(2), client.post("/api/videos/${video.id}/share") { bearerAuth(max.token) }.body<CountDto>())
        repeat(3) {
            val response = client.post("/api/videos/${video.id}/views") {
                bearerAuth(max.token)
                contentType(ContentType.Application.Json)
                setBody(ViewEventRequest(watchedMs = 900, completed = it == 0))
            }
            assertEquals(HttpStatusCode.NoContent, response.status)
        }
        val counted = video(video.id)
        assertEquals(2, counted.shareCount)
        assertEquals(3, counted.viewCount)
    }

    @Test
    fun `liked videos are listed on the profile`() = loopTest {
        val ned = register("ned")
        val ola = register("ola")
        val a = postVideo(ned, "a")
        val b = postVideo(ned, "b")
        like(ola, a.id)
        like(ola, b.id)
        assertEquals(listOf(b.id, a.id), page<VideoDto>("/api/users/ola/liked").items.map { it.id })
        assertEquals(listOf(b.id, a.id), allPages<VideoDto>("/api/users/ned/videos", limit = 1).map { it.id })
        assertError(client.get("/api/users/nobody/videos"), HttpStatusCode.NotFound, "not_found")
    }
}
