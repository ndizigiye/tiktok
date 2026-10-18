package com.ndizigiye.loop.ui.create

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ndizigiye.loop.api.ApiLimits
import com.ndizigiye.loop.api.SoundDto
import com.ndizigiye.loop.data.DiscoverRepository
import com.ndizigiye.loop.data.VideoRepository
import com.ndizigiye.loop.data.copyUriToCache
import com.ndizigiye.loop.data.remote.userMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

data class PostUiState(
    val cover: CoverState = CoverState.Loading,
    val sound: SoundDto? = null,
    val isUploading: Boolean = false,
    /** Share of the file sent so far, 0f..1f. */
    val progress: Float = 0f,
    /** The video is published; the screen is about to close. */
    val isPosted: Boolean = false,
)

sealed interface PostEvent {
    data object Posted : PostEvent
    data object Cancelled : PostEvent
    data class Failed(val message: String) : PostEvent
}

/**
 * Publishes the video at [videoUri]: a file:// recording is uploaded as is, a
 * content:// pick is copied to the cache first (once, so retries reuse the copy).
 */
class PostViewModel(
    private val appContext: Context,
    private val videoRepository: VideoRepository,
    discoverRepository: DiscoverRepository,
    videoUri: String,
    private val soundId: Long?,
) : ViewModel() {

    private val uri: Uri = Uri.parse(videoUri)

    private val _state = MutableStateFlow(PostUiState())
    val state: StateFlow<PostUiState> = _state.asStateFlow()

    private val _events = Channel<PostEvent>(Channel.BUFFERED)
    val events: Flow<PostEvent> = _events.receiveAsFlow()

    private var uploadJob: Job? = null

    /** Cache copy of a content:// video; deleted once posted or when the screen goes away. */
    private var copy: File? = null

    init {
        viewModelScope.launch {
            val frame = loadCoverFrame(appContext, uri)
            _state.update { it.copy(cover = if (frame != null) CoverState.Ready(frame) else CoverState.Unavailable) }
        }
        if (soundId != null) {
            viewModelScope.launch {
                discoverRepository.sound(soundId).onSuccess { sound -> _state.update { it.copy(sound = sound) } }
            }
        }
    }

    fun post(caption: String) {
        if (uploadJob?.isCompleted == false || _state.value.isPosted) return
        _state.update { it.copy(isUploading = true, progress = 0f) }
        uploadJob = viewModelScope.launch {
            try {
                val file = videoFile()
                if (withContext(Dispatchers.IO) { file.length() } > ApiLimits.MAX_UPLOAD_BYTES) {
                    _events.trySend(PostEvent.Failed(TOO_LARGE_MESSAGE))
                    return@launch
                }
                videoRepository.upload(file, caption, soundId) { progress ->
                    _state.update { it.copy(progress = progress) }
                }
                    .onSuccess {
                        deleteCopy()
                        _state.update { it.copy(isPosted = true) }
                        _events.trySend(PostEvent.Posted)
                    }
                    .onFailure { error -> _events.trySend(PostEvent.Failed(error.userMessage())) }
            } catch (e: IOException) {
                _events.trySend(PostEvent.Failed(UNREADABLE_MESSAGE))
            } catch (e: SecurityException) {
                _events.trySend(PostEvent.Failed(UNREADABLE_MESSAGE))
            } finally {
                _state.update { it.copy(isUploading = false) }
            }
        }
    }

    fun cancelUpload() {
        val job = uploadJob ?: return
        if (!job.isActive) return
        job.cancel()
        _events.trySend(PostEvent.Cancelled)
    }

    override fun onCleared() {
        deleteCopy()
    }

    /** The file to upload: the recording itself, or a cache copy of picked content (reused on retry). */
    private suspend fun videoFile(): File {
        if (uri.scheme == ContentResolver.SCHEME_FILE) {
            val file = uri.path?.let { File(it) }
            if (file == null || !isFile(file)) throw FileNotFoundException(uri.toString())
            return file
        }
        copy?.let { existing -> if (isFile(existing)) return existing }
        return appContext.copyUriToCache(uri, COPY_PREFIX).also { copy = it }
    }

    private suspend fun isFile(file: File): Boolean = withContext(Dispatchers.IO) { file.isFile }

    private fun deleteCopy() {
        copy?.delete()
        copy = null
    }

    private companion object {
        const val COPY_PREFIX = "upload"
        const val UNREADABLE_MESSAGE = "Couldn't read this video. Try picking it again."
        const val TOO_LARGE_MESSAGE =
            "This video is too large. Videos can be up to ${ApiLimits.MAX_UPLOAD_BYTES / (1024 * 1024)} MB."
    }
}
