package com.ndizigiye.loop.server.routes

import com.ndizigiye.loop.server.ApiException
import com.ndizigiye.loop.server.paging.PageRequest
import io.ktor.server.application.ApplicationCall

/** A numeric path parameter; anything else means the resource does not exist. */
internal fun ApplicationCall.idParam(name: String, notFoundMessage: String): Long =
    parameters[name]?.toLongOrNull() ?: throw ApiException.notFound(notFoundMessage)

internal fun ApplicationCall.textParam(name: String): String =
    parameters[name] ?: throw ApiException.notFound("Not found.")

internal fun ApplicationCall.pageRequest(): PageRequest =
    PageRequest.of(request.queryParameters["cursor"], request.queryParameters["limit"])

internal const val VIDEO_NOT_FOUND = "Video not found."
internal const val COMMENT_NOT_FOUND = "Comment not found."
