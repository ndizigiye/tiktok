package com.ndizigiye.loop.e2e

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import com.ndizigiye.loop.e2e.harness.ForwardingServer
import com.ndizigiye.loop.e2e.harness.LoopDeviceTest
import com.ndizigiye.loop.e2e.harness.hasRole
import com.ndizigiye.loop.e2e.harness.saveScreenshot
import com.ndizigiye.loop.e2e.harness.waitForText
import com.ndizigiye.loop.e2e.harness.waitForVideo
import com.ndizigiye.loop.e2e.harness.waitUntilPlaying
import org.junit.Test

/** Feature 21 on a device: the server is down, the app says so, and retrying works once it is back. */
class ErrorHandlingDeviceTest : LoopDeviceTest() {

    @Test
    fun explainsThatTheServerIsUnreachableAndRecoversOnRetry() {
        val me = server.register()
        // The app's server address, where nothing listens until the proxy starts.
        val proxy = app.closeAfterTest(ForwardingServer(server.baseUrl))
        app.container.sessionStore.setBaseUrl(proxy.url)
        app.signIn(me)
        app.launch()

        compose.waitForText(UNREACHABLE)
        saveScreenshot("error-01-server-down")

        proxy.start()
        compose.onNode(hasText("Try again") and hasRole(Role.Button)).performClick()
        compose.waitForVideo()
        compose.waitUntilPlaying()
        saveScreenshot("error-02-retried-and-playing")
    }

    private companion object {
        const val UNREACHABLE = "Can't reach the server. Check your connection and server URL."
    }
}
