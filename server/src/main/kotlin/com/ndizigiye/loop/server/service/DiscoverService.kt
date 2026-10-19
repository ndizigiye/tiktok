package com.ndizigiye.loop.server.service

import com.ndizigiye.loop.api.HashtagDto
import com.ndizigiye.loop.api.SearchResultDto
import com.ndizigiye.loop.api.SoundDto
import com.ndizigiye.loop.api.TrendingDto
import com.ndizigiye.loop.api.TrendingHashtagDto
import com.ndizigiye.loop.api.UserDto
import com.ndizigiye.loop.api.VideoDto
import com.ndizigiye.loop.server.ApiException
import com.ndizigiye.loop.server.db.Follows
import com.ndizigiye.loop.server.db.Hashtags
import com.ndizigiye.loop.server.db.LoopDatabase
import com.ndizigiye.loop.server.db.Users
import com.ndizigiye.loop.server.db.VideoHashtags
import com.ndizigiye.loop.server.db.Videos
import org.jetbrains.exposed.sql.Case
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.LikePattern
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.notInSubQuery
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.intLiteral
import org.jetbrains.exposed.sql.lowerCase
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.sum

class DiscoverService(private val db: LoopDatabase, private val dto: DtoAssembler) {
    private val hashtagVideos = VideoHashtags
        .join(Hashtags, JoinType.INNER, VideoHashtags.hashtagId, Hashtags.id)
        .join(Videos, JoinType.INNER, VideoHashtags.videoId, Videos.id)

    suspend fun trending(viewerId: Long?): TrendingDto = db.tx {
        TrendingDto(
            hashtags = trendingHashtags(viewerId),
            sounds = popularSounds(),
            creators = suggestedCreators(viewerId),
        )
    }

    suspend fun search(rawQuery: String?, limit: Int, viewerId: Long?): SearchResultDto {
        // No stored text contains U+0000, and PostgreSQL rejects it as a parameter.
        val query = rawQuery?.trim()?.lowercase()?.takeIf(TextRules::canStore).orEmpty()
        if (query.isEmpty()) return SearchResultDto()
        return db.tx {
            SearchResultDto(
                users = query.removePrefix("@").takeIf { it.isNotEmpty() }?.let { searchUsers(it, limit, viewerId) }.orEmpty(),
                videos = searchVideos(query, limit, viewerId),
                hashtags = query.removePrefix("#").takeIf { it.isNotEmpty() }?.let { searchHashtags(it, limit) }.orEmpty(),
            )
        }
    }

    /** 404 unless at least one video uses the hashtag. */
    suspend fun hashtag(name: String): HashtagDto = db.tx {
        val normalized = VideoRules.hashtagName(name)
        dto.hashtags(listOf(normalized))[normalized] ?: throw ApiException.notFound("Hashtag not found.")
    }

    suspend fun sound(id: Long): SoundDto = db.tx {
        dto.sound(id) ?: throw ApiException.notFound("Sound not found.")
    }

    /** Up to 8 hashtags by total views of their videos, each with its 6 most viewed videos. */
    private fun trendingHashtags(viewerId: Long?): List<TrendingHashtagDto> {
        val views = Videos.viewCount.sum()
        val names = hashtagVideos.select(Hashtags.name, views)
            .groupBy(Hashtags.id, Hashtags.name)
            .orderBy(views to SortOrder.DESC, Hashtags.name to SortOrder.ASC)
            .limit(TRENDING_HASHTAGS)
            .map { it[Hashtags.name] }
        val stats = dto.hashtags(names)
        return names.mapNotNull { name ->
            val ids = hashtagVideos.select(Videos.id)
                .where { Hashtags.name eq name }
                .orderBy(Videos.viewCount to SortOrder.DESC, Videos.id to SortOrder.DESC)
                .limit(VIDEOS_PER_HASHTAG)
                .map { it[Videos.id].value }
            stats[name]?.let { TrendingHashtagDto(it, dto.videos(ids, viewerId)) }
        }
    }

    /** Up to 10 sounds by number of videos using them. */
    private fun popularSounds(): List<SoundDto> {
        val uses = Videos.id.count()
        val ids = Videos.select(Videos.soundId, uses)
            .groupBy(Videos.soundId)
            .orderBy(uses to SortOrder.DESC, Videos.soundId to SortOrder.ASC)
            .limit(POPULAR_SOUNDS)
            .map { it[Videos.soundId].value }
        val sounds = dto.sounds(ids)
        return ids.mapNotNull(sounds::get)
    }

    /** Up to 10 accounts with the most followers that the viewer does not follow yet. */
    private fun suggestedCreators(viewerId: Long?): List<UserDto> {
        val followers = Follows.followerId.count()
        val query = Users.join(Follows, JoinType.LEFT, Users.id, Follows.followeeId).select(Users.id, followers)
        if (viewerId != null) {
            val alreadyFollowed = Follows.select(Follows.followeeId).where { Follows.followerId eq viewerId }
            query.where { (Users.id neq viewerId) and (Users.id notInSubQuery alreadyFollowed) }
        }
        val ids = query.groupBy(Users.id)
            .orderBy(followers to SortOrder.DESC, Users.id to SortOrder.ASC)
            .limit(SUGGESTED_CREATORS)
            .map { it[Users.id].value }
        return dto.userList(ids, viewerId)
    }

    /** Exact username match first, then the most followed accounts whose username or name matches. */
    private fun searchUsers(query: String, limit: Int, viewerId: Long?): List<UserDto> {
        val followers = Follows.followerId.count()
        val pattern = containsPattern(query)
        val exactFirst = Case().When(Users.username eq query, intLiteral(0)).Else(intLiteral(1))
        val ids = Users.join(Follows, JoinType.LEFT, Users.id, Follows.followeeId)
            .select(Users.id, followers)
            .where { (Users.username like pattern) or (Users.displayName.lowerCase() like pattern) }
            .groupBy(Users.id)
            .orderBy(exactFirst to SortOrder.ASC, followers to SortOrder.DESC, Users.id to SortOrder.ASC)
            .limit(limit)
            .map { it[Users.id].value }
        return dto.userList(ids, viewerId)
    }

    private fun searchVideos(query: String, limit: Int, viewerId: Long?): List<VideoDto> {
        val ids = Videos.select(Videos.id)
            .where { Videos.caption.lowerCase() like containsPattern(query) }
            .orderBy(Videos.viewCount to SortOrder.DESC, Videos.id to SortOrder.DESC)
            .limit(limit)
            .map { it[Videos.id].value }
        return dto.videos(ids, viewerId)
    }

    private fun searchHashtags(query: String, limit: Int): List<HashtagDto> {
        val uses = VideoHashtags.videoId.count()
        val names = hashtagVideos.select(Hashtags.name, uses)
            .where { Hashtags.name like containsPattern(query) }
            .groupBy(Hashtags.id, Hashtags.name)
            .orderBy(uses to SortOrder.DESC, Hashtags.name to SortOrder.ASC)
            .limit(limit)
            .map { it[Hashtags.name] }
        val stats = dto.hashtags(names)
        return names.mapNotNull(stats::get)
    }

    private companion object {
        const val TRENDING_HASHTAGS = 8
        const val VIDEOS_PER_HASHTAG = 6
        const val POPULAR_SOUNDS = 10
        const val SUGGESTED_CREATORS = 10

        /** LIKE pattern matching [text] anywhere, with LIKE wildcards in [text] taken literally. */
        fun containsPattern(text: String): LikePattern {
            val escaped = text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
            return LikePattern("%$escaped%", '\\')
        }
    }
}
