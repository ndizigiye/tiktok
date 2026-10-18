package com.ndizigiye.loop.ui.profile

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ndizigiye.loop.api.ApiLimits
import com.ndizigiye.loop.api.UserDto
import com.ndizigiye.loop.data.UserRepository
import com.ndizigiye.loop.data.copyUriToCache
import com.ndizigiye.loop.data.mimeTypeOf
import com.ndizigiye.loop.data.remote.userMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

data class EditProfileUiState(
    val isSaving: Boolean = false,
    val isUploadingAvatar: Boolean = false,
)

sealed interface EditProfileEvent {
    data object Saved : EditProfileEvent
    data object AvatarUpdated : EditProfileEvent
    data class Error(val message: String) : EditProfileEvent
}

/** Saves the signed-in user's name and bio and uploads a new avatar; both also update SessionStore. */
class EditProfileViewModel(private val userRepository: UserRepository) : ViewModel() {

    private val _state = MutableStateFlow(EditProfileUiState())
    val state: StateFlow<EditProfileUiState> = _state.asStateFlow()

    private val _events = Channel<EditProfileEvent>(Channel.BUFFERED)
    val events: Flow<EditProfileEvent> = _events.receiveAsFlow()

    /** Sends only the fields that differ from [current]. */
    fun save(current: UserDto, displayName: String, bio: String) {
        if (_state.value.isSaving) return
        val name = displayName.trim()
        val newBio = bio.trim()
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                userRepository.updateProfile(
                    displayName = name.takeIf { it != current.displayName },
                    bio = newBio.takeIf { it != current.bio },
                )
                    .onSuccess { _events.send(EditProfileEvent.Saved) }
                    .onFailure { _events.send(EditProfileEvent.Error(it.userMessage())) }
            } finally {
                _state.update { it.copy(isSaving = false) }
            }
        }
    }

    /** Copies the picked image at [uri] into the cache and uploads it as the new avatar. */
    fun uploadAvatar(context: Context, uri: Uri) {
        if (_state.value.isUploadingAvatar) return
        val appContext = context.applicationContext
        _state.update { it.copy(isUploadingAvatar = true) }
        viewModelScope.launch {
            try {
                uploadAvatarFrom(appContext, uri)
            } finally {
                _state.update { it.copy(isUploadingAvatar = false) }
            }
        }
    }

    private suspend fun uploadAvatarFrom(context: Context, uri: Uri) {
        val file = try {
            context.copyUriToCache(uri, AVATAR_FILE_PREFIX)
        } catch (e: IOException) {
            _events.send(EditProfileEvent.Error(UNREADABLE_IMAGE_MESSAGE))
            return
        } catch (e: SecurityException) {
            _events.send(EditProfileEvent.Error(UNREADABLE_IMAGE_MESSAGE))
            return
        }
        try {
            val (mimeType, size) = withContext(Dispatchers.IO) {
                (context.mimeTypeOf(uri) ?: DEFAULT_MIME_TYPE) to file.length()
            }
            if (size > ApiLimits.MAX_AVATAR_BYTES) {
                _events.send(EditProfileEvent.Error(TOO_LARGE_MESSAGE))
                return
            }
            userRepository.uploadAvatar(file, mimeType)
                .onSuccess { _events.send(EditProfileEvent.AvatarUpdated) }
                .onFailure { _events.send(EditProfileEvent.Error(it.userMessage())) }
        } finally {
            withContext(NonCancellable + Dispatchers.IO) { file.delete() }
        }
    }

    private companion object {
        const val AVATAR_FILE_PREFIX = "avatar"
        /** For an image of unknown type: the server then goes by the file name's extension. */
        const val DEFAULT_MIME_TYPE = "application/octet-stream"
        const val UNREADABLE_IMAGE_MESSAGE = "Couldn't open that image. Try another one."
        const val TOO_LARGE_MESSAGE =
            "That image is too large. Choose one under ${ApiLimits.MAX_AVATAR_BYTES / (1024 * 1024)} MB."
    }
}
