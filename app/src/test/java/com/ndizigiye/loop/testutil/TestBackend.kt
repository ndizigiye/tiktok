package com.ndizigiye.loop.testutil

import com.ndizigiye.loop.api.ErrorDto
import com.ndizigiye.loop.api.UserDto
import com.ndizigiye.loop.di.AppContainer
import kotlinx.coroutines.cancel
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.QueueDispatcher
import okhttp3.mockwebserver.RecordedRequest
import java.io.Closeable
import java.util.concurrent.TimeUnit

/**
 * The app's real dependency graph ([AppContainer]) pointed at a [MockWebServer] that
 * plays the Loop server under the path prefix [PATH_PREFIX], as behind a reverse proxy.
 * Requests without a queued response fail fast with a 404 instead of hanging.
 */
class TestBackend : Closeable {

    val server = MockWebServer().apply {
        dispatcher = QueueDispatcher().apply { setFailFast(true) }
        start()
    }
    val container = AppContainer(FakeContext())
    val sessionStore get() = container.sessionStore
    val interactions get() = container.interactions

    init {
        sessionStore.setBaseUrl(server.url(PATH_PREFIX).toString())
    }

    fun signIn(user: UserDto = ME, token: String = TOKEN) = sessionStore.save(token, user)

    inline fun <reified T> enqueue(value: T, code: Int = 200) {
        server.enqueue(jsonResponse(value, code))
    }

    fun enqueueError(code: Int, errorCode: String, message: String) {
        server.enqueue(errorResponse(code, errorCode, message))
    }

    fun enqueueNoContent() {
        server.enqueue(MockResponse().setResponseCode(204))
    }

    /** Answers every request with [respond], which runs on the server thread as the request arrives. */
    fun respondWith(respond: (RecordedRequest) -> MockResponse) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = respond(request)
        }
    }

    fun takeRequest(): RecordedRequest =
        server.takeRequest(5, TimeUnit.SECONDS) ?: error("No request reached the server")

    override fun close() {
        server.shutdown()
        container.applicationScope.cancel()
        container.okHttpClient.dispatcher.executorService.shutdown()
        container.okHttpClient.connectionPool.evictAll()
    }

    companion object {
        const val PATH_PREFIX = "/loop/"
        const val TOKEN = "token-of-me"
        val ME = user(id = 1, username = "me", isMe = true, followingCount = 3)
    }
}

/** Encodes like the server does (`encodeDefaults = true`). */
val ServerJson = Json { encodeDefaults = true }

inline fun <reified T> jsonResponse(value: T, code: Int = 200): MockResponse =
    MockResponse()
        .setResponseCode(code)
        .setHeader("Content-Type", "application/json; charset=utf-8")
        .setBody(ServerJson.encodeToString(value))

fun errorResponse(code: Int, errorCode: String, message: String): MockResponse =
    jsonResponse(ErrorDto(errorCode, message), code)
