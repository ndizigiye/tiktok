package com.ndizigiye.loop.server

import com.ndizigiye.loop.api.ErrorDto
import com.ndizigiye.loop.api.UserDto
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse.BodyHandlers
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The API with media in Supabase Storage ([FakeSupabaseStorage]) instead of the data directory. */
class SupabaseMediaTest {
    @Test
    fun `uploads go to the bucket and DTOs carry its public URLs`() = supabaseLoopTest {
        val storage = storage!!
        val creator = register("bucketeer")
        val video = postVideo(creator, "stored remotely #cloud")

        val name = video.videoUrl.substringAfterLast('/')
        assertEquals(storage.publicUrl("videos/$name"), video.videoUrl)
        assertTrue(Regex("[0-9a-f]{24}\\.mp4").matches(name), name)
        val stored = storage.objects.getValue("videos/$name")
        assertContentEquals(TestMedia.videoBytes, stored.bytes)
        assertEquals("video/mp4", stored.contentType)
        assertContentEquals(TestMedia.videoBytes, download(video.videoUrl), "the public URL serves the upload")
        if (TestMedia.hasFfmpeg) {
            val thumbnail = "thumbs/${name.removeSuffix(".mp4")}.jpg"
            assertEquals(storage.publicUrl(thumbnail), video.thumbnailUrl)
            assertEquals("image/jpeg", storage.objects.getValue(thumbnail).contentType)
            assertEquals(video.thumbnailUrl, video.sound.coverUrl)
        }
        val serverRequests = storage.requests.filterNot { it.path.startsWith("/storage/v1/object/public/") }
        assertTrue(serverRequests.isNotEmpty() && serverRequests.all { it.authorized }, "every server request carries the service key")
        assertTrue(storage.requests("POST").all { it.upsert == "false" }, "uploads never replace anything")

        assertEquals(video.videoUrl, video(video.id).videoUrl)
        assertFalse(File(config.dataDir, "videos").exists(), "nothing is written to the data directory")
        assertEquals(emptyList(), stagedFiles(), "temporary copies are deleted")
        assertError(client.get("/media/videos/$name"), HttpStatusCode.NotFound, "not_found")
    }

    @Test
    fun `deleting a video deletes its objects and storage failures do not block it`() = supabaseLoopTest {
        val storage = storage!!
        val creator = register("tidy")
        val first = postVideo(creator, "first")
        val second = postVideo(creator, "second")
        val firstPaths = listOfNotNull(first.videoUrl, first.thumbnailUrl).map { it.substringAfter("/loop-media/") }

        assertEquals(HttpStatusCode.NoContent, client.delete("/api/videos/${first.id}") { bearerAuth(creator.token) }.status)
        val deletion = storage.requests("DELETE").single()
        assertTrue(deletion.authorized)
        val prefixes = Json.parseToJsonElement(deletion.json!!).jsonObject.getValue("prefixes").jsonArray.map { it.jsonPrimitive.content }
        assertEquals(firstPaths, prefixes)
        assertTrue(firstPaths.none { it in storage.objects })

        storage.failNext("DELETE", 400, """{"statusCode":"403","error":"Unauthorized","message":"invalid signature"}""")
        assertEquals(HttpStatusCode.NoContent, client.delete("/api/videos/${second.id}") { bearerAuth(creator.token) }.status)
        assertError(client.get("/api/videos/${second.id}"), HttpStatusCode.NotFound, "not_found")
        assertTrue(second.videoUrl.substringAfter("/loop-media/") in storage.objects, "left behind, only logged")
    }

