package com.ndizigiye.loop.ui.create

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ndizigiye.loop.api.SoundDto
import com.ndizigiye.loop.data.DiscoverRepository
import com.ndizigiye.loop.data.upload.UploadManager
import com.ndizigiye.loop.data.upload.UploadState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
 * Publishes the video at [videoUri] through the app-wide [UploadManager], so the upload
 * keeps going when the user leaves the app, and follows that upload's progress and
 * outcome. Uploads started by other post screens are ignored.
 */
class PostViewModel(
    appContext: Context,
    private val uploadManager: UploadManager,
    discoverRepository: DiscoverRepository,
    videoUri: String,
    private val soundId: Long?,
) : ViewModel() {

    private val uri: Uri = Uri.parse(videoUri)

    private val _state = MutableStateFlow(PostUiState())
    val state: StateFlow<PostUiState> = _state.asStateFlow()

    private val _events = Channel<PostEvent>(Channel.BUFFERED)
    val events: Flow<PostEvent> = _events.receiveAsFlow()

    /** The upload this screen started last. */
    private var uploadId: Long? = null

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
        viewModelScope.launch {
            uploadManager.state.collect { upload ->
                if (upload != null && upload.id == uploadId) onUploadChanged(upload)
            }
        }
    }

    fun post(caption: String) {
        val current = _state.value
        if (current.isUploading || current.isPosted) return
        val id = uploadManager.start(uri, caption, soundId)
        if (id == null) {
            _events.trySend(PostEvent.Failed(BUSY_MESSAGE))
            return
        }
        uploadId = id
        _state.update { it.copy(isUploading = true, progress = 0f) }
    }

    fun cancelUpload() {
        val id = uploadId ?: return
        if (_state.value.isUploading) uploadManager.cancel(id)
    }

    private fun onUploadChanged(upload: UploadState) {
        _state.update {
            it.copy(
                isUploading = upload is UploadState.Uploading,
                progress = if (upload is UploadState.Uploading) upload.progress else it.progress,
                isPosted = upload is UploadState.Posted,
            )
        }
        when (upload) {
            is UploadState.Uploading -> Unit
            is UploadState.Posted -> _events.trySend(PostEvent.Posted)
            is UploadState.Cancelled -> _events.trySend(PostEvent.Cancelled)
            is UploadState.Failed -> _events.trySend(PostEvent.Failed(upload.message))
        }
    }

    private companion object {
        const val BUSY_MESSAGE = "Another video is still uploading. Try again when it's done."
    }
}
