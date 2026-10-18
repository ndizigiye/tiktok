package com.ndizigiye.loop.ui.feed

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.ndizigiye.loop.api.UserDto
import com.ndizigiye.loop.api.VideoDto
import com.ndizigiye.loop.data.remote.userMessage
import com.ndizigiye.loop.di.AppContainer
import com.ndizigiye.loop.ui.common.LocalSnackbarHostState
import com.ndizigiye.loop.ui.navigation.LocalAppContainer
import com.ndizigiye.loop.ui.navigation.LocalNavigator
import com.ndizigiye.loop.ui.navigation.LoopNavigator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The account-bound actions of a feed page. Each asks signed-out viewers to log in
 * first, updates the shared InteractionStore through the repositories and reports
 * failures in the app snackbar.
 */
@Stable
internal class VideoActions(
    private val container: AppContainer,
    private val navigator: LoopNavigator,
    private val snackbarHostState: SnackbarHostState,
    private val scope: CoroutineScope,
) {
    fun toggleLike(video: VideoDto) {
        if (!navigator.requireAuth()) return
        setLiked(video, !container.interactions.statsFor(video).liked)
    }

    /** Likes [video] unless it already is. Returns false when the viewer has to log in first. */
    fun likeByDoubleTap(video: VideoDto): Boolean {
        if (!navigator.requireAuth()) return false
        if (!container.interactions.statsFor(video).liked) setLiked(video, true)
        return true
    }

    fun toggleBookmark(video: VideoDto) {
        if (!navigator.requireAuth()) return
        val bookmarked = container.interactions.statsFor(video).bookmarked
        scope.launch {
            container.videoRepository.setBookmarked(video, !bookmarked).onFailure { showError(it) }
        }
    }

    /** Follows [user]. Returns false when the viewer has to log in first. */
    fun follow(user: UserDto): Boolean {
        if (!navigator.requireAuth()) return false
        scope.launch {
            container.userRepository.setFollowing(user, follow = true).onFailure { showError(it) }
        }
        return true
    }

    /** Hides [video] from the viewer's For You feed and calls [onRemoved] once the server agreed. */
    fun notInterested(video: VideoDto, onRemoved: (videoId: Long) -> Unit) {
        if (!navigator.requireAuth()) return
        scope.launch {
            withContext(NonCancellable) { container.videoRepository.notInterested(video.id) }
                .onSuccess {
                    onRemoved(video.id)
                    snackbarHostState.showSnackbar("Got it. You'll see fewer videos like this.")
                }
                .onFailure { showError(it) }
        }
    }

    /** Deletes the viewer's own [video] and calls [onRemoved] when it is gone. */
    fun delete(video: VideoDto, onRemoved: (videoId: Long) -> Unit) {
        scope.launch {
            withContext(NonCancellable) { container.videoRepository.delete(video.id) }
                .onSuccess {
                    onRemoved(video.id)
                    snackbarHostState.showSnackbar("Video deleted")
                }
                .onFailure { showError(it) }
        }
    }

    fun showMessage(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    private fun setLiked(video: VideoDto, liked: Boolean) {
        scope.launch {
            container.videoRepository.setLiked(video, liked).onFailure { showError(it) }
        }
    }

    private suspend fun showError(error: Throwable) {
        snackbarHostState.showSnackbar(error.userMessage())
    }
}

@Composable
internal fun rememberVideoActions(): VideoActions {
    val container = LocalAppContainer.current
    val navigator = LocalNavigator.current
    val snackbarHostState = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    return remember(container, navigator, snackbarHostState, scope) {
        VideoActions(container, navigator, snackbarHostState, scope)
    }
}
