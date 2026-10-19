package com.ndizigiye.loop.data

import com.ndizigiye.loop.api.CountDto
import com.ndizigiye.loop.api.PageDto
import com.ndizigiye.loop.api.ToggleResultDto
import com.ndizigiye.loop.api.VideoDto
import com.ndizigiye.loop.api.ViewEventRequest
import com.ndizigiye.loop.data.remote.LoopApi
import com.ndizigiye.loop.data.remote.ProgressRequestBody
import com.ndizigiye.loop.data.remote.apiCall
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

enum class FeedType { FOR_YOU, FOLLOWING }

class VideoRepository(
    private val api: LoopApi,
    private val interactions: InteractionStore,
) {
    private val _deletedVideos = MutableSharedFlow<Long>()

    /** Ids of videos deleted through [delete], so lists loaded earlier can drop them. */
    val deletedVideos: SharedFlow<Long> = _deletedVideos.asSharedFlow()

    suspend fun feed(type: FeedType, cursor: String?): Result<PageDto<VideoDto>> = page {
        when (type) {
            FeedType.FOR_YOU -> api.forYouFeed(cursor)
            FeedType.FOLLOWING -> api.followingFeed(cursor)
        }
    }

    suspend fun video(id: Long): Result<VideoDto> =
        apiCall { api.video(id) }.onSuccess { interactions.remember(listOf(it)) }

    suspend fun userVideos(username: String, cursor: String?): Result<PageDto<VideoDto>> =
        page { api.userVideos(username, cursor) }

    suspend fun likedVideos(username: String, cursor: String?): Result<PageDto<VideoDto>> =
        page { api.likedVideos(username, cursor) }

    suspend fun bookmarks(cursor: String?): Result<PageDto<VideoDto>> =
        page { api.bookmarks(cursor) }

    suspend fun hashtagVideos(name: String, cursor: String?): Result<PageDto<VideoDto>> =
        page { api.hashtagVideos(name.removePrefix("#").lowercase(), cursor) }

    suspend fun soundVideos(soundId: Long, cursor: String?): Result<PageDto<VideoDto>> =
        page { api.soundVideos(soundId, cursor) }

    /** Optimistically flips the like in [InteractionStore]; reverts on failure. */
    suspend fun setLiked(video: VideoDto, liked: Boolean): Result<ToggleResultDto> =
        toggle(
            video = video,
            optimistic = { stats ->
                if (stats.liked == liked) stats
                else stats.copy(liked = liked, likeCount = (stats.likeCount + if (liked) 1 else -1).coerceAtLeast(0))
            },
            confirmed = { stats, result -> stats.copy(liked = result.active, likeCount = result.count) },
            reverted = { stats, before -> stats.copy(liked = before.liked, likeCount = before.likeCount) },
        ) {
            if (liked) api.likeVideo(video.id) else api.unlikeVideo(video.id)
        }

    /** Optimistically flips the bookmark in [InteractionStore]; reverts on failure. */
    suspend fun setBookmarked(video: VideoDto, bookmarked: Boolean): Result<ToggleResultDto> =
        toggle(
            video = video,
            optimistic = { stats ->
                if (stats.bookmarked == bookmarked) stats
                else stats.copy(
                    bookmarked = bookmarked,
                    bookmarkCount = (stats.bookmarkCount + if (bookmarked) 1 else -1).coerceAtLeast(0),
                )
            },
            confirmed = { stats, result -> stats.copy(bookmarked = result.active, bookmarkCount = result.count) },
            reverted = { stats, before ->
                stats.copy(bookmarked = before.bookmarked, bookmarkCount = before.bookmarkCount)
            },
        ) {
            if (bookmarked) api.bookmarkVideo(video.id) else api.unbookmarkVideo(video.id)
        }

    suspend fun share(video: VideoDto): Result<CountDto> =
        apiCall { api.shareVideo(video.id) }
            .onSuccess { result -> interactions.update(video) { it.copy(shareCount = result.count) } }

    suspend fun recordView(videoId: Long, watchedMs: Long, completed: Boolean): Result<Unit> =
        apiCall { api.recordView(videoId, ViewEventRequest(watchedMs.coerceAtLeast(0), completed)) }

    suspend fun notInterested(videoId: Long): Result<Unit> = apiCall { api.notInterested(videoId) }

    /** Deletes the viewer's own video and announces it on [deletedVideos]. */
    suspend fun delete(videoId: Long): Result<Unit> =
        apiCall { api.deleteVideo(videoId) }.onSuccess { _deletedVideos.emit(videoId) }

    /**
     * Uploads [file] as a new video. [onProgress] receives 0f..1f on a background
     * thread while the file is sent.
     */
    suspend fun upload(
        file: File,
        caption: String,
        soundId: Long?,
        onProgress: (Float) -> Unit,
    ): Result<VideoDto> {
        val body = ProgressRequestBody(file, videoMimeType(file).toMediaType(), onProgress)
        val videoPart = MultipartBody.Part.createFormData("video", file.name, body)
        val captionPart = caption.trim().toRequestBody(TEXT_PLAIN)
        val soundPart = soundId?.toString()?.toRequestBody(TEXT_PLAIN)
        return apiCall { api.uploadVideo(videoPart, captionPart, soundPart) }
            .onSuccess { interactions.remember(listOf(it)) }
    }

    private suspend fun page(block: suspend () -> PageDto<VideoDto>): Result<PageDto<VideoDto>> =
        apiCall(block).onSuccess { interactions.remember(it.items) }

    /**
     * Applies [optimistic] at once, then confirms with the server's answer or restores
     * the previous values. Runs to completion even if the caller is cancelled, so the
     * stored state never stays optimistic without a request behind it.
     */
    private suspend fun toggle(
        video: VideoDto,
        optimistic: (VideoStats) -> VideoStats,
        confirmed: (VideoStats, ToggleResultDto) -> VideoStats,
        reverted: (VideoStats, VideoStats) -> VideoStats,
        request: suspend () -> ToggleResultDto,
    ): Result<ToggleResultDto> = withContext(NonCancellable) {
        val before = interactions.statsFor(video)
        interactions.beginMutation(video.id)
        try {
            interactions.update(video, optimistic)
            apiCall(request)
                .onSuccess { result -> interactions.update(video) { confirmed(it, result) } }
                .onFailure { interactions.update(video) { reverted(it, before) } }
        } finally {
            interactions.endMutation(video.id)
        }
    }

    private companion object {
        val TEXT_PLAIN = "text/plain; charset=utf-8".toMediaType()

        /**
         * The content type of the `video` part. Formats the server does not accept go as
         * octet-stream, so it rejects them with its own message instead of storing them as MP4.
         */
        fun videoMimeType(file: File): String = when (file.extension.lowercase()) {
            "mp4" -> "video/mp4"
            "webm" -> "video/webm"
            "3gp" -> "video/3gpp"
            "mov" -> "video/quicktime"
            else -> "application/octet-stream"
        }
    }
}
