package com.ndizigiye.loop.data

import com.ndizigiye.loop.api.HashtagDto
import com.ndizigiye.loop.api.SearchResultDto
import com.ndizigiye.loop.api.SoundDto
import com.ndizigiye.loop.api.TrendingDto
import com.ndizigiye.loop.data.remote.LoopApi
import com.ndizigiye.loop.data.remote.apiCall

class DiscoverRepository(
    private val api: LoopApi,
    private val interactions: InteractionStore,
) {
    suspend fun discover(): Result<TrendingDto> =
        apiCall { api.discover() }.onSuccess { trending ->
            interactions.remember(trending.hashtags.flatMap { it.videos })
            interactions.rememberUsers(trending.creators)
        }

    suspend fun search(query: String): Result<SearchResultDto> =
        apiCall { api.search(query.trim()) }.onSuccess { result ->
            interactions.remember(result.videos)
            interactions.rememberUsers(result.users)
        }

    /** [name] may include a leading '#'; any case. */
    suspend fun hashtag(name: String): Result<HashtagDto> =
        apiCall { api.hashtag(name.removePrefix("#").lowercase()) }

    suspend fun sound(id: Long): Result<SoundDto> = apiCall { api.sound(id) }
}
