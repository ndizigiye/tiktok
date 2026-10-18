package com.ndizigiye.loop.e2e

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.ndizigiye.loop.e2e.harness.LoopE2eTest
import com.ndizigiye.loop.e2e.harness.currentVideoIdOrNull
import com.ndizigiye.loop.e2e.harness.onCurrentVideo
import com.ndizigiye.loop.e2e.harness.waitFor
import com.ndizigiye.loop.e2e.harness.waitForText
import com.ndizigiye.loop.e2e.harness.waitForVideo
import org.junit.Assert.assertEquals
import org.junit.Test

/** Feature 20: `loop://video/{id}`, the link behind "Open in Loop" on the share page. */
class DeepLinkE2eTest : LoopE2eTest() {

    @Test
    fun openInLoopLinkOpensTheVideo() {
        val video = freshVideo()
        // The link exactly as the server's share page has it.
        val (_, sharePage) = server.webPage("v/${video.id}")
        val link = Regex("href=\"(loop://video/\\d+)\"").find(sharePage)?.groupValues?.get(1)
        assertEquals("loop://video/${video.id}", link)

        app.launch(deepLink = Uri.parse(link))
        assertEquals(video.id, compose.waitForVideo())
        compose.onCurrentVideo(hasText("@${video.author.username}")).assertIsDisplayed()
        compose.onCurrentVideo(hasText(video.caption)).assertIsDisplayed()

        // Back leads to the app's home feed.
        compose.onNode(hasContentDescription("Back")).performClick()
        compose.waitFor("the home feed shows a video") { compose.currentVideoIdOrNull().let { it != null && it != video.id } }
        compose.waitForText("For You")
    }

    @Test
    fun linkReachesTheRunningApp() {
        val video = freshVideo()
        val scenario = app.launch()
        val first = compose.waitForVideo()

        // MainActivity is singleTop: a link while it runs arrives as a new intent.
        val link = Intent(Intent.ACTION_VIEW, Uri.parse("loop://video/${video.id}"))
        scenario.onActivity { activity -> InstrumentationRegistry.getInstrumentation().callActivityOnNewIntent(activity, link) }
        compose.waitFor("the linked video is shown") { compose.currentVideoIdOrNull() == video.id }
        compose.onCurrentVideo(hasText(video.caption)).assertIsDisplayed()

        compose.onNode(hasContentDescription("Back")).performClick()
        compose.waitFor("the feed is back") { compose.currentVideoIdOrNull() == first }
    }

    @Test
    fun linkToAMissingVideoSaysSo() {
        app.launch(deepLink = Uri.parse("loop://video/999999999"))
        compose.waitForText("Video not found.")
    }
}
