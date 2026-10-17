package com.ndizigiye.loop.player

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory

/**
 * A small ring of ExoPlayers for a vertical feed: page `i` uses slot `i % size`, so
 * the previous, current and next pages each own a prepared player and a swipe
 * starts playback from an already buffered first frame.
 *
 * Main thread only. Obtain one with [rememberFeedPlayerPool] in Compose.
 */
@androidx.annotation.OptIn(UnstableApi::class)
class FeedPlayerPool(
    context: Context,
    private val videoCache: VideoCache,
    private val size: Int = 3,
) {
    init {
        require(size > 0) { "size must be positive" }
    }

    private val appContext = context.applicationContext
    private val players = arrayOfNulls<ExoPlayer>(size)
    private val urls = arrayOfNulls<String>(size)
    private val pageIndices = IntArray(size) { NO_PAGE }

    private var currentIndex = NO_PAGE
    private var playCurrent = false

    /**
     * The player for page [index], prepared with [url] (an absolute URL), repeating
     * and paused unless [index] is the current page. The media item is only replaced
     * when the slot held a different URL.
     */
    fun acquire(index: Int, url: String): ExoPlayer {
        val slot = slotOf(index)
        val player = players[slot] ?: createPlayer().also { players[slot] = it }
        pageIndices[slot] = index
        if (urls[slot] != url) {
            urls[slot] = url
            player.playWhenReady = index == currentIndex && playCurrent
            player.setMediaItem(MediaItem.fromUri(url))
            player.prepare()
        } else if (index == currentIndex) {
            player.playWhenReady = playCurrent
        }
        return player
    }

    /** Plays (or pauses, when [play] is false) page [index]; pauses and rewinds the others. */
    fun setCurrent(index: Int, play: Boolean) {
        currentIndex = index
        playCurrent = play
        for (slot in 0 until size) {
            val player = players[slot] ?: continue
            if (pageIndices[slot] == index) {
                player.playWhenReady = play
            } else if (player.playWhenReady || player.currentPosition != 0L) {
                player.pause()
                player.seekTo(0)
            }
        }
    }

    fun pauseAll() {
        playCurrent = false
        players.forEach { it?.pause() }
    }

    fun release() {
        for (slot in 0 until size) {
            players[slot]?.release()
            players[slot] = null
            urls[slot] = null
            pageIndices[slot] = NO_PAGE
        }
        currentIndex = NO_PAGE
        playCurrent = false
    }

    private fun slotOf(index: Int): Int = index.mod(size)

    private fun createPlayer(): ExoPlayer {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                MIN_BUFFER_MS,
                MAX_BUFFER_MS,
                BUFFER_FOR_PLAYBACK_MS,
                BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
            )
            .build()
        return ExoPlayer.Builder(appContext)
            .setMediaSourceFactory(DefaultMediaSourceFactory(videoCache.dataSourceFactory))
            .setLoadControl(loadControl)
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .build()
            .apply {
                repeatMode = Player.REPEAT_MODE_ONE
                videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
                playWhenReady = false
            }
    }

    private companion object {
        const val NO_PAGE = Int.MIN_VALUE
        const val MIN_BUFFER_MS = 1_500
        const val MAX_BUFFER_MS = 2_500
        const val BUFFER_FOR_PLAYBACK_MS = 500
        const val BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 1_000
    }
}
