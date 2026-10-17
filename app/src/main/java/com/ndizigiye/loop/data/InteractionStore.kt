package com.ndizigiye.loop.data

import com.ndizigiye.loop.api.UserDto
import com.ndizigiye.loop.api.VideoDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** The viewer-specific, frequently changing part of a video. */
data class VideoStats(
    val liked: Boolean,
    val likeCount: Long,
    val bookmarked: Boolean,
    val bookmarkCount: Long,
    val commentCount: Long,
    val shareCount: Long,
) {
    companion object {
        fun from(video: VideoDto) = VideoStats(
            liked = video.likedByMe,
            likeCount = video.likeCount,
            bookmarked = video.bookmarkedByMe,
            bookmarkCount = video.bookmarkCount,
            commentCount = video.commentCount,
            shareCount = video.shareCount,
        )
    }
}

/**
 * Single source of truth for like/bookmark/follow state, so a like in the pager
 * shows up in the grid it came from. Repositories record server state here; the
 * UI reads it through `rememberVideoStats` / `rememberIsFollowing`.
 * Thread-safe.
 */
class InteractionStore {

    private val _videos = MutableStateFlow<Map<Long, VideoStats>>(emptyMap())
    val videos: StateFlow<Map<Long, VideoStats>> = _videos.asStateFlow()

    /** userId -> whether the signed-in user follows them. */
    private val _following = MutableStateFlow<Map<Long, Boolean>>(emptyMap())
    val following: StateFlow<Map<Long, Boolean>> = _following.asStateFlow()

    private val videoMutations = mutableMapOf<Long, Int>()
    private val followMutations = mutableMapOf<Long, Int>()

    /** Records server state for [videos] and their authors, skipping ids with a mutation in flight. */
    fun remember(videos: List<VideoDto>) {
        if (videos.isEmpty()) return
        val busy = synchronized(videoMutations) { videoMutations.keys.toSet() }
        val fresh = videos.filter { it.id !in busy }.associate { it.id to VideoStats.from(it) }
        if (fresh.isNotEmpty()) _videos.update { it + fresh }
        rememberUsers(videos.map { it.author })
    }

    /** Records server follow state for [users], skipping users with a follow change in flight. */
    fun rememberUsers(users: List<UserDto>) {
        if (users.isEmpty()) return
        val busy = synchronized(followMutations) { followMutations.keys.toSet() }
        val fresh = users.filter { !it.isMe && it.id !in busy }.associate { it.id to it.isFollowing }
        if (fresh.isNotEmpty()) _following.update { it + fresh }
    }

    /** The stored stats for [video], else the ones in the DTO. */
    fun statsFor(video: VideoDto): VideoStats = _videos.value[video.id] ?: VideoStats.from(video)

    fun update(video: VideoDto, transform: (VideoStats) -> VideoStats) {
        _videos.update { current ->
            current + (video.id to transform(current[video.id] ?: VideoStats.from(video)))
        }
    }

    fun isFollowing(user: UserDto): Boolean = _following.value[user.id] ?: user.isFollowing

    fun setFollowing(userId: Long, following: Boolean) {
        _following.update { it + (userId to following) }
    }

    /** Marks a like/bookmark change in flight so [remember] does not overwrite the optimistic state. */
    fun beginMutation(videoId: Long) = begin(videoMutations, videoId)

    fun endMutation(videoId: Long) = end(videoMutations, videoId)

    /** Like [beginMutation], for follow changes of [userId]. */
    fun beginFollowMutation(userId: Long) = begin(followMutations, userId)

    fun endFollowMutation(userId: Long) = end(followMutations, userId)

    /** Forgets everything, e.g. when the signed-in account changes. */
    fun clear() {
        _videos.value = emptyMap()
        _following.value = emptyMap()
    }

    private fun begin(counters: MutableMap<Long, Int>, id: Long) {
        synchronized(counters) { counters[id] = (counters[id] ?: 0) + 1 }
    }

    private fun end(counters: MutableMap<Long, Int>, id: Long) {
        synchronized(counters) {
            val remaining = (counters[id] ?: 0) - 1
            if (remaining > 0) counters[id] = remaining else counters.remove(id)
        }
    }
}
