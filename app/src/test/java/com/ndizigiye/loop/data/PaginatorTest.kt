package com.ndizigiye.loop.data

import com.ndizigiye.loop.api.PageDto
import com.ndizigiye.loop.data.remote.ApiException
import com.ndizigiye.loop.data.remote.NETWORK_ERROR_MESSAGE
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PaginatorTest {

    /**
     * Pages of two numbers each, cursors "1", "2", ...; the last page has no cursor.
     * Bumping [generation] shifts the numbers by 100 per generation, like fresh server data.
     */
    private class FakeSource(private val pages: Int, private val latencyMs: Long = 0) {
        var failure: Throwable? = null
        var generation = 0
        val requested = mutableListOf<String?>()

        suspend fun load(cursor: String?): Result<PageDto<Int>> {
            requested += cursor
            delay(latencyMs)
            failure?.let { return Result.failure(it) }
            val page = cursor?.toInt() ?: 0
            val next = (page + 1).takeIf { it < pages }?.toString()
            val first = generation * 100 + page * 2
            return Result.success(PageDto(listOf(first, first + 1), next))
        }
    }

    private fun TestScope.paginator(source: FakeSource) = Paginator(this) { source.load(it) }

    @Test
    fun `the first page goes from loading to loaded`() = runTest {
        val source = FakeSource(pages = 3)
        val paginator = paginator(source)
        assertEquals(PagedState<Int>(), paginator.state.value)

        paginator.loadMore()
        val loading = paginator.state.value
        assertTrue(loading.isLoading)
        assertFalse(loading.loadedOnce)
        advanceUntilIdle()

        assertEquals(
            PagedState(items = listOf(0, 1), nextCursor = "1", loadedOnce = true),
            paginator.state.value,
        )
    }

    @Test
    fun `loads pages in order until the end`() = runTest {
        val source = FakeSource(pages = 2)
        val paginator = paginator(source)

        paginator.loadMore()
        advanceUntilIdle()
        paginator.loadMore()
        advanceUntilIdle()
        paginator.loadMore()
        advanceUntilIdle()

        val state = paginator.state.value
        assertEquals(listOf(0, 1, 2, 3), state.items)
        assertTrue(state.endReached)
        assertNull(state.nextCursor)
        assertEquals(listOf(null, "1"), source.requested)
    }

    @Test
    fun `a single page list is at its end at once`() = runTest {
        val source = FakeSource(pages = 1)
        val paginator = paginator(source)

        paginator.loadMore()
        advanceUntilIdle()
        paginator.loadMore()
        advanceUntilIdle()

        assertTrue(paginator.state.value.endReached)
        assertFalse(paginator.state.value.isLoading)
        assertEquals(listOf<String?>(null), source.requested)
    }

    @Test
    fun `ignores load more while a page is in flight`() = runTest {
        val source = FakeSource(pages = 3)
        val paginator = paginator(source)

        paginator.loadMore()
        paginator.loadMore()
        advanceUntilIdle()

        assertEquals(listOf<String?>(null), source.requested)
    }

    @Test
    fun `a failed first page shows the error and load more retries it`() = runTest {
        val source = FakeSource(pages = 3).apply { failure = ApiException(0, "network", NETWORK_ERROR_MESSAGE) }
        val paginator = paginator(source)

        paginator.loadMore()
        advanceUntilIdle()
        val failed = paginator.state.value
        assertEquals(NETWORK_ERROR_MESSAGE, failed.error)
        assertTrue(failed.items.isEmpty())
        assertFalse(failed.isLoading)
        assertFalse(failed.loadedOnce)

        source.failure = null
        paginator.loadMore()
        advanceUntilIdle()

        assertEquals(listOf(0, 1), paginator.state.value.items)
        assertNull(paginator.state.value.error)
        assertEquals(listOf<String?>(null, null), source.requested)
    }

    @Test
    fun `a failed page keeps the items and the next load more retries it`() = runTest {
        val source = FakeSource(pages = 3)
        val paginator = paginator(source)
        paginator.loadMore()
        advanceUntilIdle()

        source.failure = ApiException(0, "network", "offline")
        paginator.loadMore()
        advanceUntilIdle()
        val failed = paginator.state.value
        assertEquals("offline", failed.error)
        assertEquals(listOf(0, 1), failed.items)
        assertFalse(failed.isLoading)

        source.failure = null
        paginator.loadMore()
        assertNull(paginator.state.value.error)
        advanceUntilIdle()
        assertEquals(listOf(0, 1, 2, 3), paginator.state.value.items)
        assertEquals(listOf(null, "1", "1"), source.requested)
    }

    @Test
    fun `errors that are not ApiExceptions get a readable message`() = runTest {
        val source = FakeSource(pages = 3).apply { failure = IOException("socket closed") }
        val paginator = paginator(source)

        paginator.loadMore()
        advanceUntilIdle()
        assertEquals(NETWORK_ERROR_MESSAGE, paginator.state.value.error)

        source.failure = IllegalStateException("bug")
        paginator.loadMore()
        advanceUntilIdle()
        assertEquals("Something went wrong. Please try again.", paginator.state.value.error)
    }

    @Test
    fun `refresh starts again from the first page`() = runTest {
        val source = FakeSource(pages = 3)
        val paginator = paginator(source)
        paginator.loadMore()
        advanceUntilIdle()
        paginator.loadMore()
        advanceUntilIdle()

        paginator.refresh()
        assertTrue(paginator.state.value.isRefreshing)
        advanceUntilIdle()

        val state = paginator.state.value
        assertEquals(listOf(0, 1), state.items)
        assertEquals("1", state.nextCursor)
        assertFalse(state.isRefreshing)
    }

    @Test
    fun `refresh keeps the items visible until the new page lands`() = runTest {
        val source = FakeSource(pages = 3, latencyMs = 1_000)
        val paginator = paginator(source)
        paginator.loadMore()
        advanceUntilIdle()
        paginator.loadMore()
        advanceUntilIdle()

        source.generation = 1
        paginator.refresh()
        val refreshing = paginator.state.value
        assertEquals(listOf(0, 1, 2, 3), refreshing.items)
        assertTrue(refreshing.isRefreshing)
        assertFalse(refreshing.isLoading)
        paginator.loadMore()
        advanceUntilIdle()

        val refreshed = paginator.state.value
        assertEquals(listOf(100, 101), refreshed.items)
        assertEquals("1", refreshed.nextCursor)
        assertFalse(refreshed.isRefreshing)
        assertEquals(listOf(null, "1", null), source.requested)

        paginator.loadMore()
        advanceUntilIdle()
        assertEquals(listOf(100, 101, 102, 103), paginator.state.value.items)
    }

    @Test
    fun `refresh of an empty list shows the loading state`() = runTest {
        val source = FakeSource(pages = 3)
        val paginator = paginator(source)

        paginator.refresh()
        val loading = paginator.state.value
        assertTrue(loading.isLoading)
        assertFalse(loading.isRefreshing)
        advanceUntilIdle()

        assertEquals(listOf(0, 1), paginator.state.value.items)
        assertTrue(paginator.state.value.loadedOnce)
    }

    @Test
    fun `a failed refresh keeps the items and shows the error`() = runTest {
        val source = FakeSource(pages = 3)
        val paginator = paginator(source)
        paginator.loadMore()
        advanceUntilIdle()

        source.failure = ApiException(500, "internal", "The server had a problem.")
        paginator.refresh()
        advanceUntilIdle()

        val state = paginator.state.value
        assertEquals(listOf(0, 1), state.items)
        assertEquals("1", state.nextCursor)
        assertEquals("The server had a problem.", state.error)
        assertFalse(state.isRefreshing)
        assertFalse(state.isLoading)
    }

    @Test
    fun `refresh drops a page that was still loading`() = runTest {
        val source = FakeSource(pages = 3, latencyMs = 1_000)
        val paginator = paginator(source)
        paginator.loadMore()
        advanceUntilIdle()

        paginator.loadMore()
        runCurrent()
        paginator.refresh()
        advanceUntilIdle()

        val state = paginator.state.value
        assertEquals(listOf(0, 1), state.items)
        assertEquals("1", state.nextCursor)
        assertFalse(state.isLoading)
        assertEquals(listOf(null, "1", null), source.requested)
    }

    @Test
    fun `update edits the items and paging continues after them`() = runTest {
        val source = FakeSource(pages = 3)
        val paginator = paginator(source)
        paginator.loadMore()
        advanceUntilIdle()

        paginator.update { items -> items - 0 }
        paginator.update { items -> listOf(-1) + items }
        assertEquals(listOf(-1, 1), paginator.state.value.items)
        assertEquals("1", paginator.state.value.nextCursor)

        paginator.loadMore()
        advanceUntilIdle()
        assertEquals(listOf(-1, 1, 2, 3), paginator.state.value.items)
    }
}
