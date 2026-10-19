package com.ndizigiye.loop.server.service

import com.ndizigiye.loop.api.ApiLimits
import com.ndizigiye.loop.api.CountDto
import com.ndizigiye.loop.api.NotificationType
import com.ndizigiye.loop.api.PageDto
import com.ndizigiye.loop.api.ToggleResultDto
import com.ndizigiye.loop.api.VideoDto
import com.ndizigiye.loop.api.ViewEventRequest
import com.ndizigiye.loop.common.CaptionParser
import com.ndizigiye.loop.server.ApiException
import com.ndizigiye.loop.server.db.Bookmarks
import com.ndizigiye.loop.server.db.CommentLikes
import com.ndizigiye.loop.server.db.Follows
import com.ndizigiye.loop.server.db.Hashtags
import com.ndizigiye.loop.server.db.LoopDatabase
import com.ndizigiye.loop.server.db.NotInterested
import com.ndizigiye.loop.server.db.Sounds
import com.ndizigiye.loop.server.db.Users
import com.ndizigiye.loop.server.db.VideoHashtags
import com.ndizigiye.loop.server.db.VideoLikes
import com.ndizigiye.loop.server.db.Videos
import com.ndizigiye.loop.server.db.ViewHistory
import com.ndizigiye.loop.server.media.VideoProbe
import com.ndizigiye.loop.server.paging.KeysetOrder
import com.ndizigiye.loop.server.paging.PageRequest
import com.ndizigiye.loop.server.paging.RowPage
import com.ndizigiye.loop.server.paging.offsetIds
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inSubQuery
import org.jetbrains.exposed.sql.SqlExpressionBuilder.notInSubQuery
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.upsert

/** A stored upload, ready to become a video row. */
data class NewVideo(
    val videoFile: String,
    val thumbFile: String?,
    val probe: VideoProbe?,
    val caption: String,
    val soundId: Long?,
)

/** Files that belonged to a deleted video. */
data class DeletedVideoFiles(val videoFile: String, val thumbFile: String?)

class VideoService(private val db: LoopDatabase, private val dto: DtoAssembler) {
    private val newestFirst = KeysetOrder(Videos.createdAt, Videos.id)

    suspend fun create(authorId: Long, video: NewVideo, now: Long = System.currentTimeMillis()): VideoDto = db.tx {
        val id = insertVideo(authorId, video, now)
        dto.video(id, viewerId = authorId)!!
    }

    /** Inserts the video, its hashtags and mention notifications; call inside a transaction. */
    internal fun insertVideo(authorId: Long, video: NewVideo, now: Long): Long {
        val caption = VideoRules.caption(video.caption)
        val soundId = video.soundId
            ?.also { if (Sounds.selectAll().where { Sounds.id eq it }.empty()) throw ApiException.notFound("Sound not found.") }
            ?: createOriginalSound(authorId, now)
        val id = Videos.insertAndGetId {
            it[Videos.authorId] = authorId
            it[Videos.caption] = caption
            it[videoFile] = video.videoFile
            it[thumbFile] = video.thumbFile
            it[width] = video.probe?.width?.takeIf { w -> w > 0 } ?: DEFAULT_WIDTH
            it[height] = video.probe?.height?.takeIf { h -> h > 0 } ?: DEFAULT_HEIGHT
            it[durationMs] = video.probe?.durationMs ?: 0
            it[Videos.soundId] = soundId
            it[createdAt] = now
        }.value
        val hashtags = CaptionParser.hashtags(caption).filter { it.length <= MAX_HASHTAG_LENGTH }
        val hashtagIds = hashtagIds(hashtags)
        hashtags.forEachIndexed { index, name ->
            VideoHashtags.insert {
                it[videoId] = id
                it[hashtagId] = hashtagIds.getValue(name)
                it[position] = index
            }
        }
        Notifier.existingUsers(CaptionParser.mentions(caption), except = authorId).forEach { mentioned ->
            Notifier.notify(mentioned, authorId, NotificationType.MENTION, videoId = id, text = caption, now = now)
        }
        return id
    }

    suspend fun get(videoId: Long, viewerId: Long?): VideoDto = db.tx {
        dto.video(videoId, viewerId) ?: throw videoNotFound()
    }

    /** Deletes the video and everything attached to it; the caller removes the returned files. */
    suspend fun delete(userId: Long, videoId: Long): DeletedVideoFiles = db.tx {
        val row = Videos.selectAll().where { Videos.id eq videoId }.firstOrNull() ?: throw videoNotFound()
        if (row[Videos.authorId].value != userId) throw ApiException.forbidden("Only the creator can delete this video.")
        val soundId = row[Videos.soundId].value
        Videos.deleteWhere { Videos.id eq videoId }
        if (Videos.selectAll().where { Videos.soundId eq soundId }.empty()) {
            Sounds.deleteWhere { Sounds.id eq soundId }
        }
        Hashtags.deleteWhere { Hashtags.id notInSubQuery VideoHashtags.select(VideoHashtags.hashtagId) }
        DeletedVideoFiles(row[Videos.videoFile], row[Videos.thumbFile])
    }

