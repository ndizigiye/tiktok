package com.ndizigiye.loop.e2e

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import com.ndizigiye.loop.e2e.harness.ForwardingServer
import com.ndizigiye.loop.e2e.harness.LoopE2eTest
import com.ndizigiye.loop.e2e.harness.hasRole
import com.ndizigiye.loop.e2e.harness.openBottomTab
import com.ndizigiye.loop.e2e.harness.waitForText
import com.ndizigiye.loop.e2e.harness.waitForVideo
import org.junit.Test

/** Feature 21: the server is down, the app says so, and retrying works once the server is back. */
class ErrorHandlingE2eTest : LoopE2eTest() {

    @Test
    fun explainsThatTheServerIsUnreachableAndRecoversOnRetry() {
        val me = server.register()
        // The app's server address, where nothing listens until the proxy starts.
        val proxy = app.closeAfterTest(ForwardingServer(server.baseUrl))
        app.container.sessionStore.setBaseUrl(proxy.url)
        app.signIn(me)
        app.launch()

        compose.waitForText(UNREACHABLE)
        compose.openBottomTab("Discover")
        compose.waitForText(UNREACHABLE)

        proxy.start()
        compose.onNode(hasText("Try again") and hasRole(Role.Button)).performClick()
        compose.waitForText("Trending creators")

        compose.openBottomTab("Home")
        compose.onNode(hasText("Try again") and hasRole(Role.Button)).performClick()
        compose.waitForVideo()
    }

    private companion object {
        const val UNREACHABLE = "Can't reach the server. Check your connection and server URL."
    }
}
