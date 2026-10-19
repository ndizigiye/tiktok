package com.ndizigiye.loop.server.routes

import com.ndizigiye.loop.server.LoopServices
import com.ndizigiye.loop.server.web.SharePage
import io.ktor.http.ContentType
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/** Health check and the public web page behind share links. */
fun Route.webRoutes(services: LoopServices) {
    get("/health") {
        call.respondText("ok", ContentType.Text.Plain)
    }
    get("/v/{id}") {
        val video = services.videos.get(call.idParam("id", VIDEO_NOT_FOUND), viewerId = null)
        call.respondText(SharePage.render(video), ContentType.Text.Html.withParameter("charset", "utf-8"))
    }
}
