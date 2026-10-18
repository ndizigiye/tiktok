package com.ndizigiye.loop

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.ndizigiye.loop.ui.navigation.LoopApp
import com.ndizigiye.loop.ui.navigation.PictureInPictureLifecycleOwner
import com.ndizigiye.loop.ui.navigation.PictureInPictureState
import com.ndizigiye.loop.ui.navigation.videoIdFromDeepLink
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * The only activity. Opens `loop://video/{id}` links in the video pager, and moves a
 * full-screen video that is playing into a picture-in-picture window when the user
 * leaves the app.
 */
class MainActivity : ComponentActivity() {

    private val pictureInPicture = PictureInPictureState()

    /** Video ids from deep links, buffered until LoopApp collects them. */
    private val deepLinkChannel = Channel<Long>(Channel.UNLIMITED)
    private val deepLinks: Flow<Long> = deepLinkChannel.receiveAsFlow()

    private val supportsPictureInPicture: Boolean by lazy {
        packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.BLACK),
        )
        // A recreated activity restores its back stack, which already shows the linked video, and
        // one relaunched from Recents gets the intent that first opened the app, not a new link.
        if (savedInstanceState == null && (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) == 0) {
            openDeepLink(intent)
        }

        pictureInPicture.setActive(isInPictureInPictureMode)
        if (supportsPictureInPicture && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // From Android 12 the system enters PiP itself (with a smoother animation) while auto-enter is on.
            lifecycleScope.launch {
                pictureInPicture.videoPlaying.collect { playing ->
                    setPictureInPictureParams(pictureInPictureParams(autoEnter = playing))
                }
            }
        }

        val contentLifecycle = PictureInPictureLifecycleOwner(this)
        setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides contentLifecycle) {
                LoopApp(deepLinks = deepLinks, pictureInPicture = pictureInPicture)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openDeepLink(intent)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (supportsPictureInPicture && Build.VERSION.SDK_INT < Build.VERSION_CODES.S && pictureInPicture.isVideoPlaying) {
            enterPictureInPictureMode(pictureInPictureParams(autoEnter = false))
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        pictureInPicture.setActive(isInPictureInPictureMode)
    }

    private fun openDeepLink(intent: Intent?) {
        videoIdFromDeepLink(intent?.data)?.let { deepLinkChannel.trySend(it) }
    }

    /** A portrait 9:16 window, like the videos; [autoEnter] only takes effect from Android 12. */
    private fun pictureInPictureParams(autoEnter: Boolean): PictureInPictureParams =
        PictureInPictureParams.Builder()
            .setAspectRatio(Rational(9, 16))
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setAutoEnterEnabled(autoEnter)
                    // Video doesn't resize seamlessly; let the system cross-fade instead.
                    setSeamlessResizeEnabled(false)
                }
            }
            .build()
}
