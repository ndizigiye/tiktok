package com.ndizigiye.loop.e2e.harness

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import androidx.test.core.app.ActivityScenario
import com.ndizigiye.loop.MainActivity

/**
 * The player of the video on screen: a feed shows each page's player in a PlayerView, and
 * the current page's one fills most of the window. Robolectric decodes no video, but what
 * the app sets on the player (e.g. its speed) is real.
 */
fun ActivityScenario<MainActivity>.currentPlayer(): Player {
    var player: Player? = null
    onActivity { activity ->
        val decorView = activity.window.decorView
        val window = Rect().also { decorView.getGlobalVisibleRect(it) }
        player = decorView.descendants()
            .filterIsInstance<PlayerView>()
            .firstOrNull { view ->
                val bounds = Rect()
                view.isShown && view.getGlobalVisibleRect(bounds) && bounds.height() * 2 > window.height()
            }
            ?.player
    }
    return player ?: throw AssertionError("No video player fills the screen")
}

private fun View.descendants(): Sequence<View> = sequence {
    yield(this@descendants)
    if (this@descendants is ViewGroup) {
        for (i in 0 until childCount) yieldAll(getChildAt(i).descendants())
    }
}
