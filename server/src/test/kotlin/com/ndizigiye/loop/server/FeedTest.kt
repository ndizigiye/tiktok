package com.ndizigiye.loop.server

import com.ndizigiye.loop.api.PageDto
import com.ndizigiye.loop.api.VideoDto
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FeedTest {
    private suspend fun LoopTest.forYou(viewer: TestUser?, cursor: String?, limit: Int): PageDto<VideoDto> =
        client.get("/api/feed/for-you?limit=$limit" + (cursor?.let { "&cursor=$it" } ?: "")) {
            viewer?.let { bearerAuth(it.token) }
        }.body()

    /** Reads [count] items from the For You feed, page by page. */
    private suspend fun LoopTest.readForYou(viewer: TestUser?, count: Int, limit: Int): List<VideoDto> {
        val items = mutableListOf<VideoDto>()
        var cursor: String? = null
        while (items.size < count) {
            val page = forYou(viewer, cursor, limit)
            assertTrue(page.items.isNotEmpty(), "pages are never empty while videos exist")
            items += page.items
            cursor = assertNotNull(page.nextCursor, "the feed never ends while videos exist")
        }
        return items
    }

    @Test
    fun `for you serves every candidate once per pass, then starts a new pass`() = loopTest {
        val creators = listOf(register("ava"), register("bob"), register("cam"))
        val viewer = register("viewer")
        val expected = creators.flatMap { creator -> (1..3).map { postVideo(creator, "${creator.username} $it").id } }.toSet()
        val own = postVideo(viewer, "my own")

        val firstPass = mutableListOf<VideoDto>()
        var cursor: String? = null
        while (firstPass.size < expected.size) {
            val page = forYou(viewer, cursor, limit = 4)
            firstPass += page.items
            cursor = assertNotNull(page.nextCursor)
        }
        assertEquals(expected.size, firstPass.size, "no duplicates within a pass")
        assertEquals(expected, firstPass.map { it.id }.toSet())
        assertTrue(own.id !in firstPass.map { it.id }, "own videos are excluded")
        firstPass.zipWithNext().forEach { (a, b) ->
            assertTrue(a.author.id != b.author.id, "consecutive videos come from different authors when possible")
        }

        val secondPass = readForYou(viewer, expected.size, limit = 4).take(expected.size)
        assertEquals(expected, secondPass.map { it.id }.toSet(), "the next pass serves the candidates again")
    }

    @Test
    fun `for you pages are stable for a cursor`() = loopTest {
        val creator = register("dot")
        repeat(5) { postVideo(creator, "clip $it") }
        val first = forYou(null, null, limit = 2)
        val cursor = assertNotNull(first.nextCursor)
        assertEquals(forYou(null, cursor, 2).items.map { it.id }, forYou(null, cursor, 2).items.map { it.id })
    }

    @Test
    fun `not interested videos leave the viewer's feed`() = loopTest {
        val creator = register("eve")
        val viewer = register("finn")
        val boring = postVideo(creator, "boring")
        val keep = (1..3).map { postVideo(creator, "keep $it").id }.toSet()
        val response = client.post("/api/videos/${boring.id}/not-interested") { bearerAuth(viewer.token) }
        assertEquals(HttpStatusCode.NoContent, response.status)

        val seen = readForYou(viewer, count = 6, limit = 2).map { it.id }
        assertTrue(boring.id !in seen)
        assertEquals(keep, seen.toSet())
        assertTrue(boring.id in readForYou(null, count = 4, limit = 4).map { it.id }, "others still see it")
    }

    @Test
    fun `for you is empty without candidates`() = loopTest {
        val solo = register("solo")
        postVideo(solo, "only mine")
        val page = forYou(solo, null, limit = 5)
        assertTrue(page.items.isEmpty())
        assertNull(page.nextCursor)
    }

    @Test
    fun `following feed shows followed creators, newest first`() = loopTest {
        val gia = register("gia")
        val hugo = register("hugo")
        val stranger = register("stranger")
        val viewer = register("iris")
        val g1 = postVideo(gia, "g1")
        val h1 = postVideo(hugo, "h1")
        postVideo(stranger, "s1")
        val g2 = postVideo(gia, "g2")
        follow(viewer, gia)
        follow(viewer, hugo)

        val feed = allPages<VideoDto>("/api/feed/following", viewer, limit = 2)
        assertEquals(listOf(g2.id, h1.id, g1.id), feed.map { it.id })

        client.delete("/api/users/hugo/follow") { bearerAuth(viewer.token) }
        assertEquals(listOf(g2.id, g1.id), allPages<VideoDto>("/api/feed/following", viewer).map { it.id })
    }

    @Test
    fun `invalid cursors and limits are rejected`() = loopTest {
        assertError(client.get("/api/feed/for-you?cursor=garbage"), HttpStatusCode.BadRequest, "validation")
        assertError(client.get("/api/feed/for-you?limit=many"), HttpStatusCode.BadRequest, "validation")
        val creator = register("jay")
        repeat(3) { postVideo(creator) }
        assertEquals(3, page<VideoDto>("/api/users/jay/videos?limit=500").items.size)
    }
}
