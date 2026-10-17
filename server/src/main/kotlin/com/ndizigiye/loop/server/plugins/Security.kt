package com.ndizigiye.loop.server.plugins

import com.ndizigiye.loop.api.ErrorDto
import com.ndizigiye.loop.server.ApiException
import com.ndizigiye.loop.server.auth.TokenService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.principal
import io.ktor.server.response.respond

/** Name of the bearer-token provider; `[opt]` routes use it with `optional = true`. */
const val JWT_AUTH = "jwt"

/** The signed-in user of a request. */
data class UserPrincipal(val userId: Long)

/**
 * Bearer JWT authentication. [userExists] rejects tokens of accounts that are gone, so a
 * stale token is a clean 401 instead of a failure deeper in a handler.
 */
fun Application.configureSecurity(tokens: TokenService, userExists: suspend (Long) -> Boolean) {
    install(Authentication) {
        jwt(JWT_AUTH) {
            realm = "loop"
            verifier(tokens.verifier)
            validate { credential ->
                credential.payload.getClaim(TokenService.UID_CLAIM).asLong()
                    ?.takeIf { userExists(it) }
                    ?.let(::UserPrincipal)
            }
            challenge { _, _ ->
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorDto("unauthorized", "Your session has expired. Please log in again."),
                )
            }
        }
    }
}

/** The signed-in user's id, or null for anonymous requests. */
fun ApplicationCall.viewerId(): Long? = principal<UserPrincipal>()?.userId

/** The signed-in user's id; throws 401 for anonymous requests. */
fun ApplicationCall.userId(): Long = viewerId() ?: throw ApiException.unauthorized()
