package com.ndizigiye.loop.server

import com.ndizigiye.loop.api.CreateCommentRequest
import com.ndizigiye.loop.api.HashtagDto
import com.ndizigiye.loop.api.LoginRequest
import com.ndizigiye.loop.api.RegisterRequest
import com.ndizigiye.loop.api.SearchResultDto
import com.ndizigiye.loop.api.UpdateProfileRequest
import com.ndizigiye.loop.api.ViewEventRequest
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Behaviour that SQLite's single database thread used to guarantee for free. On PostgreSQL the
 * requests below run in concurrent transactions; both backends must give the same results.
 */
class ConcurrencyTest {
    @Test
    fun `concurrent sign-ups for one name create one account`() = loopTest {
        val statuses = coroutineScope {
            (1..8).map {
                async {
                    client.post("/api/auth/register") {
                        contentType(ContentType.Application.Json)
                        setBody(RegisterRequest("same.name", "Same", "secret123"))
                    }.status
                }
            }.awaitAll()
        }
        assertEquals(1, statuses.count { it == HttpStatusCode.Created }, statuses.toString())
        assertEquals(7, statuses.count { it == HttpStatusCode.Conflict }, statuses.toString())
        assertEquals(1, queryLong("SELECT count(*) FROM users"))
    }

    @Test
    fun `concurrent views, likes and shares all count`() = loopTest {
        val creator = register("creator")
        val viewer = register("viewer")
        val fans = (1..6).map { register("fan$it") }
        val video = postVideo(creator, "#busy")
        coroutineScope {
            repeat(20) { index ->
                launch {
                    client.post("/api/videos/${video.id}/views") {
                        bearerAuth(viewer.token)
                        contentType(ContentType.Application.Json)
                        setBody(ViewEventRequest(watchedMs = 100, completed = index == 0))
                    }
                }
            }
            fans.forEach { fan -> launch { like(fan, video.id) } }
            repeat(5) { launch { client.post("/api/videos/${video.id}/share") } }
        }
        val counted = video(video.id)
        assertEquals(20, counted.viewCount)
        assertEquals(6, counted.likeCount)
        assertEquals(5, counted.shareCount)
        assertEquals(2_000, queryLong("SELECT watched_ms FROM view_history WHERE user_id = ${viewer.id}"), "watch time adds up")
        assertEquals(1, queryLong("SELECT count(*) FROM view_history WHERE completed"))
    }

    @Test
    fun `concurrent uploads share new hashtags`() = loopTest {
        val makers = (1..4).map { register("maker$it") }
        coroutineScope { makers.map { maker -> async { postVideo(maker, "#fresh #common #${maker.username}") } }.awaitAll() }
        assertEquals(4, client.get("/api/hashtags/fresh").body<HashtagDto>().videoCount)
        assertEquals(4, client.get("/api/hashtags/common").body<HashtagDto>().videoCount)
        assertEquals(1, queryLong("SELECT count(*) FROM hashtags WHERE name = 'fresh'"))
    }
}

/** U+0000 cannot be stored in PostgreSQL text, so neither backend accepts or looks it up. */
class StoredTextTest {
    @Test
    fun `text with NUL characters is rejected or finds nothing`() = loopTest {
        val user = register("nul.tester")
        val video = postVideo(user, "fine")
        val badComment = client.post("/api/videos/${video.id}/comments") {
            bearerAuth(user.token)
            contentType(ContentType.Application.Json)
            setBody(CreateCommentRequest("bad\u0000text"))
        }
        assertError(badComment, HttpStatusCode.BadRequest, "validation")
        for (request in listOf(UpdateProfileRequest(bio = "a\u0000b"), UpdateProfileRequest(displayName = "A\u0000"))) {
            val response = client.patch("/api/me") {
                bearerAuth(user.token)
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            assertError(response, HttpStatusCode.BadRequest, "validation")
        }
        val badName = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("other_name", "Na\u0000me", "secret123"))
        }
        assertError(badName, HttpStatusCode.BadRequest, "validation")
        assertError(upload(user, caption = "x\u0000y"), HttpStatusCode.BadRequest, "validation")
        val login = client.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest("nul.tester\u0000", "secret123"))
        }
        assertError(login, HttpStatusCode.Unauthorized, "invalid_credentials")
        assertError(client.get("/api/users/nul.tester%00"), HttpStatusCode.NotFound, "not_found")
        assertError(client.get("/api/hashtags/fine%00"), HttpStatusCode.NotFound, "not_found")
        assertEquals(SearchResultDto(), client.get("/api/search?q=nul%00").body<SearchResultDto>())
        assertEquals(1, queryLong("SELECT count(*) FROM videos"), "the rejected upload left no row")
    }
}
