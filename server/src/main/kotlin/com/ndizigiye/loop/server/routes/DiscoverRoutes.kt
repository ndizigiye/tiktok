package com.ndizigiye.loop.server.routes

import com.ndizigiye.loop.server.LoopServices
import com.ndizigiye.loop.server.paging.PageRequest
import com.ndizigiye.loop.server.plugins.JWT_AUTH
import com.ndizigiye.loop.server.plugins.viewerId
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/** Discover page, search, hashtag and sound pages. */
fun Route.discoverRoutes(services: LoopServices) {
    val discover = services.discover
    authenticate(JWT_AUTH, optional = true) {
        get("/api/discover") {
            call.respond(discover.trending(call.viewerId()))
        }
        get("/api/search") {
            val limit = PageRequest.of(cursor = null, limit = call.request.queryParameters["limit"], default = SEARCH_LIMIT).limit
            call.respond(discover.search(call.request.queryParameters["q"], limit, call.viewerId()))
        }
        get("/api/hashtags/{name}") {
            call.respond(discover.hashtag(call.textParam("name")))
        }
        get("/api/hashtags/{name}/videos") {
            call.respond(services.videos.hashtagVideos(call.textParam("name"), call.viewerId(), call.pageRequest()))
        }
        get("/api/sounds/{id}") {
            call.respond(discover.sound(call.idParam("id", SOUND_NOT_FOUND)))
        }
        get("/api/sounds/{id}/videos") {
            call.respond(services.videos.soundVideos(call.idParam("id", SOUND_NOT_FOUND), call.viewerId(), call.pageRequest()))
        }
    }
}

private const val SEARCH_LIMIT = 20
private const val SOUND_NOT_FOUND = "Sound not found."
