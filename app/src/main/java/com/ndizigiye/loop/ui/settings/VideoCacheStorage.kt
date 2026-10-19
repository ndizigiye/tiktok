package com.ndizigiye.loop.ui.settings

import com.ndizigiye.loop.player.VideoCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Size and clearing of the shared [VideoCache] for the settings screen. [videoCache]
 * is a provider so the cache is opened off the main thread.
 */
class VideoCacheStorage(private val videoCache: () -> VideoCache) {

    /** Bytes used by cached video, or null when the cache cannot be inspected. */
    suspend fun usedBytes(): Long? = withContext(Dispatchers.IO) {
        try {
            videoCache().usedBytes()
        } catch (e: IllegalStateException) {
            null
        }
    }

    /** Removes every cached video; false when the cache cannot be cleared. */
    suspend fun clear(): Boolean = withContext(Dispatchers.IO) {
        try {
            videoCache().clear()
            true
        } catch (e: IllegalStateException) {
            false
        }
    }
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
