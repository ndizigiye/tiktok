package com.ndizigiye.loop.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ndizigiye.loop.api.PageDto
import com.ndizigiye.loop.api.VideoDto
import com.ndizigiye.loop.data.PagedState
import com.ndizigiye.loop.data.Paginator
import com.ndizigiye.loop.data.VideoListSource
import com.ndizigiye.loop.data.VideoRepository
import com.ndizigiye.loop.data.remote.ApiException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/**
 * The list behind the full-screen pager: the handed-off [source] as its first page
 * (continued with the source's own loader), or just [videoId] when the source is
 * gone, e.g. after process death or when opening a single video.
 */
internal class VideoPagerViewModel(
    private val source: VideoListSource?,
    startIndex: Int,
    private val videoId: Long?,
    private val videoRepository: VideoRepository,
    applicationScope: CoroutineScope,
) : ViewModel() {

    private val viewReporter = ViewReporter(videoRepository, applicationScope)

    private val paginator = Paginator<VideoDto>(viewModelScope) { cursor -> load(cursor) }

    val state: StateFlow<PagedState<VideoDto>> = paginator.state

    /** Where the pager opens. */
    val initialPage: Int = initialPageOf(source?.videos.orEmpty(), startIndex, videoId)

    init {
        paginator.loadMore()
    }

    fun loadMore() = paginator.loadMore()

    fun refresh() = paginator.refresh()

    fun remove(videoId: Long) {
        paginator.update { items -> items.filterNot { it.id == videoId } }
    }

    fun recordView(videoId: Long, watchedMs: Long, completed: Boolean) {
        viewReporter.report(videoId, watchedMs, completed)
    }

    private suspend fun load(cursor: String?): Result<PageDto<VideoDto>> {
        if (cursor != null) {
            return source?.loadMore?.invoke(cursor) ?: Result.success(PageDto(emptyList()))
        }
        if (source != null) {
            val nextCursor = if (source.loadMore != null) source.nextCursor else null
            return Result.success(PageDto(source.videos, nextCursor))
        }
        val id = videoId ?: return Result.failure(ApiException(404, "not_found", UNAVAILABLE_MESSAGE))
        return videoRepository.video(id).map { PageDto(listOf(it)) }
    }

    private companion object {
        const val UNAVAILABLE_MESSAGE = "This video is no longer available."
    }
}

/** [startIndex] when it holds [videoId] (or no id was given), else the page holding [videoId]. */
private fun initialPageOf(videos: List<VideoDto>, startIndex: Int, videoId: Long?): Int {
    if (videos.isEmpty()) return 0
    val fallback = startIndex.coerceIn(0, videos.lastIndex)
    if (videoId == null || videos.getOrNull(startIndex)?.id == videoId) return fallback
    val byId = videos.indexOfFirst { it.id == videoId }
    return if (byId >= 0) byId else fallback
}
