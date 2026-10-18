package com.ndizigiye.loop.data

import com.ndizigiye.loop.testutil.user
import com.ndizigiye.loop.testutil.video
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InteractionStoreTest {

    private val store = InteractionStore()

    @Test
    fun `stats come from the DTO until something is stored`() {
        val clip = video(id = 1, likeCount = 3, likedByMe = true, bookmarkCount = 2, commentCount = 4, shareCount = 5)

        assertEquals(VideoStats(true, 3, false, 2, 4, 5), store.statsFor(clip))
        assertTrue(store.videos.value.isEmpty())
    }

    @Test
    fun `remember records server state and newer pages overwrite it`() {
        val clip = video(id = 1, likeCount = 3)
        store.remember(listOf(clip))
        store.remember(listOf(clip.copy(likeCount = 9, likedByMe = true)))

        val stats = store.statsFor(clip)
        assertTrue(stats.liked)
        assertEquals(9, stats.likeCount)
    }

    @Test
    fun `remember skips videos with a mutation in flight`() {
        val busy = video(id = 1, likeCount = 3)
        val idle = video(id = 2, likeCount = 3)
        store.update(busy) { it.copy(liked = true, likeCount = 4) }
        store.beginMutation(busy.id)

        store.remember(listOf(busy, idle.copy(likeCount = 8)))

        assertEquals(4, store.statsFor(busy).likeCount)
        assertEquals(8, store.statsFor(idle).likeCount)

        store.endMutation(busy.id)
        store.remember(listOf(busy.copy(likeCount = 5)))
        assertEquals(5, store.statsFor(busy).likeCount)
    }

    @Test
    fun `overlapping mutations keep a video busy until the last one ends`() {
        val clip = video(id = 1, likeCount = 3)
        store.update(clip) { it.copy(likeCount = 4) }
        store.beginMutation(clip.id)
        store.beginMutation(clip.id)

        store.endMutation(clip.id)
        store.remember(listOf(clip))
        assertEquals(4, store.statsFor(clip).likeCount)

        store.endMutation(clip.id)
        store.remember(listOf(clip))
        assertEquals(3, store.statsFor(clip).likeCount)
    }

    @Test
    fun `an unmatched end does not make later mutations end early`() {
        val clip = video(id = 1, likeCount = 3)
        store.endMutation(clip.id)
        store.update(clip) { it.copy(likeCount = 4) }
        store.beginMutation(clip.id)

        store.remember(listOf(clip))

        assertEquals(4, store.statsFor(clip).likeCount)
    }

    @Test
    fun `update starts from the DTO and only changes what the transform changes`() {
        val clip = video(id = 1, likeCount = 3, commentCount = 7)

        store.update(clip) { it.copy(commentCount = it.commentCount + 1) }

        assertEquals(VideoStats.from(clip).copy(commentCount = 8), store.videos.value[1])
    }

    @Test
    fun `remembering videos records their authors' follow state`() {
        val author = user(id = 9, isFollowing = true)

        store.remember(listOf(video(id = 1, author = author)))

        assertTrue(store.isFollowing(author.copy(isFollowing = false)))
    }

    @Test
    fun `follow state falls back to the DTO and setFollowing overrides it`() {
        val ana = user(id = 5, isFollowing = true)
        assertTrue(store.isFollowing(ana))

        store.setFollowing(ana.id, following = false)

        assertFalse(store.isFollowing(ana))
        assertEquals(mapOf(5L to false), store.following.value)
    }

    @Test
    fun `rememberUsers ignores me and users with a follow change in flight`() {
        val me = user(id = 1, isMe = true)
        val busy = user(id = 2, isFollowing = false)
        val idle = user(id = 3, isFollowing = true)
        store.setFollowing(busy.id, following = true)
        store.beginFollowMutation(busy.id)

        store.rememberUsers(listOf(me, busy, idle))

        assertNull(store.following.value[me.id])
        assertTrue(store.isFollowing(busy))
        assertTrue(store.isFollowing(idle.copy(isFollowing = false)))

        store.endFollowMutation(busy.id)
        store.rememberUsers(listOf(busy))
        assertFalse(store.isFollowing(busy))
    }

    @Test
    fun `follow mutations do not block like state and vice versa`() {
        val author = user(id = 1, isFollowing = true)
        val clip = video(id = 1, author = author, likeCount = 3)
        store.beginFollowMutation(1)
        store.setFollowing(1, following = false)

        store.remember(listOf(clip))

        assertEquals(3, store.statsFor(clip).likeCount)
        assertFalse(store.isFollowing(author))
    }

    @Test
    fun `clear forgets videos and follow state`() {
        val author = user(id = 9, isFollowing = true)
        store.remember(listOf(video(id = 1, author = author, likeCount = 3)))

        store.clear()

        assertTrue(store.videos.value.isEmpty())
        assertTrue(store.following.value.isEmpty())
        assertFalse(store.isFollowing(author.copy(isFollowing = false)))
    }
}
