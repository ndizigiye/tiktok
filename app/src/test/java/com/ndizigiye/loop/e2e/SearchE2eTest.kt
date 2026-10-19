package com.ndizigiye.loop.e2e

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.ndizigiye.loop.e2e.harness.ForwardingServer
import com.ndizigiye.loop.e2e.harness.LoopE2eTest
import com.ndizigiye.loop.e2e.harness.hasRole
import com.ndizigiye.loop.e2e.harness.openBottomTab
import com.ndizigiye.loop.e2e.harness.waitForText
import com.ndizigiye.loop.e2e.harness.waitForVideo
import com.ndizigiye.loop.e2e.harness.waitUntilExists
import com.ndizigiye.loop.e2e.harness.waitUntilGone
import com.ndizigiye.loop.ui.discover.RecentSearchStore
import org.junit.Assert.assertEquals
import org.junit.Test

/** Feature 14: search as you type, result tabs and recent searches. */
class SearchE2eTest : LoopE2eTest() {

    private val searchField get() = compose.onNode(hasSetTextAction())

    @Test
    fun searchesAfterTheDebounceAndShowsResultTabs() {
        // The app reaches the server through a proxy that records the requests it makes.
        val proxy = app.closeAfterTest(ForwardingServer(server.baseUrl))
        proxy.start()
        app.container.sessionStore.setBaseUrl(proxy.url)
        launchSignedIn()
        compose.waitForVideo()
        compose.openBottomTab("Discover")
        compose.onNode(hasText("Search") and hasClickAction()).performClick()
        compose.waitForText("Find creators, hashtags and videos")

        // Typed key by key without a pause (the test clock stands still meanwhile): the
        // debounce lets only the whole word reach the server.
        "mira".forEach { key -> searchField.performTextInput(key.toString()) }
        compose.waitUntilExists(resultTab("Top"))
        assertEquals(listOf("mira"), proxy.searchQueries())
        compose.waitForText("Users")
        compose.waitForText("@mira.makes", substring = true)

        val videos = server.search("mira").videos
        compose.onNode(resultTab("Videos")).performClick()
        compose.waitUntilExists(hasContentDescription(videos.first().caption) and hasClickAction())

        compose.onNode(resultTab("Hashtags")).performClick()
        compose.waitForText("No results for \"mira\"")

        compose.onNode(resultTab("Users")).performClick()
        compose.waitForText("@mira.makes", substring = true)

        searchField.performTextReplacement("pottery")
        compose.onNode(resultTab("Hashtags")).performClick()
        compose.waitForText("#pottery")
        compose.onNode(hasText("#pottery") and hasClickAction()).performClick()
        val potteryVideo = server.hashtagVideos("pottery").first()
        compose.waitUntilExists(hasContentDescription(potteryVideo.caption) and hasClickAction())
    }

    @Test
    fun remembersSubmittedSearchesUntilTheyAreRemoved() {
        launchSignedIn()
        compose.waitForVideo()
        // The search button on the home feed.
        compose.onNode(hasContentDescription("Search")).performClick()
        compose.waitForText("Find creators, hashtags and videos")

        for (query in listOf("pottery", "beats")) {
            searchField.performTextReplacement(query)
            searchField.performImeAction()
            compose.waitUntilExists(resultTab("Top"))
        }
        compose.onNode(hasContentDescription("Clear search")).performClick()
        compose.waitForText("Recent searches")
        assertEquals(listOf("beats", "pottery"), RecentSearchStore(app.application).load())

        compose.onNode(recentSearch("pottery")).performClick()
        searchField.assert(hasText("pottery"))
        compose.waitUntilExists(resultTab("Top"))

        compose.onNode(hasContentDescription("Clear search")).performClick()
        compose.onNode(hasContentDescription("Remove \"pottery\" from recent searches")).performClick()
        compose.waitUntilGone(recentSearch("pottery"))
        compose.waitUntilExists(recentSearch("beats"))

        compose.onNode(hasText("Clear all") and hasRole(Role.Button)).performClick()
        compose.waitUntilGone(hasText("Recent searches"))
        compose.waitForText("Find creators, hashtags and videos")
        assertEquals(emptyList<String>(), RecentSearchStore(app.application).load())
    }

    private fun resultTab(title: String) = hasText(title) and hasRole(Role.Tab)

    /** The queries of the searches the app sent through this proxy, oldest first. */
    private fun ForwardingServer.searchQueries(): List<String?> =
        forwarded.filter { it.encodedPath == "/api/search" }.map { it.queryParameter("q") }

    private fun recentSearch(query: String) = hasText(query) and hasClickAction() and !hasSetTextAction()
}
