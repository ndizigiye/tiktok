package com.ndizigiye.loop.server

import com.ndizigiye.loop.api.AuthResponse
import com.ndizigiye.loop.api.LoginRequest
import com.ndizigiye.loop.api.RegisterRequest
import com.ndizigiye.loop.api.UpdateProfileRequest
import com.ndizigiye.loop.api.UserDto
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuthTest {
    private suspend fun LoopTest.registerRaw(username: String, displayName: String, password: String): HttpResponse =
        client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(username, displayName, password))
        }

    @Test
    fun `register returns a token and the new user`() = loopTest {
        val response = registerRaw("  Mia.Rose ", "Mia", "secret123")
        assertEquals(HttpStatusCode.Created, response.status)
        val auth = response.body<AuthResponse>()
        assertEquals("mia.rose", auth.user.username)
        assertTrue(auth.user.isMe)
        assertTrue(auth.token.isNotBlank())

        val me = client.get("/api/me") { bearerAuth(auth.token) }.body<UserDto>()
        assertEquals(auth.user.id, me.id)
        assertTrue(me.isMe)
    }

    @Test
    fun `usernames are unique regardless of case`() = loopTest {
        register("sam")
        assertError(registerRaw("SAM", "Sam again", "secret123"), HttpStatusCode.Conflict, "username_taken")
    }

    @Test
    fun `registration is validated`() = loopTest {
        assertError(registerRaw("ab", "Too short", "secret123"), HttpStatusCode.BadRequest, "validation")
        assertError(registerRaw("_leading", "Underscore", "secret123"), HttpStatusCode.BadRequest, "validation")
        assertError(registerRaw("has space", "Space", "secret123"), HttpStatusCode.BadRequest, "validation")
        assertError(registerRaw("valid_name", "   ", "secret123"), HttpStatusCode.BadRequest, "validation")
        assertError(registerRaw("valid_name", "x".repeat(41), "secret123"), HttpStatusCode.BadRequest, "validation")
        assertError(registerRaw("valid_name", "Valid", "12345"), HttpStatusCode.BadRequest, "validation")
        assertError(
            client.post("/api/auth/register") {
                contentType(ContentType.Application.Json)
                setBody("""{"username": 3}""")
            },
            HttpStatusCode.BadRequest,
            "validation",
        )
    }

    @Test
    fun `login accepts any username case and rejects wrong passwords`() = loopTest {
        val user = register("lou")
        val ok = client.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest("LOU", "secret123"))
        }
        assertEquals(HttpStatusCode.OK, ok.status)
        assertEquals(user.id, ok.body<AuthResponse>().user.id)

        val wrong = client.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest("lou", "nope-nope"))
        }
        assertError(wrong, HttpStatusCode.Unauthorized, "invalid_credentials")
        val unknown = client.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest("nobody", "secret123"))
        }
        assertError(unknown, HttpStatusCode.Unauthorized, "invalid_credentials")
    }

    @Test
    fun `profile updates leave null fields unchanged`() = loopTest {
        val user = register("pat", "Pat")
        val updated = client.patch("/api/me") {
            bearerAuth(user.token)
            contentType(ContentType.Application.Json)
            setBody(UpdateProfileRequest(bio = "Hello there"))
        }.body<UserDto>()
        assertEquals("Pat", updated.displayName)
        assertEquals("Hello there", updated.bio)

        val renamed = client.patch("/api/me") {
            bearerAuth(user.token)
            contentType(ContentType.Application.Json)
            setBody(UpdateProfileRequest(displayName = "Patricia"))
        }.body<UserDto>()
        assertEquals("Patricia", renamed.displayName)
        assertEquals("Hello there", renamed.bio)

        val tooLong = client.patch("/api/me") {
            bearerAuth(user.token)
            contentType(ContentType.Application.Json)
            setBody(UpdateProfileRequest(bio = "x".repeat(161)))
        }
        assertError(tooLong, HttpStatusCode.BadRequest, "validation")
    }

    @Test
    fun `auth endpoints return 401 without a valid token`() = loopTest {
        val requests: List<suspend () -> HttpResponse> = listOf(
            { client.get("/api/me") },
            { client.patch("/api/me") },
            { client.post("/api/me/avatar") },
            { client.get("/api/me/bookmarks") },
            { client.get("/api/feed/following") },
            { client.post("/api/videos") },
            { client.delete("/api/videos/1") },
            { client.put("/api/videos/1/like") },
            { client.delete("/api/videos/1/like") },
            { client.put("/api/videos/1/bookmark") },
            { client.delete("/api/videos/1/bookmark") },
            { client.post("/api/videos/1/not-interested") },
            { client.post("/api/videos/1/comments") },
            { client.delete("/api/comments/1") },
            { client.put("/api/comments/1/like") },
            { client.delete("/api/comments/1/like") },
            { client.put("/api/users/someone/follow") },
            { client.delete("/api/users/someone/follow") },
            { client.get("/api/notifications") },
            { client.get("/api/notifications/unread-count") },
            { client.post("/api/notifications/read") },
            { client.get("/api/me") { bearerAuth("not-a-token") } },
        )
        for (request in requests) assertError(request(), HttpStatusCode.Unauthorized, "unauthorized")
    }

    @Test
    fun `optional endpoints work anonymously`() = loopTest {
        val user = register("anna")
        val video = postVideo(user, "hello")
        assertEquals(HttpStatusCode.OK, client.get("/api/feed/for-you").status)
        assertEquals(HttpStatusCode.OK, client.get("/api/videos/${video.id}").status)
        assertEquals(HttpStatusCode.OK, client.get("/api/users/anna").status)
        assertEquals(HttpStatusCode.OK, client.get("/api/discover").status)
        assertEquals(HttpStatusCode.OK, client.get("/api/search?q=an").status)
        assertEquals(HttpStatusCode.NoContent, client.post("/api/videos/${video.id}/views") {
            contentType(ContentType.Application.Json)
            setBody("""{"watchedMs": 1000, "completed": false}""")
        }.status)
    }
}
