package com.ndizigiye.loop.e2e

import android.net.Uri
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import com.ndizigiye.loop.api.VideoDto
import com.ndizigiye.loop.common.formatCount
import com.ndizigiye.loop.e2e.harness.LoopE2eTest
import com.ndizigiye.loop.e2e.harness.TestMedia
import com.ndizigiye.loop.e2e.harness.answerPickerWith
import com.ndizigiye.loop.e2e.harness.currentVideoIdOrNull
import com.ndizigiye.loop.e2e.harness.eventually
import com.ndizigiye.loop.e2e.harness.exists
import com.ndizigiye.loop.e2e.harness.hasRole
import com.ndizigiye.loop.e2e.harness.onCurrentVideo
import com.ndizigiye.loop.e2e.harness.onCurrentVideoPage
import com.ndizigiye.loop.e2e.harness.openBottomTab
import com.ndizigiye.loop.e2e.harness.swipeToNextVideo
import com.ndizigiye.loop.e2e.harness.waitFor
import com.ndizigiye.loop.e2e.harness.waitForText
import com.ndizigiye.loop.e2e.harness.waitForVideo
import com.ndizigiye.loop.e2e.harness.waitUntilExists
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Features 9 (delete), 10, 11 and 12: profiles, following, follow lists and editing your own profile. */
class ProfileE2eTest : LoopE2eTest() {

    @Test
    fun ownProfileShowsCountsTabsAndOpensThePagerFromTheGrid() {
        val me = server.register("Grid Owner")
        val older = server.upload(me, TestMedia.video, "Older clip ${System.nanoTime()}")
        val newer = server.upload(me, TestMedia.video, "Newer clip ${System.nanoTime()}")
        server.follow(server.register("A Fan"), me.username)
        app.signIn(me)
        app.launch()

        compose.openBottomTab("Me")
        compose.waitForText("@${me.username}")
        compose.waitForText("Grid Owner")
        compose.onNode(stat("Followers", 1L)).assertIsDisplayed()
        compose.onNode(stat("Following", 0L)).assertIsDisplayed()
        for (tab in listOf("Videos", "Liked videos", "Saved videos")) {
            compose.onNode(hasContentDescription(tab) and hasRole(Role.Tab)).assertIsDisplayed()
        }

        openFromGrid(newer)
        // The pager carries on with the rest of the grid, newest first.
        assertEquals(older.id, compose.swipeToNextVideo())
        compose.onNode(hasContentDescription("Back")).performClick()

        compose.onNode(hasContentDescription("Saved videos") and hasRole(Role.Tab)).performClick()
        compose.waitForText("Saved videos appear here")
        compose.onNode(hasContentDescription("Liked videos") and hasRole(Role.Tab)).performClick()
        compose.waitForText("No liked videos yet")
    }

    @Test
    fun followsAndUnfollowsFromAnotherProfileWithCounts() {
        val me = launchSignedIn()
        val videoId = compose.waitForVideo()
        val author = server.video(videoId).author
        val followersBefore = server.user(author.username).followerCount

        compose.onCurrentVideo(hasText("@${author.username}")).performClick()
        compose.waitForText(author.displayName)
        compose.onNode(stat("Followers", followersBefore)).assertIsDisplayed()
        // Someone else's profile: videos and likes, but no saved videos.
        compose.onNode(hasContentDescription("Videos") and hasRole(Role.Tab)).assertIsDisplayed()
        compose.onNode(hasContentDescription("Liked videos") and hasRole(Role.Tab)).assertIsDisplayed()
        assertFalse(compose.exists(hasContentDescription("Saved videos")))
        val firstVideo = server.userVideos(author.username).first()
        compose.waitUntilExists(hasContentDescription(firstVideo.caption) and hasClickAction())

        compose.onNode(hasText("Follow") and hasRole(Role.Button)).performClick()
        compose.waitUntilExists(hasText("Following") and hasRole(Role.Button))
        compose.onNode(stat("Followers", followersBefore + 1)).assertIsDisplayed()
        compose.eventually {
            assertTrue(server.followers(author.username).any { it.id == me.user.id })
            assertEquals(followersBefore + 1, server.user(author.username).followerCount)
        }

        compose.onNode(hasText("Following") and hasRole(Role.Button)).performClick()
        compose.waitUntilExists(hasText("Follow") and hasRole(Role.Button))
        compose.onNode(stat("Followers", followersBefore)).assertIsDisplayed()
        compose.eventually {
            assertFalse(server.followers(author.username).any { it.id == me.user.id })
            assertEquals(followersBefore, server.user(author.username).followerCount)
        }
    }

