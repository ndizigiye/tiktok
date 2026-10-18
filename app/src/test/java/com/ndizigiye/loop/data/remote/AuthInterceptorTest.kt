package com.ndizigiye.loop.data.remote

import com.ndizigiye.loop.api.CountDto
import com.ndizigiye.loop.api.SoundDto
import com.ndizigiye.loop.testutil.TestBackend
import com.ndizigiye.loop.testutil.errorResponse
import com.ndizigiye.loop.testutil.user
import kotlinx.coroutines.test.runTest
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** [AuthInterceptor] as wired by AppContainer: in front of [BaseUrlInterceptor], signing out via AuthRepository. */
class AuthInterceptorTest {

    private val backend = TestBackend()
    private val container get() = backend.container
    private val sessionStore get() = backend.sessionStore
    private val interactions get() = backend.interactions

    @AfterTest
    fun tearDown() = backend.close()

    /** A plain GET through the app's OkHttpClient, as Coil and Media3 send them. */
    private fun get(url: String, authorization: String? = null): Int {
        val request = Request.Builder().url(url)
            .apply { if (authorization != null) header("Authorization", authorization) }
            .build()
        return container.okHttpClient.newCall(request).execute().use { it.code }
    }

    private fun MockWebServer.enqueueOk() = enqueue(MockResponse().setBody("ok"))

    @Test
    fun `API requests carry the token only while signed in`() = runTest {
        val sound = SoundDto(id = 1, title = "Beat", authorName = "Ana")
        backend.enqueue(sound)
        backend.enqueue(sound)

        container.discoverRepository.sound(1).getOrThrow()
        backend.signIn()
        container.discoverRepository.sound(1).getOrThrow()

        assertNull(backend.takeRequest().getHeader("Authorization"))
        assertEquals("Bearer ${TestBackend.TOKEN}", backend.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `media on the configured server gets the token`() {
        backend.signIn()
        backend.server.enqueueOk()

        get(sessionStore.resolveUrl("/media/videos/1.mp4")!!)

        val request = backend.takeRequest()
        assertEquals("/loop/media/videos/1.mp4", request.path)
        assertEquals("Bearer ${TestBackend.TOKEN}", request.getHeader("Authorization"))
    }

    @Test
    fun `other servers and other paths on the same host never see the token`() = runTest {
        backend.signIn()
        MockWebServer().use { other ->
            other.enqueueOk()
            backend.server.enqueueOk()
            backend.server.enqueueOk()

            container.api.healthAt(other.url("/health").toString())
            get(backend.server.url("/media/videos/1.mp4").toString())
            get(backend.server.url("/loopy/api/me").toString())

            assertNull(other.takeRequest().getHeader("Authorization"))
            assertNull(backend.takeRequest().getHeader("Authorization"))
            assertNull(backend.takeRequest().getHeader("Authorization"))
        }
    }

    @Test
    fun `an explicit Authorization header is left alone`() {
        backend.signIn()
        backend.server.enqueueOk()

        get(sessionStore.resolveUrl("/media/videos/1.mp4")!!, authorization = "Bearer someone-else")

        assertEquals("Bearer someone-else", backend.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `a 401 from a session endpoint signs out`() = runTest {
        backend.signIn()
        backend.enqueue(CountDto(3))
        container.inboxRepository.refreshUnreadCount()
        interactions.setFollowing(userId = 5, following = true)
        backend.enqueueError(401, "unauthorized", "Your session has expired.")

        val error = container.authRepository.refreshMe().exceptionOrNull()

        assertIs<ApiException>(error)
        assertEquals(401 to "unauthorized", error.status to error.code)
        assertNull(sessionStore.session.value)
        assertTrue(interactions.following.value.isEmpty())
        assertEquals(0, container.inboxRepository.unreadCount.value)
    }

    @Test
    fun `a 401 for a session path addressed to the server directly also signs out`() {
        backend.signIn()
        backend.server.enqueue(errorResponse(401, "unauthorized", "Your session has expired."))

        assertEquals(401, get(backend.server.url("/loop/api/me").toString()))

        assertNull(sessionStore.session.value)
    }

    @Test
    fun `a 401 from an auth endpoint keeps the session`() {
        backend.signIn()
        backend.server.enqueue(errorResponse(401, "invalid_credentials", "Wrong username or password."))

        assertEquals(401, get(backend.server.url("/loop/api/auth/login").toString()))

        assertEquals(TestBackend.TOKEN, sessionStore.token)
    }

    @Test
    fun `a 401 outside the API keeps the session`() {
        backend.signIn()
        backend.server.enqueue(MockResponse().setResponseCode(401))

        assertEquals(401, get(sessionStore.resolveUrl("/media/videos/1.mp4")!!))

        assertEquals(TestBackend.TOKEN, sessionStore.token)
    }

    @Test
    fun `a 401 while signed out does not reset anything`() = runTest {
        interactions.setFollowing(userId = 5, following = true)
        backend.enqueueError(401, "unauthorized", "Log in to see your notifications.")

        container.inboxRepository.notifications(cursor = null)

        assertEquals(mapOf(5L to true), interactions.following.value)
    }

    @Test
    fun `a 401 for a token that was replaced meanwhile keeps the new session`() = runTest {
        backend.signIn()
        backend.respondWith {
            sessionStore.save("token-of-ana", user(id = 5, username = "ana"))
            errorResponse(401, "unauthorized", "Your session has expired.")
        }

        container.authRepository.refreshMe()

        assertEquals("token-of-ana", sessionStore.token)
    }
}
