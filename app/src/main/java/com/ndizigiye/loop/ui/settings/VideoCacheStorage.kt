package com.ndizigiye.loop.ui.settings

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import com.ndizigiye.loop.player.VideoCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Size and clearing of the shared video cache. Works on the IO dispatcher through the
 * Media3 [Cache] behind [VideoCache.dataSourceFactory]; `SimpleCache` is thread-safe
 * and removing a resource while it is read or prefetched only makes that reader go
 * back to the network. [videoCache] is a provider so the cache is opened off the
 * main thread.
 */
@androidx.annotation.OptIn(UnstableApi::class)
class VideoCacheStorage(private val videoCache: () -> VideoCache) {

    /** Bytes used by cached video, or null when the cache cannot be inspected. */
    suspend fun usedBytes(): Long? = withContext(Dispatchers.IO) {
        try {
            cache()?.cacheSpace
        } catch (e: IllegalStateException) {
            null
        }
    }

    /** Removes every cached video; false when the cache cannot be cleared. */
    suspend fun clear(): Boolean = withContext(Dispatchers.IO) {
        val cache = cache() ?: return@withContext false
        try {
            for (key in cache.keys) cache.removeResource(key)
            true
        } catch (e: IllegalStateException) {
            false
        }
    }

    private fun cache(): Cache? = (videoCache().dataSourceFactory as? CacheDataSource.Factory)?.cache
}

/** "512 KB", "12.3 MB". */
internal fun formatBytes(bytes: Long): String {
    val kib = 1024L
    val mib = kib * 1024
    return when {
        bytes < kib -> "$bytes B"
        bytes < mib -> "${bytes / kib} KB"
        else -> {
            val tenths = bytes * 10 / mib
            "${tenths / 10}.${tenths % 10} MB"
        }
    }
}
