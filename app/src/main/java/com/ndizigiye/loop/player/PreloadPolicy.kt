package com.ndizigiye.loop.player

/** Cache the first [maxBytes] of the video [offset] pages after the current one. */
data class PreloadTask(val offset: Int, val maxBytes: Long)

/**
 * Loop's preload model: a short task list gated on how far ahead the video on
 * screen has buffered, plus an early fetch of its comments. Modelled on the
 * behaviour recorded in docs/feature-analysis.md, section 4 "Instant start".
 */
object PreloadPolicy {
    /** Prefetching starts once the current video has this much buffered ahead (or is fully buffered). */
    const val SECURE_BUFFER_MS = 2_500L

    /** In-flight prefetches are cancelled when a playing video has less than this buffered ahead. */
    const val DANGER_BUFFER_MS = 800L

    /** How often the feed samples the current player while it is active. */
    const val POLL_INTERVAL_MS = 250L

    /**
     * The next video already loads in its pooled player (see [FeedPlayerPool]); a
     * prefetch of it could not be cached while that player holds it. So the tasks
     * start after it: the first video without a player up to 2 MB, the one after up
     * to 512 KB.
     */
    val TASKS: List<PreloadTask> = listOf(
        PreloadTask(offset = 2, maxBytes = 2L * 1024 * 1024),
        PreloadTask(offset = 3, maxBytes = 512L * 1024),
    )

    /** Playing time on a page (or one full play of a shorter video) before its first comments are fetched. */
    const val COMMENTS_AFTER_MS = 3_000L
}
