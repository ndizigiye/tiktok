package com.ndizigiye.loop.data

import com.ndizigiye.loop.api.PageDto
import com.ndizigiye.loop.api.VideoDto
import java.util.UUID

/** An already-loaded list of videos and, optionally, how to continue it. */
class VideoListSource(
    val videos: List<VideoDto>,
    val nextCursor: String?,
    val loadMore: (suspend (cursor: String) -> Result<PageDto<VideoDto>>)?,
)

/**
 * Hands a [VideoListSource] from a grid to the full-screen pager without putting
 * it in navigation arguments. Entries live in memory only (a few recent ones are
 * kept), so the pager must cope with [get] returning null after process death.
 */
class VideoListHandoff {

    private val sources = object : LinkedHashMap<String, VideoListSource>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, VideoListSource>?): Boolean =
            size > MAX_ENTRIES
    }

    fun put(source: VideoListSource): String {
        val key = UUID.randomUUID().toString()
        synchronized(sources) { sources[key] = source }
        return key
    }

    fun get(key: String): VideoListSource? = synchronized(sources) { sources[key] }

    private companion object {
        const val MAX_ENTRIES = 16
    }
}
