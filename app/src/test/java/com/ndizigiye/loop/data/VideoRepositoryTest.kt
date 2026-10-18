package com.ndizigiye.loop.data

import com.ndizigiye.loop.api.CountDto
import com.ndizigiye.loop.api.PageDto
import com.ndizigiye.loop.api.ToggleResultDto
import com.ndizigiye.loop.api.VideoDto
import com.ndizigiye.loop.api.ViewEventRequest
import com.ndizigiye.loop.data.remote.ApiException
import com.ndizigiye.loop.data.remote.LoopJson
import com.ndizigiye.loop.testutil.TestBackend
import com.ndizigiye.loop.testutil.errorResponse
import com.ndizigiye.loop.testutil.formParts
import com.ndizigiye.loop.testutil.jsonResponse
import com.ndizigiye.loop.testutil.video
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.SocketPolicy
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VideoRepositoryTest {

    private val backend = TestBackend()
    private val repository get() = backend.container.videoRepository
    private val interactions get() = backend.interactions

    @BeforeTest
    fun signIn() = backend.signIn()

    @AfterTest
    fun tearDown() = backend.close()

    /** Answers with [response] and records the stored stats of [video] as the request arrives. */
    private fun respondRecordingStats(video: VideoDto, response: MockResponse): AtomicReference<VideoStats> {
        val seen = AtomicReference<VideoStats>()
        backend.respondWith {
            seen.set(interactions.statsFor(video))
            response
        }
        return seen
    }

    @Test
    fun `a like shows at once and settles on the server's count`() = runTest {
        val clip = video(id = 7, likeCount = 5)
        val duringRequest = respondRecordingStats(clip, jsonResponse(ToggleResultDto(active = true, count = 42)))

        val result = repository.setLiked(clip, liked = true)

        assertEquals(ToggleResultDto(active = true, count = 42), result.getOrThrow())
        assertEquals(true to 6L, duringRequest.get().let { it.liked to it.likeCount })
        assertEquals(true to 42L, interactions.statsFor(clip).let { it.liked to it.likeCount })
        val request = backend.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/loop/api/videos/7/like", request.path)
        assertEquals("Bearer ${TestBackend.TOKEN}", request.getHeader("Authorization"))
    }

    @Test
    fun `an unlike sends DELETE and settles on the server's count`() = runTest {
        val clip = video(id = 7, likeCount = 5, likedByMe = true)
        val duringRequest = respondRecordingStats(clip, jsonResponse(ToggleResultDto(active = false, count = 3)))

        repository.setLiked(clip, liked = false).getOrThrow()

        assertEquals(false to 4L, duringRequest.get().let { it.liked to it.likeCount })
        assertEquals(false to 3L, interactions.statsFor(clip).let { it.liked to it.likeCount })
        val request = backend.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/loop/api/videos/7/like", request.path)
    }

    @Test
    fun `liking a video that is already liked does not count it twice`() = runTest {
        val clip = video(id = 7, likeCount = 5, likedByMe = true)
        val duringRequest = respondRecordingStats(clip, jsonResponse(ToggleResultDto(active = true, count = 5)))

        repository.setLiked(clip, liked = true).getOrThrow()

        assertEquals(5, duringRequest.get().likeCount)
        assertEquals(5, interactions.statsFor(clip).likeCount)
    }

    @Test
    fun `a failed like restores the like and keeps other changes made meanwhile`() = runTest {
        val clip = video(id = 7, likeCount = 5, commentCount = 2)
        backend.respondWith {
            interactions.update(clip) { it.copy(commentCount = 3) }
            errorResponse(500, "internal", "Something broke on our side.")
        }

        val error = repository.setLiked(clip, liked = true).exceptionOrNull()

        assertIs<ApiException>(error)
        assertEquals(500, error.status)
        assertEquals("internal", error.code)
        val after = interactions.statsFor(clip)
        assertFalse(after.liked)
        assertEquals(5, after.likeCount)
        assertEquals(3, after.commentCount)
    }

    @Test
    fun `a like that cannot reach the server is reverted`() = runTest {
        val clip = video(id = 7, likeCount = 5)
        backend.respondWith { MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST) }

        val error = repository.setLiked(clip, liked = true).exceptionOrNull()

        assertIs<ApiException>(error)
        assertEquals(0, error.status)
        assertEquals("network", error.code)
        assertEquals(VideoStats.from(clip), interactions.statsFor(clip))
    }

    @Test
    fun `a page loaded while a like is in flight does not undo it`() = runTest {
        val clip = video(id = 7, likeCount = 5)
        val duringRequest = AtomicReference<VideoStats>()
        backend.respondWith {
            interactions.remember(listOf(clip))
            duringRequest.set(interactions.statsFor(clip))
            jsonResponse(ToggleResultDto(active = true, count = 6))
        }

        repository.setLiked(clip, liked = true).getOrThrow()

        assertTrue(duringRequest.get().liked)
        assertEquals(6, duringRequest.get().likeCount)
        // Once the like has settled, pages record server state again.
        interactions.remember(listOf(clip.copy(likeCount = 50, likedByMe = true)))
        assertEquals(50, interactions.statsFor(clip).likeCount)
    }

    @Test
    fun `a bookmark shows at once and settles on the server's count`() = runTest {
        val clip = video(id = 8, bookmarkCount = 1)
        val duringRequest = respondRecordingStats(clip, jsonResponse(ToggleResultDto(active = true, count = 10)))

        repository.setBookmarked(clip, bookmarked = true).getOrThrow()

        assertEquals(true to 2L, duringRequest.get().let { it.bookmarked to it.bookmarkCount })
        assertEquals(true to 10L, interactions.statsFor(clip).let { it.bookmarked to it.bookmarkCount })
        val request = backend.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/loop/api/videos/8/bookmark", request.path)
    }

    @Test
    fun `a failed bookmark removal restores the bookmark`() = runTest {
        val clip = video(id = 8, bookmarkCount = 1, bookmarkedByMe = true, likeCount = 4)
        val duringRequest = respondRecordingStats(clip, errorResponse(404, "not_found", "Video not found."))

        val error = repository.setBookmarked(clip, bookmarked = false).exceptionOrNull()

        assertEquals("Video not found.", (error as ApiException).message)
        assertEquals(false to 0L, duringRequest.get().let { it.bookmarked to it.bookmarkCount })
        assertEquals(VideoStats.from(clip), interactions.statsFor(clip))
        assertEquals("DELETE", backend.takeRequest().method)
    }

    @Test
    fun `feed pages use the right endpoint and are remembered`() = runTest {
        val page = PageDto(
            items = listOf(video(id = 1, likeCount = 3), video(id = 2, likeCount = 1, likedByMe = true)),
            nextCursor = "c2",
        )
        backend.enqueue(page)
        backend.enqueue(PageDto(emptyList<VideoDto>()))

        assertEquals(page, repository.feed(FeedType.FOR_YOU, cursor = null).getOrThrow())
        repository.feed(FeedType.FOLLOWING, cursor = "c2").getOrThrow()

        assertEquals("/loop/api/feed/for-you", backend.takeRequest().path)
        assertEquals("/loop/api/feed/following?cursor=c2", backend.takeRequest().path)
        assertEquals(VideoStats.from(page.items[1]), interactions.videos.value[2])
        assertEquals(false, interactions.following.value[page.items[0].author.id])
    }

    @Test
    fun `hashtag videos are requested by lowercase name without the hash`() = runTest {
        backend.enqueue(PageDto(emptyList<VideoDto>()))

        repository.hashtagVideos("#Dance", cursor = "x").getOrThrow()

        assertEquals("/loop/api/hashtags/dance/videos?cursor=x", backend.takeRequest().path)
    }

    @Test
    fun `sharing stores the server's share count`() = runTest {
        val clip = video(id = 7, shareCount = 2)
        backend.enqueue(CountDto(12))

        assertEquals(12, repository.share(clip).getOrThrow().count)

        assertEquals(12, interactions.statsFor(clip).shareCount)
        val request = backend.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/loop/api/videos/7/share", request.path)
    }

    @Test
    fun `a view report accepts 204 and never sends a negative watch time`() = runTest {
        backend.enqueueNoContent()

        repository.recordView(videoId = 7, watchedMs = -40, completed = true).getOrThrow()

        val request = backend.takeRequest()
        assertEquals("/loop/api/videos/7/views", request.path)
        val body = LoopJson.decodeFromString(ViewEventRequest.serializer(), request.body.readUtf8())
        assertEquals(ViewEventRequest(watchedMs = 0, completed = true), body)
    }

    @Test
    fun `deleting a video maps 204 to success and 403 to an ApiException`() = runTest {
        backend.enqueueNoContent()
        backend.enqueueError(403, "forbidden", "Only the author can delete this video.")

        assertTrue(repository.delete(7).isSuccess)
        val error = repository.delete(8).exceptionOrNull()

        assertIs<ApiException>(error)
        assertEquals(403, error.status)
        assertEquals("forbidden", error.code)
        assertEquals("DELETE", backend.takeRequest().method)
    }

    @Test
    fun `upload sends the video, caption and sound as multipart parts and reports progress`() = runTest {
        val bytes = Random(seed = 1).nextBytes(100_000)
        val file = tempFile(".MOV", bytes)
        val posted = video(id = 99)
        backend.enqueue(posted, code = 201)
        val progress = CopyOnWriteArrayList<Float>()

        val result = repository.upload(file, caption = "  Sunset #run  ", soundId = 5) { progress += it }

        assertEquals(posted, result.getOrThrow())
        val request = backend.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/loop/api/videos", request.path)
        val parts = request.formParts()
        assertEquals(listOf("video", "caption", "soundId"), parts.map { it.name })
        val videoPart = parts[0]
        assertEquals(file.name, videoPart.filename)
        assertEquals("video/quicktime", videoPart.contentType)
        assertContentEquals(bytes, videoPart.bytes)
        assertEquals("Sunset #run", parts[1].text)
        assertEquals("5", parts[2].text)
        assertTrue(progress.isNotEmpty())
        assertTrue(progress.all { it in 0f..1f }, "progress out of range: $progress")
        assertTrue(progress.zipWithNext().all { (a, b) -> a < b }, "progress not increasing: $progress")
        assertEquals(1f, progress.last())
        assertEquals(VideoStats.from(posted), interactions.videos.value[99])
    }

    @Test
    fun `upload without a sound leaves out the soundId part`() = runTest {
        val file = tempFile(".mp4", ByteArray(10))
        backend.enqueue(video(id = 99), code = 201)

        repository.upload(file, caption = "", soundId = null) {}.getOrThrow()

        val parts = backend.takeRequest().formParts()
        assertEquals(listOf("video", "caption"), parts.map { it.name })
        assertEquals("video/mp4", parts[0].contentType)
        assertEquals("", parts[1].text)
    }

    @Test
    fun `a rejected upload reports the server's message`() = runTest {
        val file = tempFile(".mp4", ByteArray(10))
        backend.enqueueError(413, "too_large", "Videos can be at most 200 MB.")

        val error = repository.upload(file, caption = "x", soundId = null) {}.exceptionOrNull()

        assertIs<ApiException>(error)
        assertEquals(413, error.status)
        assertEquals("Videos can be at most 200 MB.", error.message)
        assertNull(interactions.videos.value[99])
    }

    private fun tempFile(suffix: String, bytes: ByteArray): File =
        File.createTempFile("loop-test", suffix).apply {
            deleteOnExit()
            writeBytes(bytes)
        }
}
