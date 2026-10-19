package com.ndizigiye.loop.e2e

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import com.ndizigiye.loop.e2e.harness.LoopE2eTest
import com.ndizigiye.loop.e2e.harness.currentVideoIdOrNull
import com.ndizigiye.loop.e2e.harness.swipeToNextVideo
import com.ndizigiye.loop.e2e.harness.waitFor
import com.ndizigiye.loop.e2e.harness.waitForText
import com.ndizigiye.loop.e2e.harness.waitForVideo
import org.junit.Assert.assertEquals
import org.junit.Test
import org.robolectric.Shadows.shadowOf

/**
 * Media3, CameraX, picture-in-picture and the upload service can't really run on the JVM;
 * these tests only check that the app survives them there. Their behaviour is covered on
 * the emulator.
 */
class PlatformE2eTest : LoopE2eTest() {

    @Test
    fun leavingTheAppDuringPlaybackAndComingBackKeepsTheFeed() {
        shadowOf(app.application.packageManager).setSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE, true)
        app.signIn(server.register())
        val scenario = app.launch()
        val videoId = compose.waitForVideo()

        // Home button: the user leaves (the cue for picture-in-picture), then the activity stops.
        scenario.onActivity { InstrumentationRegistry.getInstrumentation().callActivityOnUserLeaving(it) }
        scenario.moveToState(Lifecycle.State.CREATED)
        scenario.moveToState(Lifecycle.State.RESUMED)

        assertEquals(videoId, compose.waitForVideo())
        compose.swipeToNextVideo()
    }

    @Test
    fun recorderWithoutAUsableCameraStillOffersTheGallery() {
        shadowOf(app.application).grantPermissions(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        app.signIn(server.register())
        app.launch()
        compose.waitForVideo()

        compose.onNode(hasContentDescription("Create")).performClick()
        compose.waitForText("Upload")
        compose.waitForText("Couldn't start the camera.")

        compose.onNode(hasContentDescription("Close")).performClick()
        compose.waitFor("the camera closes") { compose.currentVideoIdOrNull() != null }
    }
}
