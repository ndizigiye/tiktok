package com.ndizigiye.loop.ui.feed

import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.Player

internal data class WatchResult(val watchedMs: Long, val completed: Boolean)

/**
 * Measures one visit of a feed page: the wall-clock time [player] actually played
 * and whether playback reached the end, seen as a loop since feed players repeat
 * their video (the time played can't tell: speed and seeking change what it covers).
 * Main thread only.
 */
internal class WatchTracker(private val player: Player) : Player.Listener {

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
        return WatchResult(watchedMs, completed = looped)
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