    suspend fun setLiked(userId: Long, videoId: Long, liked: Boolean): ToggleResultDto = db.tx {
        val authorId = authorOf(videoId)
        val changed = toggle(Relation.videoLikes, userId, videoId, liked)
        when {
            !changed -> Unit
            liked -> Notifier.notify(authorId, userId, NotificationType.LIKE, videoId = videoId)
            else -> Notifier.retract(NotificationType.LIKE, actor = userId, videoId = videoId)
        }
        ToggleResultDto(liked, VideoLikes.selectAll().where { VideoLikes.videoId eq videoId }.count())
    }

    suspend fun setBookmarked(userId: Long, videoId: Long, bookmarked: Boolean): ToggleResultDto = db.tx {
        authorOf(videoId)
        toggle(Relation.bookmarks, userId, videoId, bookmarked)
        ToggleResultDto(bookmarked, Bookmarks.selectAll().where { Bookmarks.videoId eq videoId }.count())
    }

    suspend fun share(videoId: Long): CountDto = db.tx {
        authorOf(videoId)
        Videos.update({ Videos.id eq videoId }) { it[shareCount] = shareCount + 1 }
        CountDto(Videos.select(Videos.shareCount).where { Videos.id eq videoId }.single()[Videos.shareCount])
    }

    suspend fun recordView(viewerId: Long?, videoId: Long, event: ViewEventRequest, now: Long = System.currentTimeMillis()) {
        val watchedMs = event.watchedMs.coerceAtLeast(0)
        db.tx {
            authorOf(videoId)
            Videos.update({ Videos.id eq videoId }) {
                it[viewCount] = viewCount + 1
                if (event.completed) it[completionCount] = completionCount + 1
            }
            if (viewerId != null) recordHistory(viewerId, videoId, watchedMs, event.completed, now)
        }
    }

    suspend fun markNotInterested(userId: Long, videoId: Long) {
        db.tx {
            authorOf(videoId)
            NotInterested.insertIgnore {
                it[NotInterested.userId] = userId
                it[NotInterested.videoId] = videoId
                it[createdAt] = System.currentTimeMillis()
            }
        }
    }

    suspend fun userVideos(username: String, viewerId: Long?, page: PageRequest): PageDto<VideoDto> = db.tx {
        val authorId = userId(username)
        videoPage(newestFirst.ids(Videos, Videos.authorId eq authorId, page), viewerId)
    }

    suspend fun likedVideos(username: String, viewerId: Long?, page: PageRequest): PageDto<VideoDto> = db.tx {
        val userId = userId(username)
        val ids = KeysetOrder(VideoLikes.createdAt, VideoLikes.videoId).ids(VideoLikes, VideoLikes.userId eq userId, page)
        videoPage(ids, viewerId)
    }

    suspend fun bookmarks(userId: Long, page: PageRequest): PageDto<VideoDto> = db.tx {
        val ids = KeysetOrder(Bookmarks.createdAt, Bookmarks.videoId).ids(Bookmarks, Bookmarks.userId eq userId, page)
        videoPage(ids, userId)
    }

    suspend fun followingFeed(userId: Long, page: PageRequest): PageDto<VideoDto> = db.tx {
        val followed = Follows.select(Follows.followeeId).where { Follows.followerId eq userId }
        videoPage(newestFirst.ids(Videos, Videos.authorId inSubQuery followed, page), userId)
    }

    suspend fun hashtagVideos(name: String, viewerId: Long?, page: PageRequest): PageDto<VideoDto> = db.tx {
        val query = VideoHashtags.join(Hashtags, JoinType.INNER, VideoHashtags.hashtagId, Hashtags.id)
            .join(Videos, JoinType.INNER, VideoHashtags.videoId, Videos.id)
            .select(Videos.id)
            .where { Hashtags.name eq VideoRules.hashtagName(name) }
            .orderBy(Videos.viewCount to SortOrder.DESC, Videos.id to SortOrder.DESC)
        videoPage(query.offsetIds(Videos.id, page), viewerId)
    }

    suspend fun soundVideos(soundId: Long, viewerId: Long?, page: PageRequest): PageDto<VideoDto> = db.tx {
        if (Sounds.selectAll().where { Sounds.id eq soundId }.empty()) throw ApiException.notFound("Sound not found.")
        val query = Videos.select(Videos.id)
            .where { Videos.soundId eq soundId }
            .orderBy(Videos.viewCount to SortOrder.DESC, Videos.id to SortOrder.DESC)
        videoPage(query.offsetIds(Videos.id, page), viewerId)
    }

