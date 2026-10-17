package com.ndizigiye.loop.server.routes

import com.ndizigiye.loop.server.ApiException
import com.ndizigiye.loop.server.media.MediaKind
import com.ndizigiye.loop.server.media.MediaStorage
import io.ktor.http.HttpHeaders
import io.ktor.server.response.header
import io.ktor.server.response.respondFile
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/**
 * `/media/{videos|thumbs|avatars}/{file}`. Range requests (206) come from the PartialContent
 * plugin, which understands file responses.
 */
fun Route.mediaRoutes(storage: MediaStorage) {
    get("/media/{kind}/{file}") {
        val kind = MediaKind.entries.firstOrNull { it.dir == call.parameters["kind"] }
        val file = kind?.let { storage.existing(it, call.textParam("file")) }
            ?: throw ApiException.notFound("File not found.")
        // Stored names are random or fixed per demo clip and never reused for other content.
        call.response.header(HttpHeaders.CacheControl, "public, max-age=604800")
        call.respondFile(file)
    }
}
