package com.ndizigiye.loop.server

import com.ndizigiye.loop.api.ToggleResultDto
import com.ndizigiye.loop.api.UserDto
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UserTest {
    private suspend fun LoopTest.user(username: String, viewer: TestUser? = null): UserDto =
        client.get("/api/users/$username") { auth(viewer) }.body()

    @Test
    fun `follow and unfollow keep counts consistent`() = loopTest {
        val ada = register("ada")
        val ben = register("ben")
        val cal = register("cal")

        assertEquals(ToggleResultDto(true, 1), follow(ben, ada))
        assertEquals(ToggleResultDto(true, 1), follow(ben, ada), "following twice is idempotent")
        assertEquals(ToggleResultDto(true, 2), follow(cal, ada))
        follow(ada, ben)

        val adaSeenByBen = user("ADA", ben)
        assertEquals(2, adaSeenByBen.followerCount)
        assertEquals(1, adaSeenByBen.followingCount)
        assertTrue(adaSeenByBen.isFollowing)
        assertFalse(adaSeenByBen.isMe)
        assertFalse(user("ada").isFollowing)
        assertTrue(user("ada", ada).isMe)

        val unfollowed = client.delete("/api/users/ada/follow") { bearerAuth(ben.token) }.body<ToggleResultDto>()
        assertEquals(ToggleResultDto(false, 1), unfollowed)
        val again = client.delete("/api/users/ada/follow") { bearerAuth(ben.token) }.body<ToggleResultDto>()
        assertEquals(ToggleResultDto(false, 1), again)
        assertEquals(0, user("ben").followingCount)
    }

    @Test
    fun `following yourself or a missing user fails`() = loopTest {
        val dan = register("dan")
        assertError(client.put("/api/users/dan/follow") { bearerAuth(dan.token) }, HttpStatusCode.BadRequest, "validation")
        assertError(client.put("/api/users/nobody/follow") { bearerAuth(dan.token) }, HttpStatusCode.NotFound, "not_found")
        assertError(client.get("/api/users/nobody"), HttpStatusCode.NotFound, "not_found")
    }

    @Test
    fun `follower lists are newest first and paginate`() = loopTest {
        val star = register("star")
        val fans = (1..5).map { register("fan$it") }
        fans.forEach { follow(it, star) }
        follow(star, fans[0])

        val followers = allPages<UserDto>("/api/users/star/followers", viewer = fans[0], limit = 2)
        assertEquals(fans.reversed().map { it.id }, followers.map { it.id })
        assertTrue(followers.single { it.id == fans[0].id }.isMe)

        val following = allPages<UserDto>("/api/users/fan1/following")
        assertEquals(listOf(star.id), following.map { it.id })
    }

    @Test
    fun `user counts cover videos and likes received`() = loopTest {
        val eva = register("eva")
        val fan = register("evafan")
        val a = postVideo(eva)
        val b = postVideo(eva)
        like(fan, a.id)
        like(fan, b.id)
        like(eva, a.id)
        val profile = user("eva")
        assertEquals(2, profile.videoCount)
        assertEquals(3, profile.likeCount)
    }
}
