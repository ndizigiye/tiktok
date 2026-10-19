package com.ndizigiye.loop.server

import com.ndizigiye.loop.server.db.MetaStore
import com.ndizigiye.loop.server.db.Users
import com.ndizigiye.loop.server.db.Videos
import com.ndizigiye.loop.server.seed.DemoContent
import com.ndizigiye.loop.server.seed.DemoSeeder
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.selectAll
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Demo seeding through both media stores, with two short clips instead of all of them. */
class SeedingTest {
    private val clips = DemoContent.clips.sortedBy { it.seconds }.take(2)
    private val avatarNames = DemoContent.users.filter { it.avatarGradient != null }.map { "demo-${it.username.replace('.', '-')}.jpg" }

    @Test
    fun `demo media goes to Supabase Storage and an interrupted run resumes`() = seedingTest(FakeSupabaseStorage()) { services, storage ->
        storage!!
        DemoSeeder(services, clips.take(1)).seedIfNeeded()
        assertEquals("done", services.db.tx { MetaStore.get("demo_seed") })
        assertEquals(DemoContent.users.size.toLong(), services.db.tx { Users.selectAll().count() })
        if (!TestMedia.hasFfmpeg) {
            assertTrue(storage.objects.isEmpty())
            return@seedingTest
        }

        // As if the previous run stopped after storing the second clip but before inserting its row.
        services.db.tx { MetaStore.put("demo_seed", "running") }
        val second = "videos/${clips[1].fileName}"
        storage.objects[second] = FakeSupabaseStorage.StoredObject(byteArrayOf(0), "video/mp4", null)
        DemoSeeder(services, clips).seedIfNeeded()

        val expected = clips.flatMap { listOf("videos/${it.fileName}", "thumbs/${it.fileName.removeSuffix(".mp4")}.jpg") } +
            avatarNames.map { "avatars/$it" }
        assertEquals(expected.toSet(), storage.objects.keys)
        assertTrue(storage.objects.getValue(second).bytes.size > 1_000, "the interrupted upload was replaced")
        val uploads = storage.requests("POST")
        assertTrue(uploads.all { it.authorized && it.upsert == "true" }, "fixed demo names replace earlier copies")
        assertEquals(1, uploads.count { it.path.endsWith("/videos/${clips[0].fileName}") }, "seeded clips are not uploaded again")
        assertEquals(avatarNames.size, uploads.count { "/avatars/" in it.path }, "avatars only once")
        assertEquals("video/mp4", storage.objects.getValue(second).contentType)

        for (clip in clips) {
            val video = services.videos.get(videoId(services, clip.fileName), viewerId = null)
            assertEquals(storage.publicUrl("videos/${clip.fileName}"), video.videoUrl)
            assertEquals(storage.publicUrl("thumbs/${clip.fileName.removeSuffix(".mp4")}.jpg"), video.thumbnailUrl)
            assertTrue(video.durationMs >= clip.seconds * 1_000L - 100, "probed locally: ${video.durationMs} ms")
        }
        val mira = services.users.byUsername("mira.makes", viewerId = null)
        assertEquals(storage.publicUrl("avatars/demo-mira-makes.jpg"), mira.avatarUrl)
        assertFalse(File(services.config.dataDir, "videos").exists())
        assertEquals(emptyList(), services.staging.files())
    }

    @Test
    fun `demo media goes to the data directory without Supabase`() = seedingTest(null) { services, _ ->
        DemoSeeder(services, clips).seedIfNeeded()
        assertEquals("done", services.db.tx { MetaStore.get("demo_seed") })
        if (!TestMedia.hasFfmpeg) return@seedingTest
        val dataDir = services.config.dataDir
        for (clip in clips) {
            assertTrue(File(dataDir, "videos/${clip.fileName}").length() > 1_000)
            assertTrue(File(dataDir, "thumbs/${clip.fileName.removeSuffix(".mp4")}.jpg").isFile)
            val video = services.videos.get(videoId(services, clip.fileName), viewerId = null)
            assertEquals("/media/videos/${clip.fileName}", video.videoUrl)
        }
        assertEquals(avatarNames.toSet(), File(dataDir, "avatars").list()!!.toSet())
        assertEquals(emptyList(), services.staging.files())
    }

    private suspend fun videoId(services: LoopServices, fileName: String): Long = services.db.tx {
        Videos.select(Videos.id).where { Videos.videoFile eq fileName }.single()[Videos.id].value
    }

    /** A server's services (no HTTP) on a fresh data directory and database. */
    private fun seedingTest(storage: FakeSupabaseStorage?, block: suspend (LoopServices, FakeSupabaseStorage?) -> Unit) {
        val dataDir = Files.createTempDirectory("loop-seed-test").toFile()
        val postgres = if (PostgresTests.enabled) PostgresTests.createDatabase() else null
        try {
            runBlocking {
                val services = LoopServices.open(testConfig(dataDir, postgres, storage))
                try {
                    block(services, storage)
                } finally {
                    services.close()
                }
            }
        } finally {
            postgres?.let(PostgresTests::dropDatabase)
            storage?.close()
            dataDir.deleteRecursively()
        }
    }
}
