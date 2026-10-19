package com.ndizigiye.loop.server

import com.ndizigiye.loop.server.auth.PasswordHasher
import com.ndizigiye.loop.server.feed.Candidate
import com.ndizigiye.loop.server.feed.ForYouRanker
import com.ndizigiye.loop.server.feed.ViewerSignals
import com.ndizigiye.loop.server.media.MediaStaging
import com.ndizigiye.loop.server.media.MediaTools
import com.ndizigiye.loop.server.media.VideoProbe
import com.ndizigiye.loop.server.paging.Cursors
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ForYouRankerTest {
    private val now = 1_700_000_000_000L

    @Test
    fun `jitter is deterministic and bounded`() {
        val values = (1L..500L).map { ForYouRanker.jitter(viewerId = 7, seed = 42, videoId = it) }
        assertTrue(values.all { it >= 0.0 && it < 0.3 })
        assertEquals(values, (1L..500L).map { ForYouRanker.jitter(7, 42, it) })
        assertNotEquals(values, (1L..500L).map { ForYouRanker.jitter(7, 43, it) })
        assertNotEquals(values, (1L..500L).map { ForYouRanker.jitter(8, 42, it) })
    }

    @Test
    fun `score follows the documented formula`() {
        val video = Candidate(
            id = 1, authorId = 9, createdAt = now - 48 * 3_600_000L,
            likes = 4, comments = 1, shares = 1, bookmarks = 1, completions = 2, views = 10,
            hashtagIds = setOf(1, 2),
        )
        val viewer = ViewerSignals(viewerId = 3, followedAuthors = setOf(9), likedHashtagIds = setOf(2), watchedVideoIds = setOf(1))
        val engagement = (4 + 2.0 + 3.0 + 2.0 + 2) / 20.0
        val expected = engagement * 0.5 + 0.6 + 0.4 * 0.5 - 0.8 + ForYouRanker.jitter(3, 99, 1)
        assertEquals(expected, ForYouRanker.score(video, viewer, seed = 99, now = now), 1e-9)
    }

    @Test
    fun `consecutive authors differ whenever possible`() {
        // Scores favour author 1, which would otherwise take the first three slots.
        val candidates = (1L..9L).map { id ->
            Candidate(id = id, authorId = (id - 1) / 3 + 1, createdAt = now, likes = 100 - id, views = 0)
        }
        repeat(20) { seed ->
            val ranked = ForYouRanker.rank(candidates, ViewerSignals(), seed.toLong(), now)
            assertEquals(candidates.map { it.id }.toSet(), ranked.map { it.id }.toSet())
            ranked.zipWithNext().forEach { (a, b) -> assertNotEquals(a.authorId, b.authorId) }
        }
        val single = (1L..3L).map { Candidate(id = it, authorId = 5, createdAt = now) }
        assertEquals(3, ForYouRanker.rank(single, ViewerSignals(), 1, now).size, "unavoidable repeats are kept")
    }

    @Test
    fun `next seeds differ`() {
        assertNotEquals(5L, ForYouRanker.nextSeed(5L))
        assertEquals(ForYouRanker.nextSeed(5L), ForYouRanker.nextSeed(5L))
    }
}

class PasswordHasherTest {
    @Test
    fun `hashes are salted and verify`() {
        val a = PasswordHasher.hash("correct horse")
        val b = PasswordHasher.hash("correct horse")
        assertNotEquals(a.salt, b.salt)
        assertNotEquals(a.hash, b.hash)
        assertTrue(PasswordHasher.verify("correct horse", a.hash, a.salt))
        assertFalse(PasswordHasher.verify("correct horse!", a.hash, a.salt))
    }
}

class CursorsTest {
    @Test
    fun `cursors round-trip and reject other kinds`() {
        assertEquals(Cursors.Key(123, 4), Cursors.parseKey(Cursors.key(123, 4)))
        assertEquals(20, Cursors.parseOffset(Cursors.offset(20)))
        val feed = Cursors.Feed(seed = -77, offset = 3, avoidAuthor = 2)
        assertEquals(feed, Cursors.parseFeed(Cursors.feed(feed)))
        assertFailsWith<ApiException> { Cursors.parseKey(Cursors.offset(1)) }
        assertFailsWith<ApiException> { Cursors.parseOffset("!!") }
    }
}

class ProbeParsingTest {
    @Test
    fun `rotation swaps the display size`() {
        val json = """
            {"streams":[{"width":1920,"height":1080,"side_data_list":[{"rotation":-90}]}],
             "format":{"duration":"3.250000"}}
        """.trimIndent()
        assertEquals(VideoProbe(3250, 1080, 1920), MediaTools.parseProbe(json))
        val plain = """{"streams":[{"width":540,"height":960,"tags":{"rotate":"0"}}],"format":{"duration":"8.0"}}"""
        assertEquals(VideoProbe(8000, 540, 960), MediaTools.parseProbe(plain))
    }
}

class MediaStagingTest {
    @Test
    fun `files a crash left behind are removed at startup`() {
        val dir = Files.createTempDirectory("loop-staging-test").toFile()
        try {
            val stale = dir.resolve("loop-1.mp4").apply { writeText("old") }
            stale.setLastModified(System.currentTimeMillis() - 7 * 3_600_000L)
            val recent = dir.resolve("loop-2.mp4").apply { writeText("in progress") }
            val staging = MediaStaging(dir)
            assertFalse(stale.exists())
            assertTrue(recent.exists(), "another process may still be using it")
            val created = staging.newFile("jpg")
            assertTrue(created.isFile && created.name.startsWith("loop-") && created.name.endsWith(".jpg"))
            assertEquals(setOf(recent, created), staging.files().toSet())
        } finally {
            dir.deleteRecursively()
        }
    }
}
