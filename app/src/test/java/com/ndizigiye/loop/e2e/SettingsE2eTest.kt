package com.ndizigiye.loop.e2e

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import com.ndizigiye.loop.e2e.harness.LoopE2eTest
import com.ndizigiye.loop.e2e.harness.eventually
import com.ndizigiye.loop.e2e.harness.hasRole
import com.ndizigiye.loop.e2e.harness.onCurrentVideo
import com.ndizigiye.loop.e2e.harness.openBottomTab
import com.ndizigiye.loop.e2e.harness.waitFor
import com.ndizigiye.loop.e2e.harness.waitForText
import com.ndizigiye.loop.e2e.harness.waitForVideo
import com.ndizigiye.loop.e2e.harness.waitUntilExists
import com.ndizigiye.loop.e2e.harness.waitUntilGone
import com.ndizigiye.loop.ui.discover.RecentSearchStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ServerSocket

/** Feature 18: the server URL with its connection test, and logging out. */
class SettingsE2eTest : LoopE2eTest() {

    private val serverUrlField get() = compose.onNode(hasSetTextAction() and hasText("Server URL"))

    @Test
    fun testsAndSavesANewServerUrl() {
        val me = launchSignedIn()
        compose.waitForVideo()
        openSettings()
        serverUrlField.assert(hasText(server.baseUrl.toString()))

        val deadPort = ServerSocket(0).use { it.localPort }
        serverUrlField.performTextReplacement("127.0.0.1:$deadPort")
        compose.onNode(hasText("Test connection") and hasRole(Role.Button)).performClick()
        compose.waitForText("Couldn't connect to 127.0.0.1:$deadPort. Is the server running?")

        // The same server under another name.
        val newUrl = "http://localhost:${server.baseUrl.port}/"
        serverUrlField.performTextReplacement(newUrl)
        compose.onNode(hasText("Test connection") and hasRole(Role.Button)).performClick()
        compose.waitForText("Connected")
        compose.onNode(hasText("Save") and hasRole(Role.Button)).performClick()
        compose.waitForText("Server saved")
        assertEquals(newUrl, app.container.sessionStore.baseUrl.value)

        // The app now talks to the new address, still signed in.
        compose.onNode(hasContentDescription("Back")).performClick()
        compose.openBottomTab("Home")
        val videoId = compose.waitForVideo()
        compose.onCurrentVideo(hasContentDescription("Like")).performClick()
        compose.eventually { assertTrue(server.video(videoId, me).likedByMe) }
    }

    @Test
    fun logOutClearsEverythingTiedToTheAccount() {
        val me = server.register("Leaving Soon")
        server.follow(server.register("Early Fan"), me.username)
        app.signIn(me)
        app.launch()
        val videoId = compose.waitForVideo()
        compose.waitUntilExists(hasText("Inbox") and hasRole(Role.Tab) and hasText("1"))

        // Per-account state: a like, a follow and a recent search.
        compose.onCurrentVideo(hasContentDescription("Like")).performClick()
        compose.onCurrentVideo(hasContentDescription("Follow")).performClick()
        compose.eventually { assertTrue(server.video(videoId, me).likedByMe) }
        compose.onNode(hasContentDescription("Search")).performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("pottery")
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.waitFor("the search is remembered") { RecentSearchStore(app.application).load() == listOf("pottery") }
        compose.onNode(hasContentDescription("Back")).performClick()

        openSettings()
        compose.onNode(hasText("Log out") and hasRole(Role.Button) and !hasAnyAncestor(isDialog())).performClick()
        compose.waitForText("You'll need the password of @${me.username} to log back in.")
        compose.onNode(hasText("Log out") and hasAnyAncestor(isDialog())).performClick()
        compose.waitForText("Logged out")

        val container = app.container
        assertNull(container.sessionStore.session.value)
        assertEquals(emptyList<String>(), RecentSearchStore(app.application).load())
        assertEquals(0, container.inboxRepository.unreadCount.value)
        compose.waitUntilGone(hasText("Inbox") and hasRole(Role.Tab) and hasText("1"))
        assertTrue("no likes remembered", container.interactions.videos.value.values.none { it.liked || it.bookmarked })
        assertTrue("no follows remembered", container.interactions.following.value.values.none { it })

        // Signed out everywhere: the feed shows the video unliked, the account tabs ask to log in.
        compose.waitForVideo()
        compose.openBottomTab("Inbox")
        compose.waitForText("See likes, comments and new followers here")
        compose.openBottomTab("Me")
        compose.waitForText("Log in or sign up")
    }

    private fun openSettings() {
        compose.openBottomTab("Me")
        compose.onNode(hasContentDescription("Settings")).performClick()
        compose.waitForText("Server URL")
    }
}
