package com.ndizigiye.loop.ui.feed

import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.Player

internal data class WatchResult(val watchedMs: Long, val completed: Boolean)

/**
 * Measures one visit of a feed page: the wall-clock time [player] actually played
 * and whether the video was watched to the end (it looped, or the time watched
 * reached the duration). Main thread only.
 */
internal class WatchTracker(
    private val player: Player,
    private val durationMs: Long,
) : Player.Listener {

    private var watchedMs = 0L
    private var playingSinceMs = NOT_PLAYING
    private var looped = false

    fun start() {
        player.addListener(this)
        if (player.isPlaying) playingSinceMs = SystemClock.elapsedRealtime()
    }

    /** Stops listening and returns what was watched since [start]. */
    fun stop(): WatchResult {
        stopClock()
        player.removeListener(this)
        val duration = durationMs.takeIf { it > 0L } ?: player.duration.takeIf { it > 0L }
        val completed = looped || (duration != null && watchedMs >= duration)
        return WatchResult(watchedMs, completed)
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (!isPlaying) {
            stopClock()
        } else if (playingSinceMs == NOT_PLAYING) {
            playingSinceMs = SystemClock.elapsedRealtime()
        }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) looped = true
    }

    private fun stopClock() {
        if (playingSinceMs == NOT_PLAYING) return
        watchedMs += SystemClock.elapsedRealtime() - playingSinceMs
        playingSinceMs = NOT_PLAYING
    }

    private companion object {
        const val NOT_PLAYING = -1L
    }
}
