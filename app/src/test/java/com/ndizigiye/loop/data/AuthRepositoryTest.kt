package com.ndizigiye.loop.data

import com.ndizigiye.loop.api.AuthResponse
import com.ndizigiye.loop.api.CountDto
import com.ndizigiye.loop.api.LoginRequest
import com.ndizigiye.loop.api.RegisterRequest
import com.ndizigiye.loop.data.remote.ApiException
import com.ndizigiye.loop.data.remote.LoopJson
import com.ndizigiye.loop.testutil.TestBackend
import com.ndizigiye.loop.testutil.user
import com.ndizigiye.loop.testutil.video
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthRepositoryTest {

    private val backend = TestBackend()
    private val repository get() = backend.container.authRepository
    private val sessionStore get() = backend.sessionStore
    private val interactions get() = backend.interactions

    @AfterTest
    fun tearDown() = backend.close()

    @Test
    fun `login normalises the username and starts a fresh session`() = runTest {
        interactions.remember(listOf(video(id = 7, likedByMe = true)))
        val ana = user(id = 5, username = "ana")
        backend.enqueue(AuthResponse(token = "token-of-ana", user = ana))

        assertEquals(ana, repository.login("  Ana ", "secret").getOrThrow())

        val request = backend.takeRequest()
        assertEquals("/loop/api/auth/login", request.path)
        assertEquals(
            LoginRequest("ana", "secret"),
            LoopJson.decodeFromString(LoginRequest.serializer(), request.body.readUtf8()),
        )
        assertEquals("token-of-ana", sessionStore.token)
        assertEquals(ana.copy(isMe = true), sessionStore.currentUser)
        assertTrue(interactions.videos.value.isEmpty(), "like state of the previous viewer kept")
    }

    @Test
    fun `register trims its fields`() = runTest {
        backend.enqueue(AuthResponse(token = "t", user = user(id = 5, username = "ana")), code = 201)

        repository.register(" Ana ", " Ana B ", "secret1").getOrThrow()

        assertEquals(
            RegisterRequest("ana", "Ana B", "secret1"),
            LoopJson.decodeFromString(RegisterRequest.serializer(), backend.takeRequest().body.readUtf8()),
        )
    }

    @Test
    fun `wrong credentials keep the current session`() = runTest {
        backend.signIn()
        backend.enqueueError(401, "invalid_credentials", "Wrong username or password.")

        val error = repository.login("ana", "nope").exceptionOrNull()

        assertIs<ApiException>(error)
        assertEquals(401, error.status)
        assertEquals("invalid_credentials", error.code)
        assertEquals("Wrong username or password.", error.message)
        assertEquals(TestBackend.TOKEN, sessionStore.token)
    }

    @Test
    fun `logout forgets the session, interaction state and unread count`() = runTest {
        backend.signIn()
        backend.enqueue(CountDto(4))
        backend.container.inboxRepository.refreshUnreadCount()
        interactions.setFollowing(userId = 5, following = true)
        assertEquals(4, backend.container.inboxRepository.unreadCount.value)

        repository.logout()

        assertNull(sessionStore.session.value)
        assertTrue(interactions.following.value.isEmpty())
        assertEquals(0, backend.container.inboxRepository.unreadCount.value)
    }
}
