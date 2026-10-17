package com.ndizigiye.loop.server

import com.ndizigiye.loop.api.AuthResponse
import com.ndizigiye.loop.api.CommentDto
import com.ndizigiye.loop.api.CreateCommentRequest
import com.ndizigiye.loop.api.ErrorDto
import com.ndizigiye.loop.api.PageDto
import com.ndizigiye.loop.api.RegisterRequest
import com.ndizigiye.loop.api.ToggleResultDto
import com.ndizigiye.loop.api.VideoDto
import com.ndizigiye.loop.server.plugins.LoopJson
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals

/** Runs [block] against a fresh server with an empty temporary data directory and no seeding. */
fun loopTest(block: suspend LoopTest.() -> Unit) {
    val dataDir = Files.createTempDirectory("loop-test").toFile()
    try {
        val config = ServerConfig(dataDir = dataDir, jwtSecret = "test-secret", seed = false)
        testApplication {
            application { module(config) }
            LoopTest(this, config).block()
        }
    } finally {
        dataDir.deleteRecursively()
    }
}

data class TestUser(val token: String, val id: Long, val username: String)

class LoopTest(builder: ApplicationTestBuilder, val config: ServerConfig) {
    /** Client with JSON support and no automatic auth. */
    val client: HttpClient = builder.createClient {
        install(ContentNegotiation) { json(LoopJson) }
    }

    suspend fun register(username: String, displayName: String = username.replaceFirstChar(Char::uppercase)): TestUser {
        val response = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(username, displayName, "secret123"))
        }
        assertEquals(HttpStatusCode.Created, response.status, "register $username")
        val auth = response.body<AuthResponse>()
        return TestUser(auth.token, auth.user.id, auth.user.username)
    }

    suspend fun upload(
        user: TestUser,
        caption: String = "",
        soundId: Long? = null,
        bytes: ByteArray = TestMedia.videoBytes,
        contentType: String = "video/mp4",
    ): HttpResponse = client.submitFormWithBinaryData(
        url = "/api/videos",
        formData = formData {
            append("caption", caption)
            soundId?.let { append("soundId", it.toString()) }
            append(
                "video",
                bytes,
                Headers.build {
                    append(HttpHeaders.ContentType, contentType)
                    append(HttpHeaders.ContentDisposition, "filename=\"clip.mp4\"")
                },
            )
        },
    ) { bearerAuth(user.token) }

    suspend fun postVideo(user: TestUser, caption: String = "", soundId: Long? = null): VideoDto {
        val response = upload(user, caption, soundId)
        assertEquals(HttpStatusCode.Created, response.status, "upload by ${user.username}")
        return response.body()
    }

    suspend fun video(id: Long, viewer: TestUser? = null): VideoDto =
        client.get("/api/videos/$id") { auth(viewer) }.body()

    suspend fun comment(user: TestUser, videoId: Long, text: String, parentId: Long? = null): CommentDto {
        val response = client.post("/api/videos/$videoId/comments") {
            bearerAuth(user.token)
            contentType(ContentType.Application.Json)
            setBody(CreateCommentRequest(text, parentId))
        }
        assertEquals(HttpStatusCode.Created, response.status, "comment by ${user.username}")
        return response.body()
    }

    suspend fun like(user: TestUser, videoId: Long): ToggleResultDto =
        client.put("/api/videos/$videoId/like") { bearerAuth(user.token) }.body()

    suspend fun unlike(user: TestUser, videoId: Long): ToggleResultDto =
        client.delete("/api/videos/$videoId/like") { bearerAuth(user.token) }.body()

    suspend fun follow(user: TestUser, target: TestUser): ToggleResultDto =
        client.put("/api/users/${target.username}/follow") { bearerAuth(user.token) }.body()

    suspend inline fun <reified T> page(path: String, viewer: TestUser? = null): PageDto<T> =
        client.get(path) { auth(viewer) }.body()

    /** Follows `nextCursor` until the end and returns every item. */
    suspend inline fun <reified T> allPages(path: String, viewer: TestUser? = null, limit: Int = 2): List<T> {
        val items = mutableListOf<T>()
        var cursor: String? = null
        do {
            val separator = if ('?' in path) '&' else '?'
            val query = "${separator}limit=$limit" + (cursor?.let { "&cursor=$it" } ?: "")
            val page = page<T>(path + query, viewer)
            items += page.items
            cursor = page.nextCursor
        } while (cursor != null)
        return items
    }

    suspend fun assertError(response: HttpResponse, status: HttpStatusCode, code: String) {
        assertEquals(status, response.status)
        assertEquals(code, response.body<ErrorDto>().code)
    }
}

fun HttpRequestBuilder.auth(user: TestUser?) {
    user?.let { bearerAuth(it.token) }
}

/** A tiny real MP4 when ffmpeg is installed, otherwise arbitrary bytes (the server must accept both). */
object TestMedia {
    val hasFfmpeg: Boolean by lazy {
        runCatching { ProcessBuilder("ffmpeg", "-version").start().waitFor(10, TimeUnit.SECONDS) }.getOrDefault(false)
    }

    val videoBytes: ByteArray by lazy {
        if (!hasFfmpeg) return@lazy ByteArray(4096) { (it % 251).toByte() }
        val file = File.createTempFile("loop-test-clip", ".mp4")
        try {
            val process = ProcessBuilder(
                "ffmpeg", "-nostdin", "-v", "error", "-y",
                "-f", "lavfi", "-i", "testsrc2=s=90x160:r=10", "-t", "1.2",
                "-c:v", "libx264", "-pix_fmt", "yuv420p", "-movflags", "+faststart", file.absolutePath,
            ).redirectErrorStream(true).start()
            process.inputStream.readAllBytes()
            check(process.waitFor(60, TimeUnit.SECONDS) && process.exitValue() == 0) { "ffmpeg could not create a test clip" }
            file.readBytes()
        } finally {
            file.delete()
        }
    }
}
