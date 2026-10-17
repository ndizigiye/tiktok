package com.ndizigiye.loop.server

import com.ndizigiye.loop.server.plugins.configureErrorHandling
import com.ndizigiye.loop.server.plugins.configureHttp
import com.ndizigiye.loop.server.plugins.configureSecurity
import com.ndizigiye.loop.server.plugins.configureSerialization
import com.ndizigiye.loop.server.routes.accountRoutes
import com.ndizigiye.loop.server.routes.discoverRoutes
import com.ndizigiye.loop.server.routes.mediaRoutes
import com.ndizigiye.loop.server.routes.socialRoutes
import com.ndizigiye.loop.server.routes.videoRoutes
import com.ndizigiye.loop.server.routes.webRoutes
import com.ndizigiye.loop.server.seed.DemoSeeder
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.log
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

fun main() {
    val config = ServerConfig.fromEnv()
    embeddedServer(Netty, port = config.port, host = "0.0.0.0") { module(config) }.start(wait = true)
}

fun Application.module(config: ServerConfig) {
    if (config.usesDevJwtSecret) {
        log.warn("LOOP_JWT_SECRET is not set: using a development-only signing secret. Set it before exposing this server.")
    }
    val services = runBlocking { LoopServices.open(config) }
    monitor.subscribe(ApplicationStopped) { services.close() }
    log.info("Loop data directory: {}", config.dataDir.absoluteFile)

    configureHttp()
    configureSerialization()
    configureErrorHandling()
    configureSecurity(services.tokens, services.users::exists)
    routing {
        webRoutes(services)
        mediaRoutes(services.storage)
        accountRoutes(services)
        videoRoutes(services)
        socialRoutes(services)
        discoverRoutes(services)
    }

    if (config.seed) {
        // Runs in the background so the server answers requests while demo clips render.
        launch(Dispatchers.IO) { DemoSeeder(services).seedIfNeeded() }
    }
}
