package com.ndizigiye.loop.e2e

import android.Manifest
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.ndizigiye.loop.common.formatCount
import com.ndizigiye.loop.e2e.harness.LoopE2eTest
import com.ndizigiye.loop.e2e.harness.answerPermissionRequest
import com.ndizigiye.loop.e2e.harness.expectPermissionRequest
import com.ndizigiye.loop.e2e.harness.hasRole
import com.ndizigiye.loop.e2e.harness.openBottomTab
import com.ndizigiye.loop.e2e.harness.waitForText
import com.ndizigiye.loop.e2e.harness.waitForVideo
import com.ndizigiye.loop.e2e.harness.waitUntilExists
import org.junit.Assert.assertTrue
import org.junit.Test

/** Feature 13: trending hashtags, creators and sounds, the hashtag page and the sound page. */
class DiscoverE2eTest : LoopE2eTest() {

    @Test
    fun showsTrendingCreatorsHashtagsAndSoundsAndOpensAHashtag() {
        launchSignedIn()
        compose.waitForVideo()
        val trending = server.discover()
        assertTrue("seeded server has trends", trending.creators.isNotEmpty() && trending.hashtags.isNotEmpty())
        compose.openBottomTab("Discover")

        compose.waitForText("Trending creators")
        compose.waitForText("@${trending.creators.first().username}")

        val hashtag = trending.hashtags.first().hashtag
        compose.waitForText("#${hashtag.name}")
        scrollTo(hasText("Trending sounds"))
        scrollTo(hasText(trending.sounds.first().title))

        scrollTo(hasText("#${hashtag.name}"))
        compose.onNode(hasText("#${hashtag.name}") and hasClickAction()).performClick()
        compose.waitForText("${formatCount(hashtag.videoCount)} videos", substring = true)
        val topVideo = server.hashtagVideos(hashtag.name).first()
        compose.waitUntilExists(hasContentDescription(topVideo.caption) and hasClickAction())
    }

    @Test
    fun soundPageOpensTheCameraWithThatSound() {
        app.signIn(server.register())
        val scenario = app.launch()
        compose.waitForVideo()
        val sound = server.discover().sounds.first()
        compose.openBottomTab("Discover")
        compose.waitForText("Trending creators")
        scrollTo(hasText(sound.title))
        compose.onNode(hasText(sound.title) and hasClickAction()).performClick()

        compose.waitForText(sound.authorName)
        compose.waitForText("${formatCount(sound.videoCount)} videos")
        // The recorder asks for the camera and microphone and names the chosen sound.
        compose.expectPermissionRequest(scenario, Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO) {
            compose.onNode(hasText("Use this sound") and hasRole(Role.Button)).performClick()
        }
        scenario.answerPermissionRequest(grant = false)
        compose.waitForText("Record with Loop")
        compose.waitForText(sound.title)
    }

    /** Scrolls the Discover list (not one of its horizontal rows) to [matcher]. */
    private fun scrollTo(matcher: SemanticsMatcher) {
        compose.onNode(hasScrollToIndexAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
            .performScrollToNode(matcher)
    }
}
