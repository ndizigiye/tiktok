package com.ndizigiye.loop.data.upload

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.ndizigiye.loop.api.ApiLimits
import com.ndizigiye.loop.api.VideoDto
import com.ndizigiye.loop.data.VideoRepository
import com.ndizigiye.loop.data.copyUriToCache
import com.ndizigiye.loop.data.remote.userMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

/** The running or most recent upload. [id] tells one post screen's upload from another's. */
sealed interface UploadState {
    val id: Long

    data class Uploading(
        override val id: Long,
        /** Share of the file sent so far, 0f..1f; at 1f the server is processing the video. */
        val progress: Float,
    ) : UploadState

    data class Posted(override val id: Long, val video: VideoDto) : UploadState

    data class Failed(override val id: Long, val message: String) : UploadState

    data class Cancelled(override val id: Long) : UploadState
}

/**
 * Publishes videos, one at a time, independently of the screen that started them. The
 * work runs in the application scope while [UploadService] keeps the process in the
 * foreground with a progress notification, so an upload carries on when the user
 * leaves the app. A file:// recording is uploaded as is; a content:// pick is copied
 * to the cache first (while the picker's read grant is still valid) and the copy is
 * deleted when the upload ends.
 */
class UploadManager(
    context: Context,
    private val videoRepository: VideoRepository,
    private val scope: CoroutineScope,
) {
    private val appContext = context.applicationContext

    private val _state = MutableStateFlow<UploadState?>(null)

    /** Null until the first upload of this process starts. */
    val state: StateFlow<UploadState?> = _state.asStateFlow()

    private var nextId = 1L
    private var job: Job? = null

    /**
     * Starts uploading [videoUri] (file:// or content://) with [caption] and [soundId].
     * Returns the upload's id, or null while another upload is still running.
     * Main thread only.
     */
    fun start(videoUri: Uri, caption: String, soundId: Long?): Long? {
        if (_state.value is UploadState.Uploading) return null
        val id = nextId++
        _state.value = UploadState.Uploading(id, progress = 0f)
        job = scope.launch { upload(id, videoUri, caption, soundId) }
        UploadService.start(appContext)
        return id
    }

    /** Cancels upload [id] if it is still running. Main thread only. */
    fun cancel(id: Long) {
        if (finish(UploadState.Cancelled(id))) job?.cancel()
    }

    private suspend fun upload(id: Long, videoUri: Uri, caption: String, soundId: Long?) {
        var copy: File? = null
        try {
            val file = if (videoUri.scheme == ContentResolver.SCHEME_FILE) {
                recordingFile(videoUri)
            } else {
                appContext.copyUriToCache(videoUri, COPY_PREFIX).also { copy = it }
            }
            if (withContext(Dispatchers.IO) { file.length() } > ApiLimits.MAX_UPLOAD_BYTES) {
                finish(UploadState.Failed(id, TOO_LARGE_MESSAGE))
                return
            }
            videoRepository.upload(file, caption, soundId) { progress ->
                _state.update { current ->
                    if (current is UploadState.Uploading && current.id == id) current.copy(progress = progress) else current
                }
            }
                .onSuccess { video -> finish(UploadState.Posted(id, video)) }
                .onFailure { error -> finish(UploadState.Failed(id, error.userMessage())) }
        } catch (e: IOException) {
            finish(UploadState.Failed(id, UNREADABLE_MESSAGE))
        } catch (e: SecurityException) {
            finish(UploadState.Failed(id, UNREADABLE_MESSAGE))
        } finally {
            copy?.let { withContext(NonCancellable + Dispatchers.IO) { it.delete() } }
        }
    }

    private suspend fun recordingFile(uri: Uri): File {
        val file = uri.path?.let { File(it) }
        if (file == null || !withContext(Dispatchers.IO) { file.isFile }) throw FileNotFoundException(uri.toString())
        return file
    }

    /** Ends the upload that [result] is about with [result]; false when that upload had already ended. */
    private fun finish(result: UploadState): Boolean {
        var finished = false
        _state.update { current ->
            finished = current is UploadState.Uploading && current.id == result.id
            if (finished) result else current
        }
        return finished
    }

    private companion object {
        const val COPY_PREFIX = "upload"
        const val UNREADABLE_MESSAGE = "Couldn't read this video. Try picking it again."
        const val TOO_LARGE_MESSAGE =
            "This video is too large. Videos can be up to ${ApiLimits.MAX_UPLOAD_BYTES / (1024 * 1024)} MB."
    }
}
