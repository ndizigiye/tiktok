package com.ndizigiye.loop.ui.auth

import com.ndizigiye.loop.api.ApiLimits

enum class AuthMode { LOG_IN, SIGN_UP }

internal enum class AuthField { USERNAME, DISPLAY_NAME, PASSWORD }

/** Tab title and submit-button label. */
internal val AuthMode.label: String
    get() = when (this) {
        AuthMode.LOG_IN -> "Log in"
        AuthMode.SIGN_UP -> "Sign up"
    }

/** What the user entered, with client-side validation matching the server's [ApiLimits]. */
data class AuthForm(
    val mode: AuthMode,
    val username: String,
    val displayName: String,
    val password: String,
) {
    val usernameError: String?
        get() = when {
            username.isEmpty() -> "Enter a username"
            username.length !in ApiLimits.USERNAME_MIN..ApiLimits.USERNAME_MAX ->
                "Use ${ApiLimits.USERNAME_MIN} to ${ApiLimits.USERNAME_MAX} characters"
            !username.first().isLetterOrDigit() -> "Start with a letter or a number"
            !ApiLimits.USERNAME_REGEX.matches(username) -> "Use lowercase letters, numbers, '.' and '_'"
            else -> null
        }

    /** Only checked when signing up. */
    val displayNameError: String?
        get() {
            if (mode != AuthMode.SIGN_UP) return null
            val name = displayName.trim()
            return when {
                name.isEmpty() -> "Enter your name"
                name.length > ApiLimits.DISPLAY_NAME_MAX -> "Use at most ${ApiLimits.DISPLAY_NAME_MAX} characters"
                else -> null
            }
        }

    val passwordError: String?
        get() = when {
            password.isEmpty() -> "Enter your password"
            password.length < ApiLimits.PASSWORD_MIN -> "Use at least ${ApiLimits.PASSWORD_MIN} characters"
            else -> null
        }

    val isValid: Boolean
        get() = usernameError == null && displayNameError == null && passwordError == null
}

/** Lowercases and trims [raw] and keeps only characters allowed in usernames, up to the maximum length. */
internal fun sanitizeUsername(raw: String): String =
    raw.trim()
        .lowercase()
        .filter { it in 'a'..'z' || it in '0'..'9' || it == '.' || it == '_' }
        .take(ApiLimits.USERNAME_MAX)
