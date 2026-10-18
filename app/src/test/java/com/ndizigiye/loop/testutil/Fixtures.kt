package com.ndizigiye.loop.testutil

import com.ndizigiye.loop.api.CommentDto
import com.ndizigiye.loop.api.SoundDto
import com.ndizigiye.loop.api.UserDto
import com.ndizigiye.loop.api.VideoDto

fun user(
    id: Long,
    username: String = "user$id",
    isFollowing: Boolean = false,
    isMe: Boolean = false,
    followerCount: Long = 0,
    followingCount: Long = 0,
) = UserDto(
    id = id,
    username = username,
    displayName = username.replaceFirstChar { it.uppercase() },
    followerCount = followerCount,
    followingCount = followingCount,
    isFollowing = isFollowing,
    isMe = isMe,
)

fun video(
    id: Long,
    author: UserDto = user(id = 100 + id),
    caption: String = "Clip $id",
    likeCount: Long = 0,
    likedByMe: Boolean = false,
    bookmarkCount: Long = 0,
    bookmarkedByMe: Boolean = false,
    commentCount: Long = 0,
    shareCount: Long = 0,
) = VideoDto(
    id = id,
    author = author,
    caption = caption,
    videoUrl = "/media/videos/$id.mp4",
    sound = SoundDto(id = 1, title = "Original sound", authorName = author.displayName),
    likeCount = likeCount,
    commentCount = commentCount,
    shareCount = shareCount,
    bookmarkCount = bookmarkCount,
    likedByMe = likedByMe,
    bookmarkedByMe = bookmarkedByMe,
    createdAt = 1_700_000_000_000,
)

fun comment(
    id: Long,
    videoId: Long,
    author: UserDto = user(id = 200 + id),
    text: String = "Comment $id",
    replyCount: Long = 0,
    parentId: Long? = null,
) = CommentDto(
    id = id,
    videoId = videoId,
    author = author,
    text = text,
    replyCount = replyCount,
    parentId = parentId,
    createdAt = 1_700_000_000_000,
)
