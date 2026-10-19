package com.ndizigiye.loop.server

import io.ktor.http.HttpStatusCode

/**
 * An expected failure that maps to an `ErrorDto` response. [message] is shown to users,
 * so it must never contain internal details.
 */
class ApiException(
    val status: HttpStatusCode,
    val code: String,
    override val message: String,
) : RuntimeException(message, null, false, false) {
    companion object {
        fun validation(message: String) = ApiException(HttpStatusCode.BadRequest, "validation", message)
        fun unauthorized(message: String = "Please log in to continue.") =
            ApiException(HttpStatusCode.Unauthorized, "unauthorized", message)
        fun invalidCredentials() =
            ApiException(HttpStatusCode.Unauthorized, "invalid_credentials", "Wrong username or password.")
        fun forbidden(message: String) = ApiException(HttpStatusCode.Forbidden, "forbidden", message)
        fun notFound(message: String) = ApiException(HttpStatusCode.NotFound, "not_found", message)
        fun usernameTaken() =
            ApiException(HttpStatusCode.Conflict, "username_taken", "That username is already taken.")
        fun tooLarge(message: String) = ApiException(HttpStatusCode.PayloadTooLarge, "too_large", message)
    }
}
