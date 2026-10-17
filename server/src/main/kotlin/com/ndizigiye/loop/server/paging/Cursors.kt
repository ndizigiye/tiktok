package com.ndizigiye.loop.server.paging

import com.ndizigiye.loop.api.ApiLimits
import com.ndizigiye.loop.server.ApiException
import java.util.Base64

/** Page size and opaque cursor taken from `?cursor=&limit=`. */
data class PageRequest(val cursor: String?, val limit: Int) {
    companion object {
        fun of(cursor: String?, limit: String?, default: Int = ApiLimits.DEFAULT_PAGE_SIZE): PageRequest {
            val size = when (limit) {
                null, "" -> default
                else -> limit.toIntOrNull() ?: throw ApiException.validation("limit must be a number.")
            }
            return PageRequest(cursor?.takeIf { it.isNotEmpty() }, size.coerceIn(1, ApiLimits.MAX_PAGE_SIZE))
        }
    }
}

/**
 * Cursor formats. Each is a small dot-separated record behind URL-safe base64 so clients
 * treat it as opaque.
 */
object Cursors {
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    /** Position after the row with sort key [key] and id [id] in a keyset-ordered list. */
    data class Key(val key: Long, val id: Long)

    /** For You position: [offset] within the pass ranked with [seed] (see `ForYouService`). */
    data class Feed(val seed: Long, val offset: Int, val avoidAuthor: Long)

    fun key(key: Long, id: Long): String = encode("k", key, id)

    fun offset(offset: Int): String = encode("o", offset.toLong())

    fun feed(cursor: Feed): String = encode("f", cursor.seed, cursor.offset.toLong(), cursor.avoidAuthor)

    fun parseKey(cursor: String?): Key? = cursor?.let {
        val (a, b) = decode(it, "k", 2)
        Key(a, b)
    }

    fun parseOffset(cursor: String?): Int = cursor?.let {
        decode(it, "o", 1)[0].toIntChecked()
    } ?: 0

    fun parseFeed(cursor: String?): Feed? = cursor?.let {
        val (seed, offset, avoid) = decode(it, "f", 3)
        Feed(seed, offset.toIntChecked(), avoid)
    }

    private fun encode(kind: String, vararg values: Long): String =
        encoder.encodeToString((listOf(kind) + values.map(Long::toString)).joinToString(".").toByteArray())

    private fun decode(cursor: String, kind: String, size: Int): List<Long> {
        val parts = runCatching { String(decoder.decode(cursor)).split('.') }.getOrNull()
        if (parts == null || parts.size != size + 1 || parts[0] != kind) throw invalid()
        return parts.drop(1).map { it.toLongOrNull() ?: throw invalid() }
    }

    private fun Long.toIntChecked(): Int = takeIf { it in 0..Int.MAX_VALUE }?.toInt() ?: throw invalid()

    private fun invalid() = ApiException.validation("Invalid cursor.")
}
