package com.ndizigiye.loop.server.plugins

import com.ndizigiye.loop.api.ErrorDto
import com.ndizigiye.loop.server.ApiException
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.plugins.PayloadTooLargeException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory

/** Maps every failure to an `ErrorDto` with a documented code; internals are only logged. */
fun Application.configureErrorHandling() {
    val log = LoggerFactory.getLogger("com.ndizigiye.loop.server.Errors")
    install(StatusPages) {
        exception<ApiException> { call, e -> call.respondError(e.status, e.code, e.message) }
        exception<PayloadTooLargeException> { call, _ ->
            call.respondError(HttpStatusCode.PayloadTooLarge, "too_large", "That upload is too large.")
        }
        exception<BadRequestException> { call, _ -> call.respondError(HttpStatusCode.BadRequest, "validation", INVALID_REQUEST) }
        exception<ContentTransformationException> { call, _ ->
            call.respondError(HttpStatusCode.BadRequest, "validation", INVALID_REQUEST)
        }
        exception<NotFoundException> { call, _ -> call.respondError(HttpStatusCode.NotFound, "not_found", NOT_FOUND) }
        exception<Throwable> { call, e ->
            if (e is CancellationException) throw e
            log.error("Unhandled error on {} {}", call.request.local.method.value, call.request.local.uri, e)
            call.respondError(HttpStatusCode.InternalServerError, "internal", "Something went wrong. Please try again.")
        }
        // Only bare statuses produced by routing, not responses that already carry a body.
        status(HttpStatusCode.NotFound, HttpStatusCode.MethodNotAllowed) { status: HttpStatusCode ->
            if (content.isEmpty()) call.respondError(status, "not_found", NOT_FOUND)
        }
    }
}

private suspend fun ApplicationCall.respondError(status: HttpStatusCode, code: String, message: String) {
    respond(status, ErrorDto(code, message))
}

private fun OutgoingContent.isEmpty(): Boolean = this is OutgoingContent.NoContent || contentLength == 0L

private const val INVALID_REQUEST = "The request is not valid."
private const val NOT_FOUND = "Not found."
