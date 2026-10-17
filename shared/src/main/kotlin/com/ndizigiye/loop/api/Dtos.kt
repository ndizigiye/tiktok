package com.ndizigiye.loop.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Wire contract between the Android app and the Loop server. See docs/api.md.
// Timestamps are epoch milliseconds (UTC). Media URLs are server-relative paths
// such as "/media/videos/abc.mp4"; clients resolve them against their base URL.

@Serializable
data class UserDto(
    val id: Long,
    val username: String,
    val displayName: String,
    val bio: String = "",
    val avatarUrl: String? = null,
    /** Fallback avatar background, "#RRGGBB". */
    val avatarColor: String = "#6C5CE7",
    val verified: Boolean = false,
    val followerCount: Long = 0,
    val followingCount: Long = 0,
    /** Total likes received across the user's videos. */
    val likeCount: Long = 0,
    val videoCount: Long = 0,
    /** Whether the requesting user follows this user (false when anonymous). */
    val isFollowing: Boolean = false,
    /** Whether this is the requesting user. */
    val isMe: Boolean = false,
)

@Serializable
data class SoundDto(
    val id: Long,
    val title: String,
    val authorName: String,
    val coverUrl: String? = null,
    val videoCount: Long = 0,
)

@Serializable
data class VideoDto(
    val id: Long,
    val author: UserDto,
    val caption: String,
    val hashtags: List<String> = emptyList(),
    val videoUrl: String,
    val thumbnailUrl: String? = null,
    val width: Int = 720,
    val height: Int = 1280,
    val durationMs: Long = 0,
    val sound: SoundDto,
    val likeCount: Long = 0,
    val commentCount: Long = 0,
    val shareCount: Long = 0,
    val bookmarkCount: Long = 0,
    val viewCount: Long = 0,
    val likedByMe: Boolean = false,
    val bookmarkedByMe: Boolean = false,
    val createdAt: Long,
)

@Serializable
data class CommentDto(
    val id: Long,
    val videoId: Long,
    val author: UserDto,
    val text: String,
    val likeCount: Long = 0,
    val likedByMe: Boolean = false,
    val replyCount: Long = 0,
    /** Set for replies; replies are one level deep. */
    val parentId: Long? = null,
    /** True when the comment author is the video author. */
    val byCreator: Boolean = false,
    val createdAt: Long,
)

@Serializable
data class HashtagDto(
    val name: String,
    val videoCount: Long = 0,
    val viewCount: Long = 0,
)

@Serializable
data class TrendingHashtagDto(
    val hashtag: HashtagDto,
    val videos: List<VideoDto>,
)

@Serializable
data class TrendingDto(
    val hashtags: List<TrendingHashtagDto>,
    val sounds: List<SoundDto>,
    val creators: List<UserDto>,
)

@Serializable
data class SearchResultDto(
    val users: List<UserDto> = emptyList(),
    val videos: List<VideoDto> = emptyList(),
    val hashtags: List<HashtagDto> = emptyList(),
)

@Serializable
enum class NotificationType {
    @SerialName("like") LIKE,
    @SerialName("comment") COMMENT,
    @SerialName("reply") REPLY,
    @SerialName("comment_like") COMMENT_LIKE,
    @SerialName("follow") FOLLOW,
    @SerialName("mention") MENTION,
}

@Serializable
data class VideoSummaryDto(
    val id: Long,
    val thumbnailUrl: String? = null,
)

@Serializable
data class NotificationDto(
    val id: Long,
    val type: NotificationType,
    val actor: UserDto,
    val video: VideoSummaryDto? = null,
    /** Comment text for COMMENT / REPLY / COMMENT_LIKE / MENTION. */
    val text: String? = null,
    val read: Boolean = false,
    val createdAt: Long,
)

/** Cursor page. [nextCursor] is opaque; null means there are no more items. */
@Serializable
data class PageDto<T>(
    val items: List<T>,
    val nextCursor: String? = null,
)

/** Result of a like / bookmark / follow toggle: the new state and the new total. */
@Serializable
data class ToggleResultDto(
    val active: Boolean,
    val count: Long,
)

@Serializable
data class CountDto(val count: Long)

@Serializable
data class ErrorDto(
    /** Stable machine-readable code, e.g. "not_found", "unauthorized", "validation". */
    val code: String,
    val message: String,
)

@Serializable
data class RegisterRequest(
    val username: String,
    val displayName: String,
    val password: String,
)

@Serializable
data class LoginRequest(
    val username: String,
    val password: String,
)

@Serializable
data class AuthResponse(
    val token: String,
    val user: UserDto,
)

@Serializable
data class UpdateProfileRequest(
    val displayName: String? = null,
    val bio: String? = null,
)

@Serializable
data class CreateCommentRequest(
    val text: String,
    val parentId: Long? = null,
)

@Serializable
data class ViewEventRequest(
    /** How long the video was watched in this view, in milliseconds. */
    val watchedMs: Long,
    /** Whether playback reached the end at least once. */
    val completed: Boolean,
)