    private fun videoPage(ids: RowPage<Long>, viewerId: Long?) =
        PageDto(dto.videos(ids.rows, viewerId), ids.nextCursor)

    private fun createOriginalSound(authorId: Long, now: Long): Long {
        val displayName = Users.select(Users.displayName).where { Users.id eq authorId }.single()[Users.displayName]
        return Sounds.insertAndGetId {
            it[title] = "original sound - $displayName"
            it[authorName] = displayName
            it[Sounds.authorId] = authorId
            it[createdAt] = now
        }.value
    }

    /**
     * Ids of the hashtags [names], creating missing ones. Another upload may create the same
     * hashtag concurrently (PostgreSQL), so missing ones are inserted with "ignore", in name
     * order: two uploads creating the same hashtags then wait for each other instead of deadlocking.
     */
    private fun hashtagIds(names: Collection<String>): Map<String, Long> {
        if (names.isEmpty()) return emptyMap()
        fun existing() = Hashtags.select(Hashtags.id, Hashtags.name)
            .where { Hashtags.name inList names }
            .associate { it[Hashtags.name] to it[Hashtags.id].value }
        val found = existing()
        val missing = names.filter { it !in found }.sorted()
        if (missing.isEmpty()) return found
        missing.forEach { name -> Hashtags.insertIgnore { it[Hashtags.name] = name } }
        return existing()
    }

    /** Adds to the viewer's history in one statement, so concurrent view events add up. */
    private fun recordHistory(viewerId: Long, videoId: Long, watchedMs: Long, completed: Boolean, now: Long) {
        ViewHistory.upsert(
            onUpdate = {
                it[ViewHistory.watchedMs] = ViewHistory.watchedMs + insertValue(ViewHistory.watchedMs)
                it[ViewHistory.completed] = ViewHistory.completed or insertValue(ViewHistory.completed)
                it[ViewHistory.viewedAt] = insertValue(ViewHistory.viewedAt)
            },
        ) {
            it[userId] = viewerId
            it[ViewHistory.videoId] = videoId
            it[ViewHistory.watchedMs] = watchedMs
            it[ViewHistory.completed] = completed
            it[viewedAt] = now
        }
    }

    private fun userId(username: String): Long =
        Users.select(Users.id).where { Users.username eq username.lowercase() }.firstOrNull()?.get(Users.id)?.value
            ?: throw ApiException.notFound("User not found.")

    private companion object {
        const val DEFAULT_WIDTH = 720
        const val DEFAULT_HEIGHT = 1280
        const val MAX_HASHTAG_LENGTH = 100
    }
}

/** Author id of [videoId], or 404. Call inside a transaction. */
internal fun authorOf(videoId: Long): Long =
    Videos.select(Videos.authorId).where { Videos.id eq videoId }.firstOrNull()?.get(Videos.authorId)?.value
        ?: throw videoNotFound()

internal fun videoNotFound() = ApiException.notFound("Video not found.")

/**
 * Inserts or deletes the (user, target) row of a like-style relation.
 * Returns whether anything changed, so repeated calls are idempotent.
 */
internal fun toggle(
    relation: Relation,
    userId: Long,
    targetId: Long,
    active: Boolean,
    now: Long = System.currentTimeMillis(),
): Boolean = with(relation) {
    if (active) {
        table.insertIgnore {
            it[user] = userId
            it[target] = targetId
            it[createdAt] = now
        }.insertedCount > 0
    } else {
        table.deleteWhere { (user eq userId) and (target eq targetId) } > 0
    }
}

/** A (user, target, created_at) relation table such as likes or bookmarks. */
internal class Relation(
    val table: Table,
    val user: Column<EntityID<Long>>,
    val target: Column<EntityID<Long>>,
    val createdAt: Column<Long>,
) {
    companion object {
        val videoLikes = Relation(VideoLikes, VideoLikes.userId, VideoLikes.videoId, VideoLikes.createdAt)
        val bookmarks = Relation(Bookmarks, Bookmarks.userId, Bookmarks.videoId, Bookmarks.createdAt)
        val commentLikes = Relation(CommentLikes, CommentLikes.userId, CommentLikes.commentId, CommentLikes.createdAt)
    }
}

/** Validation and normalisation of video fields. */
object VideoRules {
    fun caption(raw: String): String {
        val caption = raw.trim()
        if (caption.length > ApiLimits.CAPTION_MAX) {
            throw ApiException.validation("Captions can be at most ${ApiLimits.CAPTION_MAX} characters.")
        }
        return TextRules.requireStorable(caption)
    }

    /** Hashtag path parameter: lowercase, without a leading '#'. */
    fun hashtagName(raw: String): String = raw.trim().removePrefix("#").lowercase()
}
