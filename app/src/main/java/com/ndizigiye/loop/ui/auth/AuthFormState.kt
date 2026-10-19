package com.ndizigiye.loop.ui.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/**
 * Field values of the auth form and which validation errors to show: a field's
 * error appears once the user has typed in it or tried to submit. The password is
 * deliberately not written to saved instance state.
 */
@Stable
internal class AuthFormState(
    mode: AuthMode = AuthMode.LOG_IN,
    username: String = "",
    displayName: String = "",
    edited: Set<AuthField> = emptySet(),
    submitted: Boolean = false,
) {
    var mode by mutableStateOf(mode)
        private set
    var username by mutableStateOf(username)
        private set
    var displayName by mutableStateOf(displayName)
        private set
    var password by mutableStateOf("")
        private set

    private var edited by mutableStateOf(edited)
    private var submitted by mutableStateOf(submitted)

    val form: AuthForm get() = AuthForm(mode, username, displayName, password)

    fun selectMode(value: AuthMode) {
        mode = value
        submitted = false
    }

    fun updateUsername(value: String) {
        username = sanitizeUsername(value)
        edited = edited + AuthField.USERNAME
    }

    fun updateDisplayName(value: String) {
        displayName = value
        edited = edited + AuthField.DISPLAY_NAME
    }

    fun updatePassword(value: String) {
        password = value
        edited = edited + AuthField.PASSWORD
    }

    fun markSubmitted() {
        submitted = true
    }

    /** The validation error of [field], or null while it is valid or should not be shown yet. */
    fun errorFor(field: AuthField): String? {
        if (!submitted && field !in edited) return null
        val form = form
        return when (field) {
            AuthField.USERNAME -> form.usernameError
            AuthField.DISPLAY_NAME -> form.displayNameError
            AuthField.PASSWORD -> form.passwordError
        }
    }

    companion object {
        val Saver: Saver<AuthFormState, Any> = listSaver(
            save = { state ->
                listOf(
                    state.mode.name,
                    state.username,
                    state.displayName,
                    // The password is not saved, so neither is the fact that it was edited.
                    (state.edited - AuthField.PASSWORD).joinToString(",") { it.name },
                    state.submitted,
                )
            },
            restore = { values ->
                AuthFormState(
                    mode = AuthMode.valueOf(values[0] as String),
                    username = values[1] as String,
                    displayName = values[2] as String,
                    edited = (values[3] as String).split(',').filter { it.isNotEmpty() }.map(AuthField::valueOf).toSet(),
                    submitted = values[4] as Boolean,
                )
            },
        )
    }
}

@Composable
internal fun rememberAuthFormState(): AuthFormState =
    rememberSaveable(saver = AuthFormState.Saver) { AuthFormState() }
