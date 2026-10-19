package com.ndizigiye.loop.e2e.harness

import android.net.Uri
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ndizigiye.loop.api.VideoDto
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Base of the end-to-end tests: the real app (MainActivity, LoopApp, real repositories and
 * OkHttp) on Robolectric, driven through Compose semantics, against a live seeded Loop
 * server (see [LoopAppRule]), on a phone-sized screen. Each test registers its own
 * accounts, so tests don't depend on each other or on their order.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
// Real text layout: with the legacy fakes, texts measure a few pixels wide, and touches miss
// links in captions and controls in bottom sheets.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class LoopE2eTest {

    @get:Rule(order = 0)
    val compose = createEmptyComposeRule()

    @get:Rule(order = 1)
    val app = LoopAppRule()

    protected val server: LoopServerApi get() = app.server

    /**
     * Registers a new account, signs the app in with it and starts the app: on the home
     * feed, or with [openVideo] on that video through a `loop://video/{id}` link.
     */
    protected fun launchSignedIn(displayName: String = "E2E Tester", openVideo: Long? = null): TestAccount {
        val me = server.register(displayName)
        app.signIn(me)
        app.launch(deepLink = openVideo?.let { Uri.parse("loop://video/$it") })
        return me
    }

    /** A new video by a new account, so its counts start at zero. */
    protected fun freshVideo(caption: String = "Fresh e2e clip ${System.nanoTime()}"): VideoDto =
        server.upload(server.register("Clip Maker"), TestMedia.video, caption)
}
