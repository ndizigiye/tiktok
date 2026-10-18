package com.ndizigiye.loop.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ndizigiye.loop.api.UserDto
import com.ndizigiye.loop.data.AuthRepository
import com.ndizigiye.loop.data.remote.userMessage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthSubmitState(
    val isLoading: Boolean = false,
    /** The server's answer to the last attempt, shown below the submit button. */
    val error: String? = null,
)

class AuthViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _state = MutableStateFlow(AuthSubmitState())
    val state: StateFlow<AuthSubmitState> = _state.asStateFlow()

    private val _signedIn = Channel<UserDto>(Channel.BUFFERED)

    /** Emits the account once, after a successful log-in or sign-up. */
    val signedIn: Flow<UserDto> = _signedIn.receiveAsFlow()

    /** Logs in or signs up with [form]; ignored while a request is running or when the form is invalid. */
    fun submit(form: AuthForm) {
        if (_state.value.isLoading || !form.isValid) return
        _state.value = AuthSubmitState(isLoading = true)
        viewModelScope.launch {
            val result = when (form.mode) {
                AuthMode.LOG_IN -> authRepository.login(form.username, form.password)
                AuthMode.SIGN_UP -> authRepository.register(form.username, form.displayName, form.password)
            }
            result
                // Stays loading on success: the screen closes.
                .onSuccess { user -> _signedIn.trySend(user) }
                .onFailure { error -> _state.value = AuthSubmitState(error = error.userMessage()) }
        }
    }

    fun clearError() {
        _state.update { if (it.error == null) it else it.copy(error = null) }
    }
}
