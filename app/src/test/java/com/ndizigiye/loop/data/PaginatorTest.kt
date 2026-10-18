package com.ndizigiye.loop.data

import com.ndizigiye.loop.api.PageDto
import com.ndizigiye.loop.data.remote.ApiException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PaginatorTest {

    /** Pages of two numbers each, cursors "1", "2", ...; the last page has no cursor. */
    private class FakeSource(private val pages: Int) {
        var failing = false
        val requested = mutableListOf<String?>()

        suspend fun load(cursor: String?): Result<PageDto<Int>> {
            requested += cursor
            if (failing) return Result.failure(ApiException(0, "network", "offline"))
            val page = cursor?.toInt() ?: 0
            val next = (page + 1).takeIf { it < pages }?.toString()
            return Result.success(PageDto(listOf(page * 2, page * 2 + 1), next))
        }
    }

    private fun TestScope.paginator(source: FakeSource) = Paginator(this) { source.load(it) }

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
        assertEquals(listOf(null, "1"), source.requested)
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
    fun `a failed page keeps the items and the next load more retries it`() = runTest {
        val source = FakeSource(pages = 3)
        val paginator = paginator(source)
        paginator.loadMore()
        advanceUntilIdle()

        source.failing = true
        paginator.loadMore()
        advanceUntilIdle()
        val failed = paginator.state.value
        assertEquals("offline", failed.error)
        assertEquals(listOf(0, 1), failed.items)
        assertFalse(failed.isLoading)

        source.failing = false
        paginator.loadMore()
        assertNull(paginator.state.value.error)
        advanceUntilIdle()
        assertEquals(listOf(0, 1, 2, 3), paginator.state.value.items)
        assertEquals(listOf(null, "1", "1"), source.requested)
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
}
