package com.ndizigiye.loop.ui.navigation

import androidx.navigation.NavHostController
import com.ndizigiye.loop.api.PageDto
import com.ndizigiye.loop.api.VideoDto
import com.ndizigiye.loop.data.VideoListSource
import com.ndizigiye.loop.di.AppContainer

/** All app navigation goes through here; obtain it from [LocalNavigator]. */
class LoopNavigator(
    private val navController: NavHostController,
    private val container: AppContainer,
) {
    /** Pops the current destination; does nothing on the main shell. */
    fun back() {
        if (navController.previousBackStackEntry != null) navController.popBackStack()
    }

    fun openProfile(username: String) {
        navController.navigate(ProfileRoute(username))
    }

    /**
     * Opens the full-screen pager over an already-loaded list, starting at [startIndex].
     * [loadMore] continues the list from [nextCursor] when the pager nears its end.
     */
    fun openVideoPager(
        videos: List<VideoDto>,
        startIndex: Int,
        nextCursor: String? = null,
        loadMore: (suspend (cursor: String) -> Result<PageDto<VideoDto>>)? = null,
    ) {
        if (videos.isEmpty()) return
        val index = startIndex.coerceIn(0, videos.lastIndex)
        val key = container.videoListHandoff.put(VideoListSource(videos, nextCursor, loadMore))
        navController.navigate(VideoPagerRoute(sourceKey = key, startIndex = index, videoId = videos[index].id))
    }

    /** Opens a single video by id (e.g. from a notification). */
    fun openVideo(videoId: Long) {
        navController.navigate(VideoPagerRoute(sourceKey = null, startIndex = 0, videoId = videoId))
    }

    /** [name] may include a leading '#'; any case. */
    fun openHashtag(name: String) {
        navController.navigate(HashtagRoute(name.removePrefix("#").lowercase()))
    }

    fun openSound(soundId: Long) {
        navController.navigate(SoundRoute(soundId))
    }

    fun openSearch() {
        navController.navigate(SearchRoute) { launchSingleTop = true }
    }

    fun openAuth() {
        navController.navigate(AuthRoute) { launchSingleTop = true }
    }

    /** Opens the recorder; callers check [requireAuth] first. */
    fun openCamera(soundId: Long? = null) {
        navController.navigate(CameraRoute(soundId)) { launchSingleTop = true }
    }

    /** Opens the post screen for a recorded or picked video ([videoUri] is a content:// or file:// URI). */
    fun openPost(videoUri: String, soundId: Long? = null) {
        navController.navigate(PostRoute(videoUri, soundId)) { launchSingleTop = true }
    }

    fun openEditProfile() {
        navController.navigate(EditProfileRoute) { launchSingleTop = true }
    }

    fun openFollowList(username: String, showFollowers: Boolean) {
        navController.navigate(FollowListRoute(username, showFollowers))
    }

    fun openSettings() {
        navController.navigate(SettingsRoute) { launchSingleTop = true }
    }

    /** Returns to the main shell (popping everything above it) and selects [tab]. */
    fun selectTab(tab: MainTab) {
        container.mainTab.value = tab
        navController.popBackStack(route = MainRoute, inclusive = false)
    }

    /** True when signed in; otherwise opens the auth screen and returns false. */
    fun requireAuth(): Boolean {
        if (container.sessionStore.isLoggedIn) return true
        openAuth()
        return false
    }
}
