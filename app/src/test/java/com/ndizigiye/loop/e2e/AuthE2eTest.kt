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
import com.ndizigiye.loop.e2e.harness.LoopE2eTest
import com.ndizigiye.loop.e2e.harness.LoopServerApi
import com.ndizigiye.loop.e2e.harness.hasRole
import com.ndizigiye.loop.e2e.harness.openBottomTab
import com.ndizigiye.loop.e2e.harness.waitForText
import com.ndizigiye.loop.e2e.harness.waitUntilGone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Feature 1: sign up (with validation and a taken username), log in, log out, a session surviving recreation. */
class AuthE2eTest : LoopE2eTest() {

    @Test
    fun signUpValidatesTheFormAndRejectsATakenUsername() {
        app.launch()
        openAuthFromProfileTab()
        compose.onNode(hasText("Sign up") and hasRole(Role.Tab)).performClick()

        submit("Sign up")
        compose.waitForText("Enter a username")
        compose.waitForText("Enter your name")
        compose.waitForText("Enter your password")

        field("Username").performTextReplacement("ab")
        field("Password").performTextReplacement("123")
        compose.waitForText("Use 3 to 24 characters")
        compose.waitForText("Use at least 6 characters")

        // A seeded demo account: the server refuses the name.
        field("Username").performTextReplacement("mira.makes")
        field("Name").performTextReplacement("Copy Cat")
        field("Password").performTextReplacement("secret123")
        submit("Sign up")
        compose.waitForText("That username is already taken.")

        val username = LoopServerApi.uniqueUsername()
        field("Username").performTextReplacement(username)
        submit("Sign up")
        compose.waitForText("Welcome, Copy Cat")
        compose.waitForText("@$username")

        val session = app.container.sessionStore.session.value
        assertEquals(username, session?.user?.username)
        assertEquals("Copy Cat", server.user(username).displayName)
    }

    @Test
    fun logsInLogsOutAndKeepsTheSessionAcrossRecreation() {
        val account = server.register(displayName = "Returning User")
        val scenario = app.launch()
        openAuthFromProfileTab()

        field("Username").performTextReplacement(account.username)
        field("Password").performTextReplacement("wrong-password")
        submit("Log in")
        compose.waitForText("Wrong username or password.", substring = true)

        field("Password").performTextReplacement(account.password)
        submit("Log in")
        compose.waitForText("Welcome, Returning User")
        compose.waitForText("@${account.username}")

        val token = app.container.sessionStore.token
        assertNotNull(token)
        scenario.recreate()
        compose.waitForText("@${account.username}")
        assertEquals(token, app.container.sessionStore.token)
        // Persisted, not only kept in memory: a new store (as after process death) reads it back.
        assertEquals(token, SessionStore(app.application).token)

        compose.onNode(hasContentDescription("Settings")).performClick()
        compose.onNode(hasText("Log out") and hasRole(Role.Button) and !hasAnyAncestor(isDialog())).performClick()
        compose.onNode(hasText("Log out") and hasAnyAncestor(isDialog())).performClick()
        compose.waitForText("Logged out")
        assertNull(app.container.sessionStore.session.value)

        compose.openBottomTab("Me")
        compose.waitForText("Log in or sign up")
        compose.waitUntilGone(hasText("@${account.username}"))
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
}
