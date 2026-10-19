package com.ndizigiye.loop.server.db

import com.ndizigiye.loop.api.ApiLimits
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.ReferenceOption.CASCADE
import org.jetbrains.exposed.sql.ReferenceOption.SET_NULL
import org.jetbrains.exposed.sql.Table

/** Key/value settings of this database instance (token key id, seeding state). */
object Meta : Table("meta") {
    val key = varchar("key", 64)
    val value = text("value")
    override val primaryKey = PrimaryKey(key)
}

object Users : LongIdTable("users") {
    val username = varchar("username", ApiLimits.USERNAME_MAX).uniqueIndex()
    val displayName = varchar("display_name", ApiLimits.DISPLAY_NAME_MAX)
    val bio = varchar("bio", ApiLimits.BIO_MAX).default("")
    val passwordHash = varchar("password_hash", 128)
    val passwordSalt = varchar("password_salt", 64)
    val avatarFile = varchar("avatar_file", 128).nullable()
    val avatarColor = varchar("avatar_color", 7)
    val verified = bool("verified").default(false)
    val createdAt = long("created_at")
}

object Sounds : LongIdTable("sounds") {
    val title = varchar("title", 200)
    val authorName = varchar("author_name", 100)
    val authorId = reference("author_id", Users, onDelete = SET_NULL).nullable().index()
    val createdAt = long("created_at")
}

object Videos : LongIdTable("videos") {
    val authorId = reference("author_id", Users, onDelete = CASCADE)
    val caption = varchar("caption", ApiLimits.CAPTION_MAX)
    val videoFile = varchar("video_file", 128)
    val thumbFile = varchar("thumb_file", 128).nullable()
    val width = integer("width")
    val height = integer("height")
    val durationMs = long("duration_ms")
    val soundId = reference("sound_id", Sounds).index()
    val shareCount = long("share_count").default(0)
    val viewCount = long("view_count").default(0)
    val completionCount = long("completion_count").default(0)
    val createdAt = long("created_at")

    init {
        index(false, authorId, createdAt)
    }
}

object Hashtags : LongIdTable("hashtags") {
    val name = varchar("name", 100).uniqueIndex()
}

object VideoHashtags : Table("video_hashtags") {
    val videoId = reference("video_id", Videos, onDelete = CASCADE)
    /**
     * Not cascading: unused hashtags are deleted with `NOT IN (links)`, and a link committed by a
     * concurrent upload in the meantime must make that delete fail (and retry), not vanish with it.
     */
    val hashtagId = reference("hashtag_id", Hashtags).index()
    /** Order of first appearance in the caption. */
    val position = integer("position")
    override val primaryKey = PrimaryKey(videoId, hashtagId)
}

object Follows : Table("follows") {
    val followerId = reference("follower_id", Users, onDelete = CASCADE)
    val followeeId = reference("followee_id", Users, onDelete = CASCADE).index()
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(followerId, followeeId)
}

object VideoLikes : Table("video_likes") {
    val userId = reference("user_id", Users, onDelete = CASCADE)
    val videoId = reference("video_id", Videos, onDelete = CASCADE).index()
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(userId, videoId)
}

object Bookmarks : Table("bookmarks") {
    val userId = reference("user_id", Users, onDelete = CASCADE)
    val videoId = reference("video_id", Videos, onDelete = CASCADE).index()
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(userId, videoId)
}

object Comments : LongIdTable("comments") {
    val videoId = reference("video_id", Videos, onDelete = CASCADE)
    /** Indexed for the cascade when a user is deleted (migration `*_comments_author_id_index.sql`). */
    val authorId = reference("author_id", Users, onDelete = CASCADE).index()
    /** Top-level comment this reply belongs to; null for top-level comments. */
    val parentId = reference("parent_id", Comments, onDelete = CASCADE).nullable().index()
    val text = varchar("text", ApiLimits.COMMENT_MAX)
    val createdAt = long("created_at")

    init {
        index(false, videoId, parentId)
    }
}

object CommentLikes : Table("comment_likes") {
    val userId = reference("user_id", Users, onDelete = CASCADE)
    val commentId = reference("comment_id", Comments, onDelete = CASCADE).index()
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(userId, commentId)
}

/** Per-viewer watch history, used by the For You ranking. */
object ViewHistory : Table("view_history") {
    val userId = reference("user_id", Users, onDelete = CASCADE)
    val videoId = reference("video_id", Videos, onDelete = CASCADE).index()
    val watchedMs = long("watched_ms")
    val completed = bool("completed")
    val viewedAt = long("viewed_at")
    override val primaryKey = PrimaryKey(userId, videoId)
}

object NotInterested : Table("not_interested") {
    val userId = reference("user_id", Users, onDelete = CASCADE)
    val videoId = reference("video_id", Videos, onDelete = CASCADE).index()
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(userId, videoId)
}

object Notifications : LongIdTable("notifications") {
    val recipientId = reference("recipient_id", Users, onDelete = CASCADE)
    val actorId = reference("actor_id", Users, onDelete = CASCADE).index()
    /** Wire name of a `NotificationType`. */
    val type = varchar("type", 16)
    val videoId = reference("video_id", Videos, onDelete = CASCADE).nullable().index()
    val commentId = reference("comment_id", Comments, onDelete = CASCADE).nullable().index()
    val text = varchar("text", 400).nullable()
    val read = bool("read").default(false)
    val createdAt = long("created_at")

    init {
        index(false, recipientId, createdAt)
    }
}

val allTables: Array<Table> = arrayOf(
    Meta, Users, Sounds, Videos, Hashtags, VideoHashtags, Follows, VideoLikes, Bookmarks,
    Comments, CommentLikes, ViewHistory, NotInterested, Notifications,
)
