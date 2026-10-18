package com.ndizigiye.loop.e2e

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import com.ndizigiye.loop.data.session.SessionStore
import com.ndizigiye.loop.e2e.harness.LoopDeviceTest
import com.ndizigiye.loop.e2e.harness.LoopServerApi
import com.ndizigiye.loop.e2e.harness.hasRole
import com.ndizigiye.loop.e2e.harness.openBottomTab
import com.ndizigiye.loop.e2e.harness.saveScreenshot
import com.ndizigiye.loop.e2e.harness.waitForText
import com.ndizigiye.loop.e2e.harness.waitUntilGone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Feature 1 on a device: sign up (a taken username first), the session across recreation, log out, log in. */
class AuthDeviceTest : LoopDeviceTest() {

    @Test
    fun signsUpKeepsTheSessionLogsOutAndLogsBackIn() {
        val scenario = app.launch()
        openAuthFromProfileTab()
        compose.onNode(hasText("Sign up") and hasRole(Role.Tab)).performClick()

        submit("Sign up")
        compose.waitForText("Enter a username")
        // A seeded demo account: the server refuses the name.
        field("Username").performTextReplacement("mira.makes")
        field("Name").performTextReplacement("Device Person")
        field("Password").performTextReplacement(PASSWORD)
        submit("Sign up")
        compose.waitForText("That username is already taken.")
        saveScreenshot("auth-01-username-taken")

        val username = LoopServerApi.uniqueUsername()
        field("Username").performTextReplacement(username)
        submit("Sign up")
        compose.waitForText("@$username")
        saveScreenshot("auth-02-signed-up-profile")
        assertEquals("Device Person", server.user(username).displayName)

        val token = app.container.sessionStore.token
        assertNotNull(token)
        scenario.recreate()
        compose.waitForText("@$username")
        assertEquals(token, app.container.sessionStore.token)
        assertEquals(token, SessionStore(app.application).token)

        compose.onNode(hasContentDescription("Settings")).performClick()
        compose.onNode(hasText("Log out") and hasRole(Role.Button) and !hasAnyAncestor(isDialog())).performScrollTo().performClick()
        compose.onNode(hasText("Log out") and hasAnyAncestor(isDialog())).performClick()
        compose.waitForText("Logged out")
        assertNull(app.container.sessionStore.session.value)

        openAuthFromProfileTab()
        compose.waitUntilGone(hasText("@$username"))
        field("Username").performTextReplacement(username)
        field("Password").performTextReplacement(PASSWORD)
        submit("Log in")
        compose.waitForText("@$username")
        assertEquals(username, app.container.sessionStore.currentUser?.username)
        saveScreenshot("auth-03-logged-back-in")
    }

    private fun openAuthFromProfileTab() {
        compose.openBottomTab("Me")
        compose.onNode(hasText("Log in or sign up") and hasRole(Role.Button)).performClick()
        compose.waitForText("Watch, make and share short videos.")
    }

    private fun field(label: String) = compose.onNode(hasSetTextAction() and hasText(label))

    private fun submit(label: String) {
        compose.onNode(hasText(label) and hasRole(Role.Button)).performScrollTo().performClick()
    }

    private companion object {
        const val PASSWORD = "device-secret"
    }
}
