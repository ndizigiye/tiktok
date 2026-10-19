package com.ndizigiye.loop.server

import com.ndizigiye.loop.api.HashtagDto
import com.ndizigiye.loop.api.SearchResultDto
import com.ndizigiye.loop.api.SoundDto
import com.ndizigiye.loop.api.TrendingDto
import com.ndizigiye.loop.api.VideoDto
import com.ndizigiye.loop.api.ViewEventRequest
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.encodeURLParameter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DiscoverTest {
    private suspend fun LoopTest.view(videoId: Long, times: Int) = repeat(times) {
        client.post("/api/videos/$videoId/views") {
            contentType(ContentType.Application.Json)
            setBody(ViewEventRequest(1000, completed = false))
        }
    }

    private suspend fun LoopTest.search(q: String, limit: Int? = null, viewer: TestUser? = null): SearchResultDto =
        client.get("/api/search?q=${q.encodeURLParameter()}" + (limit?.let { "&limit=$it" } ?: "")) { auth(viewer) }.body()

    @Test
    fun `search matches users, hashtags and captions`() = loopTest {
        val baker = register("bread.baker", "Sourdough Sam")
        val other = register("someone", "Bakery Fan")
        val video = postVideo(baker, "Fresh loaf #Baking #bread_art")
        postVideo(other, "Nothing to see")

        val users = search("BAK").users.map { it.username }
        assertEquals(setOf("bread.baker", "someone"), users.toSet())
        assertEquals(listOf("someone"), search("fan").users.map { it.username }, "display names match too")
        assertEquals(listOf("bread.baker"), search("@bread").users.map { it.username })

        assertEquals(listOf(HashtagDto("baking", 1, 0)), search("#bak").hashtags)
        assertEquals(listOf("bread_art"), search("_art").hashtags.map { it.name }, "LIKE wildcards are literal")
        assertEquals(listOf(video.id), search("fresh LOAF").videos.map { it.id })
        assertEquals(SearchResultDto(), search("   "))
        assertEquals(SearchResultDto(), client.get("/api/search").body<SearchResultDto>())

        repeat(3) { register("bakers$it") }
        assertEquals(2, search("bake", limit = 2).users.size)
        assertEquals("bread.baker", search("bread.baker").users.first().username, "exact match first")
    }

    @Test
    fun `hashtag and sound pages`() = loopTest {
        val creator = register("tagger")
        val popular = postVideo(creator, "popular #Cats")
        val quiet = postVideo(creator, "quiet #cats #dogs")
        view(popular.id, 3)
        view(quiet.id, 1)

        assertEquals(HashtagDto("cats", 2, 4), client.get("/api/hashtags/Cats").body<HashtagDto>())
        assertEquals(HashtagDto("cats", 2, 4), client.get("/api/hashtags/%23cats").body<HashtagDto>())
        assertError(client.get("/api/hashtags/unused"), HttpStatusCode.NotFound, "not_found")
        assertEquals(listOf(popular.id, quiet.id), allPages<VideoDto>("/api/hashtags/cats/videos", limit = 1).map { it.id })
        assertTrue(page<VideoDto>("/api/hashtags/unused/videos").items.isEmpty())

        val remix = postVideo(register("remixer"), "remix", soundId = quiet.sound.id)
        view(remix.id, 5)
        val sound = client.get("/api/sounds/${quiet.sound.id}").body<SoundDto>()
        assertEquals(2, sound.videoCount)
        assertEquals("original sound - Tagger", sound.title)
        assertEquals(listOf(remix.id, quiet.id), allPages<VideoDto>("/api/sounds/${quiet.sound.id}/videos", limit = 1).map { it.id })
        assertError(client.get("/api/sounds/999"), HttpStatusCode.NotFound, "not_found")
        assertError(client.get("/api/sounds/999/videos"), HttpStatusCode.NotFound, "not_found")
    }

    @Test
    fun `discover ranks hashtags, sounds and creators`() = loopTest {
        val big = register("big")
        val mid = register("mid")
        val viewer = register("looker")
        val fans = (1..3).map { register("fan$it") }
        fans.forEach { follow(it, big) }
        follow(fans[0], mid)
        follow(viewer, mid)

        val a = postVideo(big, "#alpha #shared")
        val b = postVideo(mid, "#beta #shared", soundId = a.sound.id)
        postVideo(mid, "#gamma")
        view(a.id, 5)
        view(b.id, 2)

        val trending = client.get("/api/discover").body<TrendingDto>()
        assertEquals(listOf("shared", "alpha", "beta", "gamma"), trending.hashtags.map { it.hashtag.name })
        assertEquals(HashtagDto("shared", 2, 7), trending.hashtags.first().hashtag)
        assertEquals(listOf(a.id, b.id), trending.hashtags.first().videos.map { it.id })
        assertEquals(a.sound.id, trending.sounds.first().id)
        assertEquals(2, trending.sounds.first().videoCount)
        assertEquals("big", trending.creators.first().username)

        val personal = client.get("/api/discover") { auth(viewer) }.body<TrendingDto>()
        val suggested = personal.creators.map { it.username }
        assertTrue("looker" !in suggested && "mid" !in suggested, "excludes the viewer and accounts they follow")
        assertEquals("big", suggested.first())
    }
}
