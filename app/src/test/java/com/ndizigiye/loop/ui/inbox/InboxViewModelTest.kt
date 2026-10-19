package com.ndizigiye.loop.ui.inbox

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ndizigiye.loop.api.CountDto
import com.ndizigiye.loop.api.NotificationDto
import com.ndizigiye.loop.api.PageDto
import com.ndizigiye.loop.testutil.TestBackend
import com.ndizigiye.loop.testutil.errorResponse
import com.ndizigiye.loop.testutil.jsonResponse
import com.ndizigiye.loop.testutil.notification
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.mockwebserver.MockResponse
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class InboxViewModelTest {

    private val backend = TestBackend()
    private val repository get() = backend.container.inboxRepository
    private val store = ViewModelStore()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        backend.signIn()
    }

    @AfterTest
    fun tearDown() {
        store.clear()
        backend.close()
        Dispatchers.resetMain()
    }

    private fun viewModel(): InboxViewModel {
        val factory = viewModelFactory { initializer { InboxViewModel(repository) } }
        return ViewModelProvider.create(store, factory)[InboxViewModel::class]
    }

    /** The next request as "METHOD path?query". */
    private fun nextRequest(): String = backend.takeRequest().let { "${it.method} ${it.path}" }

    /** Serves [inbox] as the only page and [unread] as the unread count. */
    private fun serve(inbox: List<NotificationDto>, unread: Long) = backend.respondWith { request ->
        when (request.requestUrl?.encodedPath) {
            "/loop/api/notifications" -> jsonResponse(PageDto(inbox))
            "/loop/api/notifications/read" -> MockResponse().setResponseCode(204)
            "/loop/api/notifications/unread-count" -> jsonResponse(CountDto(unread))
            else -> errorResponse(404, "not_found", "Not found.")
        }
    }

    @Test
    fun `a reload marks notifications read up to the newest one loaded`() = runTest {
        // One more notification arrived after the list was loaded, so one stays unread.
        serve(listOf(notification(id = 9), notification(id = 5)), unread = 1)

        viewModel().reload()
        repository.unreadCount.first { it == 1L }

        assertEquals("GET /loop/api/notifications", nextRequest())
        assertEquals("POST /loop/api/notifications/read?upTo=9", nextRequest())
        assertEquals("GET /loop/api/notifications/unread-count", nextRequest())
    }

    @Test
    fun `an empty inbox marks nothing read`() = runTest {
        serve(emptyList(), unread = 2)

        viewModel().reload()
        repository.unreadCount.first { it == 2L }

        assertEquals("GET /loop/api/notifications", nextRequest())
        assertEquals("GET /loop/api/notifications/unread-count", nextRequest())
        assertEquals(2, backend.server.requestCount, "no POST /read")
    }
}
