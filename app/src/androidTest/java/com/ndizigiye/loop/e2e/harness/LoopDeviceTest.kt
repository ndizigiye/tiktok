package com.ndizigiye.loop.e2e.harness

import android.net.Uri
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.runner.RunWith

/**
 * Base of the device end-to-end tests: the real app on an Android device or emulator, with
 * real video decoding, camera, picture-in-picture and services, driven through Compose
 * semantics (UiAutomator and the shell for system UI) against a live seeded Loop server
 * (see [DeviceAppRule]). Each test registers its own accounts, so tests don't depend on
 * each other or on their order.
 */
@RunWith(AndroidJUnit4::class)
abstract class LoopDeviceTest {

    @get:Rule(order = 0)
    val compose = createEmptyComposeRule()

    @get:Rule(order = 1)
    val app = DeviceAppRule()

    protected val server: LoopServerApi get() = app.server

    /**
     * Registers a new account, signs the app in with it and starts the app: on the home
     * feed, or with [openVideo] on that video through a `loop://video/{id}` link.
     */
    protected fun launchSignedIn(displayName: String = "Device Tester", openVideo: Long? = null): TestAccount {
        val me = server.register(displayName)
        app.signIn(me)
        app.launch(deepLink = openVideo?.let { Uri.parse("loop://video/$it") })
        return me
    }
}