    @Test
    fun `avatars are stored per user and replace the previous one`() = supabaseLoopTest {
        val storage = storage!!
        val user = register("portrait")
        val first = uploadAvatar(user, ByteArray(64) { 1 }, "image/png").body<UserDto>()
        val firstName = assertNotNull(first.avatarUrl).substringAfterLast('/')
        assertEquals(storage.publicUrl("avatars/$firstName"), first.avatarUrl)
        assertTrue(Regex("${user.id}-[0-9a-f]{24}\\.png").matches(firstName), firstName)
        assertEquals("image/png", storage.objects.getValue("avatars/$firstName").contentType)

        val second = uploadAvatar(user, ByteArray(32) { 2 }, "image/jpeg").body<UserDto>()
        assertTrue(second.avatarUrl!!.endsWith(".jpg"))
        assertEquals(listOf("avatars/${second.avatarUrl!!.substringAfterLast('/')}"), storage.objects.keys.filter { it.startsWith("avatars/") })
        assertEquals(emptyList(), stagedFiles())
    }

    @Test
    fun `storage size errors are too_large and leave nothing behind`() = supabaseLoopTest {
        val storage = storage!!
        val user = register("heavy")
        storage.refuseAbove = 16
        val response = upload(user, "too big for the bucket")
        assertError(response, HttpStatusCode.PayloadTooLarge, "too_large")
        assertError(uploadAvatar(user, ByteArray(64), "image/png"), HttpStatusCode.PayloadTooLarge, "too_large")
        assertEquals(0, client.get("/api/users/heavy").body<UserDto>().videoCount)
        assertNull(client.get("/api/me") { bearerAuth(user.token) }.body<UserDto>().avatarUrl)
        assertEquals(0, queryLong("SELECT COUNT(*) FROM videos"))
        assertTrue(storage.objects.isEmpty(), "${storage.objects.keys}")
        assertEquals(emptyList(), stagedFiles())
    }

    @Test
    fun `other storage failures are internal errors that reveal nothing`() = supabaseLoopTest {
        val storage = storage!!
        val user = register("unlucky")
        storage.failNext("POST", 400, """{"statusCode":"403","error":"Unauthorized","message":"key ${storage.serviceKey}"}""")
        val response = upload(user, "denied")
        assertEquals(HttpStatusCode.InternalServerError, response.status)
        val body = response.bodyAsText()
        assertEquals("internal", response.body<ErrorDto>().code)
        assertFalse("Unauthorized" in body || storage.serviceKey in body, body)
        assertEquals(0, queryLong("SELECT COUNT(*) FROM videos"))
        assertTrue(storage.objects.keys.none { it.startsWith("videos/") })
        assertEquals(emptyList(), stagedFiles())
    }

    @Test
    fun `the share page uses the bucket URLs`() = supabaseLoopTest {
        val video = postVideo(register("sharer"), "shared from storage")
        val html = client.get("/v/${video.id}").bodyAsText()
        assertTrue("<video src=\"${video.videoUrl}\"" in html, html)
        video.thumbnailUrl?.let { assertTrue("poster=\"$it\"" in html) }
    }

    @Test
    fun `startup fails with instructions when the bucket is unusable`() {
        val dataDir = Files.createTempDirectory("loop-test").toFile()
        FakeSupabaseStorage().use { storage ->
            try {
                storage.bucketPublic = false
                val error = assertFailsWith<IllegalStateException> { runBlocking { LoopServices.open(testConfig(dataDir, storage = storage)) } }
                assertTrue("\"loop-media\" is not public" in error.message.orEmpty(), error.message)
                assertFalse(File(dataDir, "loop.db").exists(), "checked before the database is opened")
            } finally {
                dataDir.deleteRecursively()
            }
        }
    }

    private suspend fun LoopTest.uploadAvatar(user: TestUser, bytes: ByteArray, type: String): HttpResponse =
        client.submitFormWithBinaryData(
            "/api/me/avatar",
            formData {
                append("avatar", bytes, Headers.build {
                    append(HttpHeaders.ContentType, type)
                    append(HttpHeaders.ContentDisposition, "filename=\"me\"")
                })
            },
        ) { bearerAuth(user.token) }

    /** Fetches a public URL the way a client does: plain GET, no key. */
    private fun download(url: String): ByteArray {
        val client = HttpClient.newHttpClient()
        val response = client.send(HttpRequest.newBuilder(URI.create(url)).build(), BodyHandlers.ofByteArray())
        assertEquals(200, response.statusCode())
        return response.body()
    }
}
