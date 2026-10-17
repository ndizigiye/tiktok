package com.ndizigiye.loop.server.service

import com.ndizigiye.loop.api.CommentDto
import com.ndizigiye.loop.api.HashtagDto
import com.ndizigiye.loop.api.SoundDto
import com.ndizigiye.loop.api.UserDto
import com.ndizigiye.loop.api.VideoDto
import com.ndizigiye.loop.server.db.Bookmarks
import com.ndizigiye.loop.server.db.CommentLikes
import com.ndizigiye.loop.server.db.Comments
import com.ndizigiye.loop.server.db.Follows
import com.ndizigiye.loop.server.db.Hashtags
import com.ndizigiye.loop.server.db.Sounds
import com.ndizigiye.loop.server.db.Users
import com.ndizigiye.loop.server.db.VideoHashtags
import com.ndizigiye.loop.server.db.VideoLikes
import com.ndizigiye.loop.server.db.Videos
import com.ndizigiye.loop.server.media.MediaKind
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNotNull
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.sum

/**
 * Builds wire DTOs for batches of rows, filling counts with grouped aggregate queries so
 * they are always consistent with the relation tables. Call inside a transaction.
 * List results keep the order of the requested ids and skip ids that no longer exist.
 */
object DtoAssembler {
    private val videoLikesJoin = VideoLikes.join(Videos, JoinType.INNER, VideoLikes.videoId, Videos.id)
    private val videoHashtagsJoin = VideoHashtags
        .join(Hashtags, JoinType.INNER, VideoHashtags.hashtagId, Hashtags.id)
        .join(Videos, JoinType.INNER, VideoHashtags.videoId, Videos.id)

    fun user(id: Long, viewerId: Long?): UserDto? = users(listOf(id), viewerId)[id]

    fun users(ids: Collection<Long>, viewerId: Long?): Map<Long, UserDto> {
        val wanted = ids.toSet()
        if (wanted.isEmpty()) return emptyMap()
        val followers = countBy(Follows, Follows.followeeId, wanted)
        val following = countBy(Follows, Follows.followerId, wanted)
        val videos = countBy(Videos, Videos.authorId, wanted)
        val likes = countBy(videoLikesJoin, Videos.authorId, wanted)
        val followedByViewer = ownedBy(Follows, Follows.followeeId, Follows.followerId, viewerId, wanted)
        return Users.selectAll().where { Users.id inList wanted }.associate { row ->
            val id = row[Users.id].value
            id to UserDto(
                id = id,
                username = row[Users.username],
                displayName = row[Users.displayName],
                bio = row[Users.bio],
                avatarUrl = row[Users.avatarFile]?.let(MediaKind.AVATARS::url),
                avatarColor = row[Users.avatarColor],
                verified = row[Users.verified],
                followerCount = followers[id] ?: 0,
                followingCount = following[id] ?: 0,
                likeCount = likes[id] ?: 0,
                videoCount = videos[id] ?: 0,
                isFollowing = id in followedByViewer,
                isMe = id == viewerId,
            )
        }
    }

    /** Users in the order of [ids]. */
    fun userList(ids: List<Long>, viewerId: Long?): List<UserDto> {
        val byId = users(ids, viewerId)
        return ids.mapNotNull(byId::get)
    }

    fun video(id: Long, viewerId: Long?): VideoDto? = videos(listOf(id), viewerId).firstOrNull()

    fun videos(ids: List<Long>, viewerId: Long?): List<VideoDto> {
        val wanted = ids.toSet()
        if (wanted.isEmpty()) return emptyList()
        val rows = Videos.selectAll().where { Videos.id inList wanted }.associateBy { it[Videos.id].value }
        val authors = users(rows.values.map { it[Videos.authorId].value }, viewerId)
        val sounds = sounds(rows.values.map { it[Videos.soundId].value })
        val hashtags = hashtagNames(wanted)
        val likes = countBy(VideoLikes, VideoLikes.videoId, wanted)
        val comments = countBy(Comments, Comments.videoId, wanted)
        val bookmarks = countBy(Bookmarks, Bookmarks.videoId, wanted)
        val liked = ownedBy(VideoLikes, VideoLikes.videoId, VideoLikes.userId, viewerId, wanted)
        val bookmarked = ownedBy(Bookmarks, Bookmarks.videoId, Bookmarks.userId, viewerId, wanted)
        return ids.mapNotNull { id ->
            val row = rows[id] ?: return@mapNotNull null
            VideoDto(
                id = id,
                author = authors[row[Videos.authorId].value] ?: return@mapNotNull null,
                caption = row[Videos.caption],
                hashtags = hashtags[id].orEmpty(),
                videoUrl = MediaKind.VIDEOS.url(row[Videos.videoFile]),
                thumbnailUrl = row[Videos.thumbFile]?.let(MediaKind.THUMBS::url),
                width = row[Videos.width],
                height = row[Videos.height],
                durationMs = row[Videos.durationMs],
                sound = sounds[row[Videos.soundId].value] ?: return@mapNotNull null,
                likeCount = likes[id] ?: 0,
                commentCount = comments[id] ?: 0,
                shareCount = row[Videos.shareCount],
                bookmarkCount = bookmarks[id] ?: 0,
                viewCount = row[Videos.viewCount],
                likedByMe = id in liked,
                bookmarkedByMe = id in bookmarked,
                createdAt = row[Videos.createdAt],
            )
        }
    }

