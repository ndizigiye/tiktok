package com.ndizigiye.loop.data

import com.ndizigiye.loop.api.CreateCommentRequest
import com.ndizigiye.loop.api.PageDto
import com.ndizigiye.loop.data.remote.ApiException
import com.ndizigiye.loop.data.remote.LoopJson
import com.ndizigiye.loop.testutil.TestBackend
import com.ndizigiye.loop.testutil.comment
import com.ndizigiye.loop.testutil.user
import com.ndizigiye.loop.testutil.video
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.RecordedRequest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class CommentRepositoryTest {

    private val backend = TestBackend()
    private val repository get() = backend.container.commentRepository
    private val interactions get() = backend.interactions

    private val clip = video(id = 7, commentCount = 5)

    @BeforeTest
    fun signIn() = backend.signIn()

    @AfterTest
    fun tearDown() = backend.close()

    private fun commentCount() = interactions.statsFor(clip).commentCount

    @Test
    fun `posting a comment sends the trimmed text and adds one to the count`() = runTest {
        val posted = comment(id = 30, videoId = 7, text = "Nice one")
        backend.enqueue(posted, code = 201)

        assertEquals(posted, repository.post(clip, "  Nice one ", parentId = null).getOrThrow())

        assertEquals(6, commentCount())
        val request = backend.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/loop/api/videos/7/comments", request.path)
        assertEquals(CreateCommentRequest("Nice one"), request.decodeComment())
    }

    @Test
    fun `a reply carries its parent id and also adds one to the count`() = runTest {
        backend.enqueue(comment(id = 31, videoId = 7, parentId = 30), code = 201)

        repository.post(clip, "Agreed", parentId = 30).getOrThrow()

        assertEquals(6, commentCount())
        assertEquals(CreateCommentRequest("Agreed", parentId = 30), backend.takeRequest().decodeComment())
    }

    @Test
    fun `a rejected comment leaves the count alone`() = runTest {
        backend.enqueueError(400, "validation", "Comments can be at most 300 characters.")

        val error = repository.post(clip, "x".repeat(301), parentId = null).exceptionOrNull()

        assertIs<ApiException>(error)
        assertEquals("Comments can be at most 300 characters.", error.message)
        assertEquals(5, commentCount())
    }

    @Test
    fun `deleting a comment removes it and its replies from the count`() = runTest {
        backend.enqueueNoContent()

        repository.delete(clip, comment(id = 30, videoId = 7, replyCount = 2)).getOrThrow()

        assertEquals(2, commentCount())
        val request = backend.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/loop/api/comments/30", request.path)
    }

    @Test
    fun `deleting a reply removes one from the count`() = runTest {
        backend.enqueueNoContent()

        repository.delete(clip, comment(id = 31, videoId = 7, parentId = 30)).getOrThrow()

        assertEquals(4, commentCount())
    }

    @Test
    fun `the count never drops below zero`() = runTest {
        backend.enqueueNoContent()
        val quiet = video(id = 8, commentCount = 1)

        repository.delete(quiet, comment(id = 30, videoId = 8, replyCount = 4)).getOrThrow()

        assertEquals(0, interactions.statsFor(quiet).commentCount)
    }

    @Test
    fun `a refused delete leaves the count alone`() = runTest {
        backend.enqueueError(403, "forbidden", "You can't delete this comment.")

        val error = repository.delete(clip, comment(id = 30, videoId = 7, replyCount = 2)).exceptionOrNull()

        assertIs<ApiException>(error)
        assertEquals(403, error.status)
        assertEquals(5, commentCount())
    }

    @Test
    fun `deleting a comment that is already gone counts as deleted`() = runTest {
        backend.enqueueError(404, "not_found", "Comment not found.")

        repository.delete(clip, comment(id = 30, videoId = 7, replyCount = 2)).getOrThrow()

        assertEquals(2, commentCount())
    }

    @Test
    fun `comment pages remember their authors' follow state`() = runTest {
        val author = user(id = 40, isFollowing = true)
        backend.enqueue(PageDto(listOf(comment(id = 30, videoId = 7, author = author)), nextCursor = null))

        repository.comments(videoId = 7, cursor = null).getOrThrow()

        assertEquals("/loop/api/videos/7/comments", backend.takeRequest().path)
        assertEquals(true, interactions.following.value[40])
    }

    @Test
    fun `a prefetched first page is handed out once`() = runTest {
        val page = PageDto(listOf(comment(id = 30, videoId = 7)), nextCursor = "next")
        backend.enqueue(page)

        repository.prefetchFirstPage(videoId = 7)
        repository.prefetchFirstPage(videoId = 7)

        assertEquals(page, repository.takePrefetchedFirstPage(videoId = 7))
        assertNull(repository.takePrefetchedFirstPage(videoId = 7))
        assertEquals(1, backend.server.requestCount)
    }

    @Test
    fun `posting drops the prefetched page of that video`() = runTest {
        backend.enqueue(PageDto(listOf(comment(id = 30, videoId = 7)), nextCursor = null))
        backend.enqueue(comment(id = 31, videoId = 7), code = 201)

        repository.prefetchFirstPage(videoId = 7)
        repository.post(clip, "Hello", parentId = null).getOrThrow()

        assertNull(repository.takePrefetchedFirstPage(videoId = 7))
    }

    @Test
    fun `signing out drops prefetched pages loaded for the previous viewer`() = runTest {
        backend.enqueue(PageDto(listOf(comment(id = 30, videoId = 7).copy(likedByMe = true)), nextCursor = null))

        repository.prefetchFirstPage(videoId = 7)
        backend.container.authRepository.logout()

        assertNull(repository.takePrefetchedFirstPage(videoId = 7))
    }

    private fun RecordedRequest.decodeComment(): CreateCommentRequest =
        LoopJson.decodeFromString(CreateCommentRequest.serializer(), body.readUtf8())
}
