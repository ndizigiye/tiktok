package com.ndizigiye.loop.server.routes

import com.ndizigiye.loop.server.ApiException
import com.ndizigiye.loop.server.media.LocalMediaStore
import com.ndizigiye.loop.server.media.MediaKind
import io.ktor.http.HttpHeaders
import io.ktor.server.response.header
import io.ktor.server.response.respondFile
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/**
 * `/media/{videos|thumbs|avatars}/{file}` for [LocalMediaStore] (with Supabase Storage, clients
 * load media from the bucket instead). Range requests (206) come from the PartialContent plugin,
 * which understands file responses.
 */
fun Route.mediaRoutes(store: LocalMediaStore) {
    get("/media/{kind}/{file}") {
        val kind = MediaKind.entries.firstOrNull { it.dir == call.parameters["kind"] }
        val file = kind?.let { store.existing(it, call.textParam("file")) }
            ?: throw ApiException.notFound("File not found.")
        // Stored names are random or fixed per demo clip and never reused for other content.
        call.response.header(HttpHeaders.CacheControl, "public, max-age=604800")
        call.respondFile(file)
    }
}
