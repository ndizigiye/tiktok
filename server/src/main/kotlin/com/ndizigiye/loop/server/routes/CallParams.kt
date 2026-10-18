package com.ndizigiye.loop.server.routes

import com.ndizigiye.loop.server.ApiException
import com.ndizigiye.loop.server.paging.PageRequest
import com.ndizigiye.loop.server.service.TextRules
import io.ktor.server.application.ApplicationCall

/** A numeric path parameter; anything else means the resource does not exist. */
internal fun ApplicationCall.idParam(name: String, notFoundMessage: String): Long =
    parameters[name]?.toLongOrNull() ?: throw ApiException.notFound(notFoundMessage)

/** A name-like path parameter; one that no database can store (U+0000) cannot exist either. */
internal fun ApplicationCall.textParam(name: String): String =
    parameters[name]?.takeIf(TextRules::canStore) ?: throw ApiException.notFound("Not found.")

/** An optional numeric query parameter such as `?upTo=<id>`: null when absent or empty, `400` when not a number. */
internal fun ApplicationCall.idQuery(name: String): Long? =
    request.queryParameters[name]?.takeIf { it.isNotEmpty() }?.let {
        it.toLongOrNull() ?: throw ApiException.validation("$name must be an id.")
    }

internal fun ApplicationCall.pageRequest(): PageRequest =
    PageRequest.of(request.queryParameters["cursor"], request.queryParameters["limit"])

internal const val VIDEO_NOT_FOUND = "Video not found."
internal const val COMMENT_NOT_FOUND = "Comment not found."
