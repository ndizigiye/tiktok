package com.ndizigiye.loop.data

import com.ndizigiye.loop.api.PageDto
import com.ndizigiye.loop.api.ToggleResultDto
import com.ndizigiye.loop.api.UpdateProfileRequest
import com.ndizigiye.loop.api.UserDto
import com.ndizigiye.loop.data.remote.ApiException
import com.ndizigiye.loop.data.remote.LoopJson
import com.ndizigiye.loop.testutil.TestBackend
import com.ndizigiye.loop.testutil.TestBackend.Companion.ME
import com.ndizigiye.loop.testutil.errorResponse
import com.ndizigiye.loop.testutil.formParts
import com.ndizigiye.loop.testutil.jsonResponse
import com.ndizigiye.loop.testutil.user
import kotlinx.coroutines.test.runTest
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class UserRepositoryTest {

    private val backend = TestBackend()
    private val repository get() = backend.container.userRepository
    private val interactions get() = backend.interactions
    private val sessionStore get() = backend.sessionStore

    private val creator = user(id = 5, username = "ana")

    @BeforeTest
    fun signIn() = backend.signIn()

    @AfterTest
    fun tearDown() = backend.close()

    @Test
    fun `a follow shows at once and raises my following count`() = runTest {
        val duringRequest = AtomicBoolean()
        backend.respondWith {
            duringRequest.set(interactions.isFollowing(creator))
            jsonResponse(ToggleResultDto(active = true, count = 11))
        }

        val result = repository.setFollowing(creator, follow = true)

        assertEquals(ToggleResultDto(active = true, count = 11), result.getOrThrow())
        assertTrue(duringRequest.get())
        assertTrue(interactions.isFollowing(creator))
        assertEquals(ME.followingCount + 1, sessionStore.currentUser?.followingCount)
        val request = backend.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/loop/api/users/ana/follow", request.path)
    }

    @Test
    fun `an unfollow sends DELETE and lowers my following count`() = runTest {
        val followed = creator.copy(isFollowing = true)
        val duringRequest = AtomicBoolean(true)
        backend.respondWith {
            duringRequest.set(interactions.isFollowing(followed))
            jsonResponse(ToggleResultDto(active = false, count = 10))
        }

        repository.setFollowing(followed, follow = false).getOrThrow()

        assertFalse(duringRequest.get())
        assertFalse(interactions.isFollowing(followed))
        assertEquals(ME.followingCount - 1, sessionStore.currentUser?.followingCount)
        assertEquals("DELETE", backend.takeRequest().method)
    }

    @Test
    fun `a failed follow is reverted and leaves my following count alone`() = runTest {
        backend.respondWith { errorResponse(400, "validation", "You can't follow yourself.") }

        val error = repository.setFollowing(creator, follow = true).exceptionOrNull()

        assertIs<ApiException>(error)
        assertEquals("validation", error.code)
        assertFalse(interactions.isFollowing(creator))
        assertEquals(ME.followingCount, sessionStore.currentUser?.followingCount)
    }

    @Test
    fun `following someone already followed does not change my count`() = runTest {
        val followed = creator.copy(isFollowing = true)
        backend.enqueue(ToggleResultDto(active = true, count = 11))

        repository.setFollowing(followed, follow = true).getOrThrow()

        assertTrue(interactions.isFollowing(followed))
        assertEquals(ME.followingCount, sessionStore.currentUser?.followingCount)
    }

    @Test
    fun `a list loaded while a follow is in flight does not undo it`() = runTest {
        val duringRequest = AtomicBoolean()
        backend.respondWith {
            interactions.rememberUsers(listOf(creator))
            duringRequest.set(interactions.isFollowing(creator))
            jsonResponse(ToggleResultDto(active = true, count = 11))
        }

        repository.setFollowing(creator, follow = true).getOrThrow()

        assertTrue(duringRequest.get())
        interactions.rememberUsers(listOf(creator))
        assertFalse(interactions.isFollowing(creator))
    }

    @Test
    fun `follower lists are remembered`() = runTest {
        val page = PageDto(listOf(user(id = 6, isFollowing = true), ME), nextCursor = "n")
        backend.enqueue(page)

        assertEquals(page, repository.followers("Ana", cursor = "c").getOrThrow())

        assertEquals("/loop/api/users/Ana/followers?cursor=c", backend.takeRequest().path)
        assertEquals(mapOf(6L to true), interactions.following.value)
    }

    @Test
    fun `loading my own profile refreshes the stored session user`() = runTest {
        backend.enqueue(ME.copy(displayName = "Me Again", videoCount = 4))

        repository.user("me").getOrThrow()

        assertEquals("Me Again", sessionStore.currentUser?.displayName)
        assertEquals(4, sessionStore.currentUser?.videoCount)
    }

    @Test
    fun `a profile edit sends trimmed fields and updates the session`() = runTest {
        backend.enqueue(ME.copy(displayName = "Ana B", bio = "Runner"))

        repository.updateProfile(displayName = "  Ana B ", bio = " Runner ").getOrThrow()

        val request = backend.takeRequest()
        assertEquals("PATCH", request.method)
        assertEquals("/loop/api/me", request.path)
        assertEquals(
            UpdateProfileRequest(displayName = "Ana B", bio = "Runner"),
            LoopJson.decodeFromString(UpdateProfileRequest.serializer(), request.body.readUtf8()),
        )
        assertEquals("Ana B", sessionStore.currentUser?.displayName)
        assertTrue(sessionStore.currentUser!!.isMe)
    }

    @Test
    fun `an avatar is uploaded as the avatar part and updates the session`() = runTest {
        val bytes = byteArrayOf(1, 2, 3, 4)
        val file = File.createTempFile("loop-avatar", ".png").apply {
            deleteOnExit()
            writeBytes(bytes)
        }
        val updated: UserDto = ME.copy(avatarUrl = "/media/avatars/1.png")
        backend.enqueue(updated)

        repository.uploadAvatar(file, "image/png").getOrThrow()

        val request = backend.takeRequest()
        assertEquals("/loop/api/me/avatar", request.path)
        val part = request.formParts().single()
        assertEquals("avatar", part.name)
        assertEquals(file.name, part.filename)
        assertEquals("image/png", part.contentType)
        assertContentEquals(bytes, part.bytes)
        assertEquals("/media/avatars/1.png", sessionStore.currentUser?.avatarUrl)
    }
}
