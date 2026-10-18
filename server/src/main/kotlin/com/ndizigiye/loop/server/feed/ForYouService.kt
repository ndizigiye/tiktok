package com.ndizigiye.loop.server.feed

import com.ndizigiye.loop.api.PageDto
import com.ndizigiye.loop.api.VideoDto
import com.ndizigiye.loop.server.db.Bookmarks
import com.ndizigiye.loop.server.db.Comments
import com.ndizigiye.loop.server.db.Follows
import com.ndizigiye.loop.server.db.LoopDatabase
import com.ndizigiye.loop.server.db.NotInterested
import com.ndizigiye.loop.server.db.VideoHashtags
import com.ndizigiye.loop.server.db.VideoLikes
import com.ndizigiye.loop.server.db.Videos
import com.ndizigiye.loop.server.db.ViewHistory
import com.ndizigiye.loop.server.paging.Cursors
import com.ndizigiye.loop.server.paging.PageRequest
import com.ndizigiye.loop.server.service.DtoAssembler
import com.ndizigiye.loop.server.service.countBy
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.selectAll
import java.security.SecureRandom

/**
 * Serves the endless For You feed in passes. A pass is the full candidate list ranked once
 * with a seed; it is kept in memory so pages stay stable while engagement changes, and it
 * can be rebuilt deterministically from the cursor if it was evicted. When a pass runs out,
 * the next cursor starts a new pass with a derived seed.
 */
class ForYouService(
    private val db: LoopDatabase,
    private val dto: DtoAssembler,
    private val passes: PassCache = PassCache(),
) {
    private val random = SecureRandom()

    suspend fun page(viewerId: Long?, page: PageRequest): PageDto<VideoDto> = db.tx {
        var position = Cursors.parseFeed(page.cursor) ?: Cursors.Feed(random.nextLong(), 0, NO_AUTHOR)
        // At most two passes: the rest of the current one, then a fresh one if nothing was left.
        repeat(2) {
            val pass = passFor(viewerId, position)
            if (pass.isEmpty()) return@tx PageDto(emptyList(), null)
            val isHidden = hiddenFilter(viewerId)
            val picked = ArrayList<Long>(page.limit)
            var index = position.offset
            while (index < pass.size && picked.size < page.limit) {
                val id = pass.videoIds[index++]
                if (!isHidden(id)) picked += id
            }
            val next = if (index >= pass.size) {
                Cursors.Feed(ForYouRanker.nextSeed(position.seed), 0, pass.authorIds.last())
            } else {
                position.copy(offset = index)
            }
            val videos = dto.videos(picked, viewerId)
            if (videos.isNotEmpty()) return@tx PageDto(videos, Cursors.feed(next))
            position = next
        }
        PageDto(emptyList(), null)
    }

    private fun passFor(viewerId: Long?, position: Cursors.Feed): Pass {
        val key = PassCache.Key(viewerId ?: 0, position.seed, position.avoidAuthor)
        return passes.getOrPut(key) {
            val ranked = ForYouRanker.rank(
                candidates = candidates(viewerId),
                viewer = signals(viewerId),
                seed = position.seed,
                now = System.currentTimeMillis(),
                avoidFirstAuthor = position.avoidAuthor.takeIf { it != NO_AUTHOR },
            )
            Pass(ranked.map(Candidate::id), ranked.map(Candidate::authorId))
        }
    }

    /** Every video except the viewer's own and those they marked not interested. */
    private fun candidates(viewerId: Long?): List<Candidate> {
        val excluded = viewerId?.let(::notInterested).orEmpty()
        val likes = countBy(VideoLikes, VideoLikes.videoId, null)
        val comments = countBy(Comments, Comments.videoId, null)
        val bookmarks = countBy(Bookmarks, Bookmarks.videoId, null)
        val hashtags = VideoHashtags.selectAll()
            .groupBy({ it[VideoHashtags.videoId].value }, { it[VideoHashtags.hashtagId].value })
        val query = Videos.selectAll()
        if (viewerId != null) query.where { Videos.authorId neq viewerId }
        return query.mapNotNull { row ->
            val id = row[Videos.id].value
            if (id in excluded) return@mapNotNull null
            Candidate(
                id = id,
                authorId = row[Videos.authorId].value,
                createdAt = row[Videos.createdAt],
                likes = likes[id] ?: 0,
                comments = comments[id] ?: 0,
                shares = row[Videos.shareCount],
                bookmarks = bookmarks[id] ?: 0,
                completions = row[Videos.completionCount],
                views = row[Videos.viewCount],
                hashtagIds = hashtags[id].orEmpty().toSet(),
            )
        }
    }

    private fun signals(viewerId: Long?): ViewerSignals {
        if (viewerId == null) return ViewerSignals()
        return ViewerSignals(
            viewerId = viewerId,
            followedAuthors = Follows.select(Follows.followeeId).where { Follows.followerId eq viewerId }
                .mapTo(HashSet()) { it[Follows.followeeId].value },
            likedHashtagIds = VideoHashtags.join(VideoLikes, JoinType.INNER, VideoHashtags.videoId, VideoLikes.videoId)
                .select(VideoHashtags.hashtagId)
                .where { VideoLikes.userId eq viewerId }
                .mapTo(HashSet()) { it[VideoHashtags.hashtagId].value },
            watchedVideoIds = ViewHistory.select(ViewHistory.videoId).where { ViewHistory.userId eq viewerId }
                .mapTo(HashSet()) { it[ViewHistory.videoId].value },
        )
    }

    /** Whether a ranked video must be skipped: deleted or marked not interested since its pass was ranked. */
    private fun hiddenFilter(viewerId: Long?): (Long) -> Boolean {
        val existing = Videos.select(Videos.id).mapTo(HashSet()) { it[Videos.id].value }
        val excluded = viewerId?.let(::notInterested).orEmpty()
        return { id -> id in excluded || id !in existing }
    }

    private fun notInterested(viewerId: Long): Set<Long> =
        NotInterested.select(NotInterested.videoId).where { NotInterested.userId eq viewerId }
            .mapTo(HashSet()) { it[NotInterested.videoId].value }

    private companion object {
        const val NO_AUTHOR = 0L
    }
}

/** One ranked pass: video ids with their authors, in serving order. */
class Pass(val videoIds: List<Long>, val authorIds: List<Long>) {
    val size: Int get() = videoIds.size

    fun isEmpty(): Boolean = videoIds.isEmpty()
}

/** Bounded LRU of ranked passes, keyed by viewer and cursor seed. */
class PassCache(private val maxEntries: Int = 1_000) {
    data class Key(val viewerId: Long, val seed: Long, val avoidAuthor: Long)

    private val entries = object : LinkedHashMap<Key, Pass>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, Pass>): Boolean = size > maxEntries
    }

    /**
     * The cached pass for [key], or [compute]'s. Computing runs outside the lock so concurrent
     * transactions (PostgreSQL) don't queue behind one ranking; when two requests compute the
     * same pass, both get the one stored first.
     */
    fun getOrPut(key: Key, compute: () -> Pass): Pass {
        synchronized(this) { entries[key] }?.let { return it }
        val pass = compute()
        return synchronized(this) { entries.getOrPut(key) { pass } }
    }
}
