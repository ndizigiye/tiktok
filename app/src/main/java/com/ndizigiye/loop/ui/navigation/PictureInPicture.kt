package com.ndizigiye.loop.ui.navigation

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Picture-in-picture (PiP) for full-screen video, shared by MainActivity and the video
 * pages. Pages report while their video plays ([PictureInPictureVideo]), so the activity
 * knows when leaving the app should continue the video in a PiP window; while
 * [isActive] they hide their overlays so only the video shows.
 */
class PictureInPictureState {

    private val _isActive = MutableStateFlow(false)

    /** The activity is in picture-in-picture mode. */
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    private val playingVideos = MutableStateFlow(0)

    /** Whether a full-screen video is playing, emitted on every change. */
    val videoPlaying: Flow<Boolean> = playingVideos.map { it > 0 }.distinctUntilChanged()

    val isVideoPlaying: Boolean get() = playingVideos.value > 0

    fun setActive(active: Boolean) {
        _isActive.value = active
    }

    internal fun onVideoStarted() {
        playingVideos.update { it + 1 }
    }

    internal fun onVideoStopped() {
        playingVideos.update { it - 1 }
    }
}

/** Picture-in-picture state of the host activity, provided by LoopApp. */
val LocalPictureInPicture: ProvidableCompositionLocal<PictureInPictureState> =
    staticCompositionLocalOf { error("LocalPictureInPicture not provided") }

/** Marks a full-screen video as playing while [playing]; leaving the app then continues it in PiP. */
@Composable
fun PictureInPictureVideo(playing: Boolean) {
    val pictureInPicture = LocalPictureInPicture.current
    DisposableEffect(pictureInPicture, playing) {
        if (playing) pictureInPicture.onVideoStarted()
        onDispose { if (playing) pictureInPicture.onVideoStopped() }
    }
}

/**
 * [activity]'s lifecycle for the app's content, except that it stays RESUMED while the
 * activity is paused in picture-in-picture: Android pauses an activity in PiP, but its
 * video should keep playing until the PiP window is closed, which stops the activity.
 * Main thread only.
 */
class PictureInPictureLifecycleOwner(private val activity: ComponentActivity) : LifecycleOwner {

    private val registry = LifecycleRegistry(this)

    override val lifecycle: Lifecycle get() = registry

    init {
        activity.lifecycle.addObserver(LifecycleEventObserver { _, _ -> sync() })
        // Leaving PiP only takes effect with the next lifecycle event (resumed when expanded,
        // stopped when closed), so expanding the window doesn't pause the video for a moment.
        activity.addOnPictureInPictureModeChangedListener { info -> if (info.isInPictureInPictureMode) sync() }
    }

    private fun sync() {
        val state = activity.lifecycle.currentState
        // The activity is already in PiP mode when it pauses on the way there.
        registry.currentState = if (state == Lifecycle.State.STARTED && activity.isInPictureInPictureMode) {
            Lifecycle.State.RESUMED
        } else {
            state
        }
    }
}
