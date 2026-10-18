package com.ndizigiye.loop.ui.discover

import com.ndizigiye.loop.common.formatCount

/** "1 video", "12 videos", "1.2K videos". */
internal fun countLabel(count: Long, singular: String, plural: String): String =
    "${formatCount(count)} ${if (count == 1L) singular else plural}"

internal fun videosLabel(count: Long): String = countLabel(count, "video", "videos")

internal fun viewsLabel(count: Long): String = countLabel(count, "view", "views")

internal fun followersLabel(count: Long): String = countLabel(count, "follower", "followers")

/** "<prefix>:<id>" for the first occurrence of an id, "<prefix>:<id>:<n>" for later ones. */
internal fun uniqueKeys(prefix: String, ids: List<Long>): List<String> {
    val seen = HashMap<Long, Int>(ids.size)
    return ids.map { id ->
        val occurrence = seen[id] ?: 0
        seen[id] = occurrence + 1
        if (occurrence == 0) "$prefix:$id" else "$prefix:$id:$occurrence"
    }
}
