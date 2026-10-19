package com.ndizigiye.loop.server.plugins

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.autohead.AutoHeadResponse
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import io.ktor.server.plugins.partialcontent.PartialContent
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import org.slf4j.event.Level

fun Application.configureHttp() {
    install(DefaultHeaders) {
        header(HttpHeaders.Server, "Loop")
        header("X-Content-Type-Options", "nosniff")
    }
    install(CallLogging) {
        level = Level.INFO
        // Players fetch media in many small range requests; keep the log readable.
        filter { !it.request.path().startsWith("/media/") }
        format { call -> "${call.response.status()?.value} ${call.request.httpMethod.value} ${call.request.path()}" }
    }
    install(CORS) {
        anyHost()
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Head)
        allowMethod(HttpMethod.Options)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Range)
        exposeHeader(HttpHeaders.ContentRange)
        exposeHeader(HttpHeaders.AcceptRanges)
        exposeHeader(HttpHeaders.ContentLength)
    }
    install(PartialContent)
    install(AutoHeadResponse)
}
