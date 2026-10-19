package com.ndizigiye.loop.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.ndizigiye.loop.ui.auth.AuthScreen
import com.ndizigiye.loop.ui.create.CameraScreen
import com.ndizigiye.loop.ui.create.PostScreen
import com.ndizigiye.loop.ui.discover.HashtagScreen
import com.ndizigiye.loop.ui.discover.SearchScreen
import com.ndizigiye.loop.ui.discover.SoundScreen
import com.ndizigiye.loop.ui.feed.VideoPagerScreen
import com.ndizigiye.loop.ui.main.MainScreen
import com.ndizigiye.loop.ui.profile.EditProfileScreen
import com.ndizigiye.loop.ui.profile.FollowListScreen
import com.ndizigiye.loop.ui.profile.ProfileScreen
import com.ndizigiye.loop.ui.settings.SettingsScreen

private const val DURATION_MS = 280
private const val RISE_DURATION_MS = 320

private typealias EnterSpec = AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition
private typealias ExitSpec = AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition

/** Full-screen flows that rise from the bottom over the current screen. */
private fun NavBackStackEntry.risesFromBottom(): Boolean =
    destination.hasRoute(CameraRoute::class) || destination.hasRoute(AuthRoute::class)

// Pushed screens slide in a little from the end edge and fade; the screen below
// drifts slightly and fades. Popping reverses it. Rising flows cover the screen
// below, which therefore stays in place.
private val pushEnter: EnterSpec = {
    slideInHorizontally(tween(DURATION_MS, easing = FastOutSlowInEasing)) { it / 5 } +
        fadeIn(tween(DURATION_MS))
}

@OptIn(ExperimentalAnimationApi::class)
private val pushExit: ExitSpec = {
    if (targetState.risesFromBottom()) {
        ExitTransition.KeepUntilTransitionsFinished
    } else {
        slideOutHorizontally(tween(DURATION_MS, easing = FastOutSlowInEasing)) { -it / 10 } +
            fadeOut(tween(DURATION_MS))
    }
}

private val popEnter: EnterSpec = {
    if (initialState.risesFromBottom()) {
        EnterTransition.None
    } else {
        slideInHorizontally(tween(DURATION_MS, easing = FastOutSlowInEasing)) { -it / 10 } +
            fadeIn(tween(DURATION_MS))
    }
}

private val popExit: ExitSpec = {
    slideOutHorizontally(tween(DURATION_MS, easing = FastOutSlowInEasing)) { it / 5 } +
        fadeOut(tween(DURATION_MS))
}

private val riseEnter: EnterSpec = {
    slideInVertically(tween(RISE_DURATION_MS, easing = FastOutSlowInEasing)) { it }
}

private val sinkExit: ExitSpec = {
    slideOutVertically(tween(RISE_DURATION_MS, easing = FastOutSlowInEasing)) { it }
}

@Composable
fun LoopNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(
        navController = navController,
        startDestination = MainRoute,
        modifier = modifier,
        enterTransition = pushEnter,
        exitTransition = pushExit,
        popEnterTransition = popEnter,
        popExitTransition = popExit,
    ) {
        composable<MainRoute> {
            MainScreen()
        }
        composable<AuthRoute>(
            enterTransition = riseEnter,
            popEnterTransition = popEnter,
            popExitTransition = sinkExit,
        ) {
            AuthScreen()
        }
        composable<ProfileRoute> { entry ->
            val route = entry.toRoute<ProfileRoute>()
            ProfileScreen(username = route.username)
        }
        composable<VideoPagerRoute> { entry ->
            val route = entry.toRoute<VideoPagerRoute>()
            VideoPagerScreen(sourceKey = route.sourceKey, startIndex = route.startIndex, videoId = route.videoId)
        }
        composable<HashtagRoute> { entry ->
            HashtagScreen(name = entry.toRoute<HashtagRoute>().name)
        }
        composable<SoundRoute> { entry ->
            SoundScreen(soundId = entry.toRoute<SoundRoute>().soundId)
        }
        composable<SearchRoute> {
            SearchScreen()
        }
        composable<EditProfileRoute> {
            EditProfileScreen()
        }
        composable<FollowListRoute> { entry ->
            val route = entry.toRoute<FollowListRoute>()
            FollowListScreen(username = route.username, showFollowers = route.showFollowers)
        }
        composable<CameraRoute>(
            enterTransition = riseEnter,
            popEnterTransition = popEnter,
            popExitTransition = sinkExit,
        ) { entry ->
            CameraScreen(soundId = entry.toRoute<CameraRoute>().soundId)
        }
        composable<PostRoute> { entry ->
            val route = entry.toRoute<PostRoute>()
            PostScreen(videoUri = route.videoUri, soundId = route.soundId)
        }
        composable<SettingsRoute> {
            SettingsScreen()
        }
    }
}
