package com.ndizigiye.loop.ui.navigation

import kotlinx.serialization.Serializable

// Type-safe navigation destinations. Navigate through LoopNavigator, not directly.

@Serializable
data object MainRoute

@Serializable
data object AuthRoute

@Serializable
data class ProfileRoute(val username: String)

/**
 * Full-screen video pager. [sourceKey] refers to a list in VideoListHandoff; when it
 * is null or no longer available, the pager loads [videoId] on its own.
 */
@Serializable
data class VideoPagerRoute(val sourceKey: String?, val startIndex: Int, val videoId: Long?)

@Serializable
data class HashtagRoute(val name: String)

@Serializable
data class SoundRoute(val soundId: Long)

@Serializable
data object SearchRoute

@Serializable
data object EditProfileRoute

@Serializable
data class FollowListRoute(val username: String, val showFollowers: Boolean)

@Serializable
data class CameraRoute(val soundId: Long?)

@Serializable
data class PostRoute(val videoUri: String, val soundId: Long?)

@Serializable
data object SettingsRoute