    fun sound(id: Long): SoundDto? = sounds(listOf(id))[id]

    fun sounds(ids: Collection<Long>): Map<Long, SoundDto> {
        val wanted = ids.toSet()
        if (wanted.isEmpty()) return emptyMap()
        val videoCounts = countBy(Videos, Videos.soundId, wanted)
        val covers = soundCovers(wanted)
        return Sounds.join(Users, JoinType.LEFT, Sounds.authorId, Users.id)
            .select(Sounds.id, Sounds.title, Sounds.authorName, Users.avatarFile)
            .where { Sounds.id inList wanted }
            .associate { row ->
                val id = row[Sounds.id].value
                id to SoundDto(
                    id = id,
                    title = row[Sounds.title],
                    authorName = row[Sounds.authorName],
                    coverUrl = covers[id] ?: row[Users.avatarFile]?.let(MediaKind.AVATARS::url),
                    videoCount = videoCounts[id] ?: 0,
                )
            }
    }

    fun comments(ids: List<Long>, viewerId: Long?): List<CommentDto> {
        val wanted = ids.toSet()
        if (wanted.isEmpty()) return emptyList()
        val rows = Comments.join(Videos, JoinType.INNER, Comments.videoId, Videos.id)
            .select(Comments.columns + Videos.authorId)
            .where { Comments.id inList wanted }
            .associateBy { it[Comments.id].value }
        val authors = users(rows.values.map { it[Comments.authorId].value }, viewerId)
        val likes = countBy(CommentLikes, CommentLikes.commentId, wanted)
        val liked = ownedBy(CommentLikes, CommentLikes.commentId, CommentLikes.userId, viewerId, wanted)
        val replies = replyCounts(wanted)
        return ids.mapNotNull { id ->
            val row = rows[id] ?: return@mapNotNull null
            val authorId = row[Comments.authorId].value
            CommentDto(
                id = id,
                videoId = row[Comments.videoId].value,
                author = authors[authorId] ?: return@mapNotNull null,
                text = row[Comments.text],
                likeCount = likes[id] ?: 0,
                likedByMe = id in liked,
                replyCount = replies[id] ?: 0,
                parentId = row[Comments.parentId]?.value,
                byCreator = authorId == row[Videos.authorId].value,
                createdAt = row[Comments.createdAt],
            )
        }
    }

    /** Stats for hashtags that at least one video uses, keyed by name. */
    fun hashtags(names: Collection<String>): Map<String, HashtagDto> {
        val wanted = names.toSet()
        if (wanted.isEmpty()) return emptyMap()
        val videoCount = VideoHashtags.videoId.count()
        val views = Videos.viewCount.sum()
        return videoHashtagsJoin
            .select(Hashtags.name, videoCount, views)
            .where { Hashtags.name inList wanted }
            .groupBy(Hashtags.id, Hashtags.name)
            .associate { row ->
                val name = row[Hashtags.name]
                name to HashtagDto(name = name, videoCount = row[videoCount], viewCount = row[views] ?: 0)
            }
    }

    private fun hashtagNames(videoIds: Collection<Long>): Map<Long, List<String>> =
        VideoHashtags.join(Hashtags, JoinType.INNER, VideoHashtags.hashtagId, Hashtags.id)
            .select(VideoHashtags.videoId, Hashtags.name)
            .where { VideoHashtags.videoId inList videoIds }
            .orderBy(VideoHashtags.position)
            .groupBy({ it[VideoHashtags.videoId].value }, { it[Hashtags.name] })

    /** Thumbnail of the oldest video using each sound. */
    private fun soundCovers(soundIds: Collection<Long>): Map<Long, String> =
        Videos.select(Videos.soundId, Videos.thumbFile)
            .where { (Videos.soundId inList soundIds) and Videos.thumbFile.isNotNull() }
            .orderBy(Videos.createdAt to SortOrder.ASC, Videos.id to SortOrder.ASC)
            .toList()
            .groupBy { it[Videos.soundId].value }
            .mapValues { (_, rows) -> MediaKind.THUMBS.url(rows.first()[Videos.thumbFile]!!) }

    private fun replyCounts(commentIds: Collection<Long>): Map<Long, Long> {
        val count = Comments.id.count()
        return Comments.select(Comments.parentId, count)
            .where { Comments.parentId inList commentIds }
            .groupBy(Comments.parentId)
            .associate { it[Comments.parentId]!!.value to it[count] }
    }
}
