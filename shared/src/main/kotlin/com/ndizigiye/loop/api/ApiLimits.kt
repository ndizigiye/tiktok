package com.ndizigiye.loop.api

/** Validation limits shared by client-side forms and server-side checks. */
object ApiLimits {
    const val USERNAME_MIN = 3
    const val USERNAME_MAX = 24
    /** Lowercase letters, digits, underscore and dot; must start with a letter or digit. */
    val USERNAME_REGEX = Regex("^[a-z0-9][a-z0-9._]{2,23}$")
    const val DISPLAY_NAME_MAX = 40
    const val PASSWORD_MIN = 6
    const val BIO_MAX = 160
    const val CAPTION_MAX = 300
    const val COMMENT_MAX = 300
    const val DEFAULT_PAGE_SIZE = 10
    const val MAX_PAGE_SIZE = 50
    /** Upload size cap in bytes. */
    const val MAX_UPLOAD_BYTES = 200L * 1024 * 1024
    /** Profile photo size cap in bytes. */
    const val MAX_AVATAR_BYTES = 5L * 1024 * 1024
    /** Maximum recording length offered by the camera screen. */
    const val MAX_RECORDING_MS = 60_000L
}
