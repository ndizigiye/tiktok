package com.ndizigiye.loop.e2e.harness

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import com.ndizigiye.loop.ui.feed.VIDEO_PAGE_TAG_PREFIX
import com.ndizigiye.loop.ui.feed.videoPageTag
import kotlin.math.abs

// Navigation and feed helpers shared by the end-to-end tests. They find nodes the way a
// user does (visible text, content descriptions, roles); only the full-screen video pages
// carry a test tag, because a feed composes its neighbouring pages with identical controls.

fun hasRole(role: Role): SemanticsMatcher = SemanticsMatcher.expectValue(SemanticsProperties.Role, role)

private val isVideoPage = SemanticsMatcher("is a video page") {
    it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(VIDEO_PAGE_TAG_PREFIX) == true
}

/** A bottom-bar tab: "Home", "Discover", "Inbox" or "Me". */
fun ComposeTestRule.onBottomTab(label: String): SemanticsNodeInteraction =
    onNode(hasText(label) and hasRole(Role.Tab) and !hasAnyAncestor(isVideoPage))

fun ComposeTestRule.openBottomTab(label: String) {
    onBottomTab(label).performClick()
    waitForIdle()
}

/** Waits until a feed (the home feed or the full-screen pager) shows a video and returns its id. */
fun ComposeTestRule.waitForVideo(): Long {
    waitFor("a video page is shown") { currentVideoIdOrNull() != null }
    return currentVideoId()
}

/** The id of the video filling the screen in the home feed or the full-screen pager. */
fun ComposeTestRule.currentVideoId(): Long =
    currentVideoIdOrNull() ?: throw AssertionError("No video page fills the screen; ${describeVideoPages()}")

fun ComposeTestRule.currentVideoIdOrNull(): Long? =
    videoPages()
        // Neighbouring pages are composed too, a screen height above or below.
        .filter { (_, node) -> abs(node.positionInRoot.x) < 1f && abs(node.positionInRoot.y) < 1f && node.size.height > 0 }
        .map { it.videoId }
        .singleOrNull()

/**
 * Whether the node is laid out on screen: a pager also keeps pages it no longer shows (for
 * reuse), and their position, (0, 0), means nothing.
 */
private val SemanticsNode.isPlaced: Boolean
    get() = generateSequence(layoutInfo) { it.parentInfo }.all { it.isPlaced && !it.isDeactivated }

private data class VideoPageNode(val videoId: Long, val node: SemanticsNode)

/** Where the composed video pages are, for failure messages. */
private fun ComposeTestRule.describeVideoPages(): String =
    "video pages: " + videoPages().joinToString { "${it.videoId} at ${it.node.positionInRoot}, size ${it.node.size}" }

private fun ComposeTestRule.videoPages(): List<VideoPageNode> =
    onAllNodes(isVideoPage, useUnmergedTree = true)
        .fetchSemanticsNodes(atLeastOneRootRequired = false)
        .filter { it.isPlaced }
        .map { node -> VideoPageNode(node.config[SemanticsProperties.TestTag].removePrefix(VIDEO_PAGE_TAG_PREFIX).toLong(), node) }

/** The node matching [matcher] on the video page filling the screen. */
fun ComposeTestRule.onCurrentVideo(matcher: SemanticsMatcher): SemanticsNodeInteraction =
    onNode(matcher and hasAnyAncestor(hasTestTag(videoPageTag(currentVideoId()))))

fun ComposeTestRule.onCurrentVideoPage(): SemanticsNodeInteraction =
    onNode(hasTestTag(videoPageTag(currentVideoId())), useUnmergedTree = true)

/** Swipes up to the next video and returns its id. */
fun ComposeTestRule.swipeToNextVideo(): Long = swipeVideo(up = true)

/** Swipes down to the previous video and returns its id. */
fun ComposeTestRule.swipeToPreviousVideo(): Long = swipeVideo(up = false)

private fun ComposeTestRule.swipeVideo(up: Boolean): Long {
    val before = currentVideoId()
    onCurrentVideoPage().performTouchInput {
        // Through the middle of the page, clear of the progress bar's scrub area at the bottom.
        if (up) {
            swipeUp(startY = height * 0.75f, endY = height * 0.25f)
        } else {
            swipeDown(startY = height * 0.25f, endY = height * 0.75f)
        }
    }
    try {
        waitFor("the feed settles on another video") { currentVideoIdOrNull().let { it != null && it != before } }
    } catch (e: AssertionError) {
        throw AssertionError("${e.message}; ${describeVideoPages()}", e)
    }
    return currentVideoId()
}
