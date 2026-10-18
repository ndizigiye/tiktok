package com.ndizigiye.loop.player

import androidx.media3.common.Player
import kotlinx.coroutines.delay

/** Cache the first [bytes] of [url] (an absolute URL). */
data class PrefetchRequest(val url: String, val bytes: Long)

/**
 * Prefetches [requests] only while [player], the video on screen, can spare the
 * bandwidth, and runs until cancelled, sampling [player] every
 * [PreloadPolicy.POLL_INTERVAL_MS]:
 * - [requests] start once [player] has [PreloadPolicy.SECURE_BUFFER_MS] buffered
 *   ahead or holds the whole video;
 * - every in-flight prefetch is cancelled while [player] should be playing but has
 *   less than [PreloadPolicy.DANGER_BUFFER_MS] buffered ahead, and [requests] start
 *   again once it has recovered.
 *
 * Call it from the main thread, which owns [player].
 */
suspend fun VideoCache.prefetchWhileBuffered(player: Player, requests: List<PrefetchRequest>) {
    var started = false
    while (true) {
        val fullyBuffered = player.bufferedPercentage == FULLY_BUFFERED_PERCENT
        val bufferedAheadMs = player.totalBufferedDuration
        val wantsToPlay = player.playWhenReady && player.playbackState != Player.STATE_IDLE
        if (fullyBuffered || bufferedAheadMs >= PreloadPolicy.SECURE_BUFFER_MS) {
            if (!started) requests.forEach { prefetch(it.url, it.bytes) }
            started = true
        } else if (wantsToPlay && bufferedAheadMs < PreloadPolicy.DANGER_BUFFER_MS) {
            cancelPrefetches()
            started = false
        }
        delay(PreloadPolicy.POLL_INTERVAL_MS)
    }
}

private const val FULLY_BUFFERED_PERCENT = 100
