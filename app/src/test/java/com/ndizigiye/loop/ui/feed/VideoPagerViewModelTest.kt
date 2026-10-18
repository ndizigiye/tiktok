package com.ndizigiye.loop.ui.feed

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ndizigiye.loop.api.VideoDto
import com.ndizigiye.loop.data.PagedState
import com.ndizigiye.loop.data.VideoListSource
import com.ndizigiye.loop.testutil.TestBackend
import com.ndizigiye.loop.testutil.errorResponse
import com.ndizigiye.loop.testutil.jsonResponse
import com.ndizigiye.loop.testutil.video
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class VideoPagerViewModelTest {

    private val backend = TestBackend()
    private val stores = mutableListOf<ViewModelStore>()
    private val clips = listOf(video(id = 1), video(id = 2), video(id = 3))

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        // Single videos come from the server; the handed-off list never does.
        backend.respondWith { request ->
            val id = request.requestUrl?.encodedPath?.removePrefix("/loop/api/videos/")?.toLongOrNull()
            clips.firstOrNull { it.id == id }?.let { jsonResponse(it) }
                ?: errorResponse(404, "not_found", "Video not found.")
        }
    }

    @AfterTest
    fun tearDown() {
        stores.forEach(ViewModelStore::clear)
        backend.close()
        Dispatchers.resetMain()
    }

    /** A pager ViewModel as `VideoPagerScreen` creates it, in its own store (one per back stack entry). */
    private fun viewModel(
        savedState: SavedStateHandle,
        source: VideoListSource?,
        startIndex: Int,
        routeVideoId: Long?,
    ): VideoPagerViewModel {
        val store = ViewModelStore().also(stores::add)
        val factory = viewModelFactory {
            initializer {
                VideoPagerViewModel(
                    source = source,
                    startIndex = startIndex,
                    routeVideoId = routeVideoId,
                    savedStateHandle = savedState,
                    videoRepository = backend.container.videoRepository,
                    applicationScope = backend.container.applicationScope,
                )
            }
        }
        return ViewModelProvider.create(store, factory)[VideoPagerViewModel::class]
    }

    private suspend fun VideoPagerViewModel.loaded(): PagedState<VideoDto> =
        state.first { it.loadedOnce || it.error != null }

    /** What survives process death: the saved state, not the in-memory handoff. */
    private fun SavedStateHandle.afterProcessDeath() = SavedStateHandle(keys().associateWith { get<Any?>(it) })

    @Test
    fun `after process death the pager reopens at the video that was on screen`() = runTest {
        val savedState = SavedStateHandle()
        val before = viewModel(savedState, VideoListSource(clips, null, null), startIndex = 0, routeVideoId = 1)
        assertEquals(0, before.initialPage)
        assertEquals(clips, before.loaded().items)
        before.onSettled(2)
        before.onSettled(3)

        val after = viewModel(savedState.afterProcessDeath(), source = null, startIndex = 0, routeVideoId = 1)

        assertEquals(listOf(3L), after.loaded().items.map { it.id })
        assertEquals(0, after.initialPage)
        assertEquals("/loop/api/videos/3", backend.takeRequest().path)
        assertEquals(1, backend.server.requestCount)
    }

    @Test
    fun `without a saved video the route's video opens`() = runTest {
        val opened = viewModel(SavedStateHandle(), source = null, startIndex = 0, routeVideoId = 2)

        assertEquals(listOf(2L), opened.loaded().items.map { it.id })
        assertEquals("/loop/api/videos/2", backend.takeRequest().path)
    }
}
