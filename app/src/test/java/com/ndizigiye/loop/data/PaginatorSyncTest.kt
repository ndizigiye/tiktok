package com.ndizigiye.loop.data

import com.ndizigiye.loop.api.ApiLimits
import com.ndizigiye.loop.api.PageDto
import com.ndizigiye.loop.data.remote.ApiException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** [Paginator.sync]: bringing a list up to date without losing the reader's place. */
@OptIn(ExperimentalCoroutinesApi::class)
class PaginatorSyncTest {

    /** A server list paged by [ApiLimits.DEFAULT_PAGE_SIZE] with the last item as cursor. */
    private class FakeList(var items: List<Int>, private val latencyMs: Long = 0) {
        var failure: Throwable? = null
        val requested = mutableListOf<String?>()

        suspend fun load(cursor: String?): Result<PageDto<Int>> {
            requested += cursor
            delay(latencyMs)
            failure?.let { return Result.failure(it) }
            val start = cursor?.let { items.indexOf(it.toInt()) + 1 } ?: 0
            val page = items.drop(start).take(PAGE)
            val next = page.lastOrNull()?.toString()?.takeIf { start + PAGE < items.size }
            return Result.success(PageDto(page, next))
        }
    }

    private fun TestScope.paginator(source: FakeList) = Paginator(this) { source.load(it) }

    private fun TestScope.loadPages(paginator: Paginator<Int>, pages: Int) {
        repeat(pages) {
            paginator.loadMore()
            advanceUntilIdle()
        }
    }

    @Test
    fun `a list of one page is simply refreshed`() = runTest {
        val source = FakeList((1..5).toList())
        val paginator = paginator(source)
        loadPages(paginator, 1)

        source.items = listOf(0) + (1..5).toList()
        paginator.sync { it }
        advanceUntilIdle()

        assertEquals((0..5).toList(), paginator.state.value.items)
        assertEquals(listOf<String?>(null, null), source.requested)
    }

    @Test
    fun `a longer list gets the fresh first page in front of the items scrolled past`() = runTest {
        val source = FakeList((1..25).toList(), latencyMs = 1_000)
        val paginator = paginator(source)
        loadPages(paginator, 2)

        // Two new items arrive and item 3 is deleted.
        source.items = listOf(100, 101) + (1..25).filterNot { it == 3 }
        paginator.sync { it }
        val syncing = paginator.state.value
        assertTrue(syncing.isRefreshing)
        assertEquals((1..20).toList(), syncing.items)
        advanceUntilIdle()

        val synced = paginator.state.value
        assertEquals(listOf(100, 101) + (1..20).filterNot { it == 3 }, synced.items)
        assertEquals("20", synced.nextCursor)
        assertFalse(synced.isRefreshing)

        paginator.loadMore()
        advanceUntilIdle()
        assertEquals(listOf(100, 101) + (1..25).filterNot { it == 3 }, paginator.state.value.items)
        assertTrue(paginator.state.value.endReached)
    }

    @Test
    fun `lists that no longer overlap start over from the fresh first page`() = runTest {
        val source = FakeList((1..25).toList())
        val paginator = paginator(source)
        loadPages(paginator, 2)

        source.items = (200..230).toList()
        paginator.sync { it }
        advanceUntilIdle()

        val state = paginator.state.value
        assertEquals((200..209).toList(), state.items)
        assertEquals("209", state.nextCursor)
        assertFalse(state.endReached)
    }

    @Test
    fun `a failed sync keeps the items and shows the error`() = runTest {
        val source = FakeList((1..25).toList())
        val paginator = paginator(source)
        loadPages(paginator, 2)

        source.failure = ApiException(500, "internal", "The server had a problem.")
        paginator.sync { it }
        advanceUntilIdle()

        val state = paginator.state.value
        assertEquals((1..20).toList(), state.items)
        assertEquals("20", state.nextCursor)
        assertEquals("The server had a problem.", state.error)
        assertFalse(state.isRefreshing)
    }

    @Test
    fun `sync drops a next page that was still loading`() = runTest {
        val source = FakeList((1..35).toList(), latencyMs = 1_000)
        val paginator = paginator(source)
        loadPages(paginator, 2)

        paginator.loadMore()
        runCurrent()
        paginator.sync { it }
        assertFalse(paginator.state.value.isLoading)
        advanceUntilIdle()

        val state = paginator.state.value
        assertEquals((1..20).toList(), state.items)
        assertEquals("20", state.nextCursor)
        assertEquals(listOf(null, "10", "20", null), source.requested)
    }

    private companion object {
        const val PAGE = ApiLimits.DEFAULT_PAGE_SIZE
    }
}
