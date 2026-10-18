package com.ndizigiye.loop.server

import com.ndizigiye.loop.api.ApiLimits
import com.ndizigiye.loop.api.ErrorDto
import com.ndizigiye.loop.api.UserDto
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MediaTest {
    @Test
    fun `media supports range requests`() = loopTest {
        val creator = register("ranger")
        val video = postVideo(creator)
        val bytes = TestMedia.videoBytes

        val full = client.get(video.videoUrl)
        assertEquals(HttpStatusCode.OK, full.status)
        assertContentEquals(bytes, full.readRawBytes())
        assertEquals("bytes", full.headers[HttpHeaders.AcceptRanges])

        val partial = client.get(video.videoUrl) { header(HttpHeaders.Range, "bytes=0-99") }
        assertEquals(HttpStatusCode.PartialContent, partial.status)
        assertEquals("bytes 0-99/${bytes.size}", partial.headers[HttpHeaders.ContentRange])
        assertContentEquals(bytes.copyOfRange(0, 100), partial.readRawBytes())

        val head = client.head(video.videoUrl)
        assertEquals(HttpStatusCode.OK, head.status)
        assertEquals(bytes.size.toString(), head.headers[HttpHeaders.ContentLength])

        video.thumbnailUrl?.let { assertEquals(HttpStatusCode.OK, client.get(it).status) }
        assertError(client.get("/media/videos/missing.mp4"), HttpStatusCode.NotFound, "not_found")
        assertError(client.get("/media/videos/..%2Floop.db"), HttpStatusCode.NotFound, "not_found")
        assertError(client.get("/media/secrets/x.mp4"), HttpStatusCode.NotFound, "not_found")
    }

    @Test
    fun `avatars are validated and replace the previous one`() = loopTest {
        val user = register("pic")
        suspend fun uploadAvatar(bytes: ByteArray, type: String) = client.submitFormWithBinaryData(
            "/api/me/avatar",
            formData {
                append("avatar", bytes, Headers.build {
                    append(HttpHeaders.ContentType, type)
                    append(HttpHeaders.ContentDisposition, "filename=\"me.png\"")
                })
            },
        ) { bearerAuth(user.token) }

        val first = uploadAvatar(ByteArray(64) { 1 }, "image/png").body<UserDto>()
        val firstUrl = assertNotNull(first.avatarUrl)
        assertTrue(firstUrl.startsWith("/media/avatars/") && firstUrl.endsWith(".png"))
        assertContentEquals(ByteArray(64) { 1 }, client.get(firstUrl).readRawBytes())

        val second = uploadAvatar(ByteArray(32) { 2 }, "image/jpeg").body<UserDto>()
        assertTrue(second.avatarUrl!!.endsWith(".jpg"))
        assertEquals(1, File(config.dataDir, "avatars").listFiles()!!.size, "the old avatar is deleted")

        assertError(uploadAvatar(ByteArray(10), "image/gif"), HttpStatusCode.BadRequest, "validation")
        val tooLarge = ByteArray(ApiLimits.MAX_AVATAR_BYTES.toInt() + 1)
        assertError(uploadAvatar(tooLarge, "image/png"), HttpStatusCode.PayloadTooLarge, "too_large")
        assertEquals(second.avatarUrl, client.get("/api/me") { bearerAuth(user.token) }.body<UserDto>().avatarUrl)
    }

    @Test
    fun `share page is self-contained html`() = loopTest {
        val creator = register("sharer", "Sharer <b>")
        val video = postVideo(creator, "Look at this & that #demo")
        val response = client.get("/v/${video.id}")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Text.Html, response.contentType()?.withoutParameters())
        val html = response.bodyAsText()
        assertTrue("<video src=\"${video.videoUrl}\"" in html)
        assertTrue("controls playsinline loop" in html)
        assertTrue("<a class=\"open\" href=\"loop://video/${video.id}\">Open in Loop</a>" in html, "deep link button")
        assertTrue("Look at this &amp; that #demo" in html)
        assertTrue("Sharer &lt;b&gt;" in html)
        assertTrue("http://" !in html && "https://" !in html, "no external assets")
        video.thumbnailUrl?.let { assertTrue("poster=\"$it\"" in html) }
        assertError(client.get("/v/999"), HttpStatusCode.NotFound, "not_found")
    }

    @Test
    fun `health and unknown routes`() = loopTest {
        val health = client.get("/health")
        assertEquals(HttpStatusCode.OK, health.status)
        assertEquals("ok", health.bodyAsText())
        val missing = client.get("/api/nothing-here")
        assertEquals(HttpStatusCode.NotFound, missing.status)
        assertEquals("not_found", missing.body<ErrorDto>().code)
    }
}
