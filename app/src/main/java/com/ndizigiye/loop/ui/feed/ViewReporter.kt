package com.ndizigiye.loop.ui.feed

import com.ndizigiye.loop.data.VideoRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Sends view events. A page reports its view when it stops being current, which
 * is often while its screen is closing, so the request runs in [scope], the
 * app-wide `AppContainer.applicationScope`, instead of the screen's or
 * ViewModel's scope (which would cancel it).
 * Visits during which the video never played are not reported.
 */
internal class ViewReporter(
    private val videoRepository: VideoRepository,
    private val scope: CoroutineScope,
) {

    fun report(videoId: Long, watchedMs: Long, completed: Boolean) {
        if (watchedMs <= 0L && !completed) return
        scope.launch { videoRepository.recordView(videoId, watchedMs, completed) }
    }
}