    @Test
    fun followListsShowAccountsThatCanBeFollowedFromTheList() {
        val creator = server.register("List Creator")
        val video = server.upload(creator, TestMedia.video, "Listed ${System.nanoTime()}")
        val fan = server.register("Loyal Fan")
        server.follow(fan, creator.username)
        val me = launchSignedIn(openVideo = video.id)
        compose.waitForVideo()

        compose.onCurrentVideo(hasText("@${creator.username}")).performClick()
        compose.waitForText("List Creator")
        compose.onNode(stat("Followers", 1L)).performClick()

        compose.waitForText("@${fan.username}")
        val fanRow = hasText("@${fan.username}") and hasClickAction()
        compose.onNode(hasText("Follow") and hasAnyAncestor(fanRow)).performClick()
        compose.waitUntilExists(hasText("Following") and hasAnyAncestor(fanRow))
        compose.eventually { assertTrue(server.following(me.username).any { it.id == fan.user.id }) }

        compose.onNode(hasText("Following") and hasRole(Role.Tab)).performClick()
        compose.waitForText("Not following anyone yet")

        // A row opens that account's profile.
        compose.onNode(hasText("Followers") and hasRole(Role.Tab)).performClick()
        compose.onNode(fanRow).performClick()
        compose.waitForText("Loyal Fan")
        compose.waitUntilExists(hasText("Following") and hasRole(Role.Button))
    }

    @Test
    fun editProfileChangesNameBioAndPhoto() {
        val me = server.register("Before Edit")
        app.signIn(me)
        val scenario = app.launch()
        compose.openBottomTab("Me")
        compose.waitForText("Tap Edit profile to add a bio")
        compose.onNode(hasText("Edit profile") and hasRole(Role.Button)).performClick()
        compose.waitForText("Usernames can't be changed.")

        compose.onNode(hasText("Change photo") and hasRole(Role.Button)).performClick()
        val picker = scenario.answerPickerWith(Uri.fromFile(TestMedia.avatar))
        assertEquals("android.provider.action.PICK_IMAGES", picker.action)
        compose.waitForText("Profile photo updated")
        compose.eventually { assertNotNull(server.me(me).avatarUrl) }

        compose.onNode(hasSetTextAction() and hasText("Name")).performTextReplacement("After Edit")
        compose.onNode(hasSetTextAction() and hasText("Bio")).performTextReplacement("Made by the e2e suite #testing")
        compose.onNode(hasText("Save") and hasClickAction()).performClick()
        compose.waitForText("Profile updated")
        compose.waitForText("After Edit")
        compose.waitForText("Made by the e2e suite #testing")
        compose.eventually {
            val saved = server.me(me)
            assertEquals("After Edit", saved.displayName)
            assertEquals("Made by the e2e suite #testing", saved.bio)
        }
    }

    @Test
    fun deletesOwnVideoFromTheOptions() {
        val me = server.register("Deleter")
        val video = server.upload(me, TestMedia.video, "Delete me ${System.nanoTime()}")
        app.signIn(me)
        app.launch()
        compose.openBottomTab("Me")
        openFromGrid(video)

        compose.onCurrentVideoPage().performTouchInput { longClick(center) }
        compose.waitForText("Playback speed")
        compose.onNode(hasText("Delete video")).performClick()
        compose.waitForText("Delete this video?")
        compose.onNode(hasText("Delete") and hasAnyAncestor(isDialog()) and hasClickAction()).performClick()
        compose.waitForText("Video deleted")
        compose.waitForText("No videos left here")
        compose.eventually { assertEquals(404, server.videoStatus(video.id)) }

        compose.onNode(hasContentDescription("Back")).performClick()
        compose.waitForText("No videos yet")
    }

    /** A profile stat ("Following", "Followers") showing [count]. */
    private fun stat(label: String, count: Long): SemanticsMatcher =
        hasText(label) and hasText(formatCount(count)) and hasClickAction() and !hasRole(Role.Button)

    private fun openFromGrid(video: VideoDto) {
        val thumbnail = hasContentDescription(video.caption) and hasClickAction()
        compose.waitUntilExists(thumbnail)
        compose.onNode(thumbnail).performClick()
        compose.waitFor("the pager shows ${video.id}") { compose.currentVideoIdOrNull() == video.id }
    }
}
