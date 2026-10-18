package com.ndizigiye.loop.e2e.harness

import android.graphics.Rect
import androidx.annotation.OptIn
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

// What the video on screen is really doing, read from its ExoPlayer: the feed shows each
// page's player in a PlayerView, so the one filling the window belongs to the current page.

/** How long a video may take to start or loop on a slow (software-emulated) device. */
val PlaybackTimeout: Duration = 120.seconds

/** A moment in the playback of the video on screen. */
data class Playback(
    val isPlaying: Boolean,
    val playWhenReady: Boolean,
    val playbackState: Int,
    val positionMs: Long,
    val durationMs: Long,
    val repeatMode: Int,
    val speed: Float,
    /** Frames the video decoder has put on the surface so far. */
    val renderedFrames: Int,
)

/** The player of the PlayerView filling most of the window (the current page), or null; main thread only. */
@OptIn(UnstableApi::class)
fun currentPlayer(): ExoPlayer? {
    val activity = visibleMainActivity() ?: return null
    val window = Rect().also { activity.window.decorView.getGlobalVisibleRect(it) }
    return activity.findViews<PlayerView>()
        .firstOrNull { view ->
            val bounds = Rect()
            view.isShown && view.getGlobalVisibleRect(bounds) && bounds.height() * 2 > window.height()
        }
        ?.player as? ExoPlayer
}

/** The playback of the video on screen, or null when none shows. */
@OptIn(UnstableApi::class)
fun currentPlayback(): Playback? = onMainThread {
    currentPlayer()?.run {
        val counters = videoDecoderCounters?.apply { ensureUpdated() }
        Playback(
            isPlaying = isPlaying,
            playWhenReady = playWhenReady,
            playbackState = playbackState,
            positionMs = currentPosition,
            durationMs = duration,
            repeatMode = repeatMode,
            speed = playbackParameters.speed,
            renderedFrames = counters?.renderedOutputBufferCount ?: 0,
        )
    }
}

/**
 * Waits until the video on screen plays: its player is playing, and between two looks its
 * position moved and the decoder put new frames on the surface. Returns the later look.
 */
fun ComposeTestRule.waitUntilPlaying(timeout: Duration = PlaybackTimeout): Playback {
    var earlier: Playback? = null
    var latest: Playback? = null
    waitFor("the video on screen plays", timeout) {
        val now = currentPlayback()?.takeIf { it.isPlaying }
        val before = earlier
        earlier = now
        latest = now
        now != null && before != null && now.positionMs != before.positionMs && now.renderedFrames > before.renderedFrames
    }
    return latest!!
}

/** Waits until the video on screen is paused and its position stays put. */
fun ComposeTestRule.waitUntilPaused(timeout: Duration = ServerTimeout): Playback {
    var earlier: Playback? = null
    var latest: Playback? = null
    waitFor("the video on screen is paused", timeout) {
        val now = currentPlayback()?.takeIf { !it.isPlaying && !it.playWhenReady }
        val before = earlier
        earlier = now
        latest = now
        now != null && before != null && now.positionMs == before.positionMs
    }
    return latest!!
}

/** Waits until [player] reaches the end of its video and starts it over; false when that took longer than [timeout]. */
fun awaitRepeat(player: Player, timeout: Duration = PlaybackTimeout): Boolean {
    val repeated = CountDownLatch(1)
    val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) repeated.countDown()
        }
    }
    onMainThread { player.addListener(listener) }
    try {
        return repeated.await(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
    } finally {
        onMainThread { player.removeListener(listener) }
    }
}
