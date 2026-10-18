package com.ndizigiye.loop.e2e.harness

import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import java.io.Closeable
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlin.time.Duration

/**
 * A plain reverse proxy to [target] on a fixed local port, so a test can point the app at
 * [url] while "the server" is down (nothing listens on the port yet) and bring it back
 * with [start]. [responseDelay] holds back the answer to chosen requests, e.g. to keep a
 * request's progress on screen; [forwarded] tells which requests the app made.
 */
class ForwardingServer(
    private val target: HttpUrl,
    private val responseDelay: (RecordedRequest) -> Duration = { Duration.ZERO },
) : Closeable {

    /** A port nothing listens on until [start]. */
    val port: Int = ServerSocket(0).use { it.localPort }

    val url: String = "http://127.0.0.1:$port/"

    private val forwardedUrls = CopyOnWriteArrayList<HttpUrl>()

    /** The URLs of the requests forwarded so far, oldest first, as the app sent them. */
    val forwarded: List<HttpUrl> get() = forwardedUrls.toList()

    private val client = OkHttpClient()

    private val server = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = forward(request)
        }
    }

    fun start() {
        server.start(port)
    }

    override fun close() {
        server.shutdown()
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }

    private fun forward(request: RecordedRequest): MockResponse {
        val requestUrl = request.requestUrl ?: return MockResponse().setResponseCode(400)
        forwardedUrls += requestUrl
        val url = target.newBuilder()
            .encodedPath(requestUrl.encodedPath)
            .encodedQuery(requestUrl.encodedQuery)
            .build()
        val method = request.method ?: "GET"
        val body = if (method == "GET" || method == "HEAD" || method == "DELETE") {
            null
        } else {
            request.body.readByteArray().toRequestBody(request.getHeader("Content-Type")?.toMediaTypeOrNull())
        }
        val forwarded = Request.Builder().url(url).method(method, body).apply {
            for ((name, value) in request.headers) {
                if (name.lowercase() !in HOP_BY_HOP_HEADERS) header(name, value)
            }
        }.build()
        client.newCall(forwarded).execute().use { response ->
            val answer = MockResponse().setResponseCode(response.code)
            for ((name, value) in response.headers) {
                if (name.lowercase() !in HOP_BY_HOP_HEADERS) answer.addHeader(name, value)
            }
            return answer
                .setBody(Buffer().write(response.body?.bytes() ?: ByteArray(0)))
                .setHeadersDelay(responseDelay(request).inWholeMilliseconds, TimeUnit.MILLISECONDS)
        }
    }

    private companion object {
        val HOP_BY_HOP_HEADERS = setOf("host", "connection", "content-length", "transfer-encoding", "accept-encoding")
    }
}
