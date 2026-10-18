package com.ndizigiye.loop.data.remote

import com.ndizigiye.loop.data.session.SessionStore
import com.ndizigiye.loop.testutil.FakeContext
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BaseUrlInterceptorTest {

    private val sessionStore = SessionStore(FakeContext())
    private val sent = mutableListOf<Request>()

    /** Records the request as it leaves [BaseUrlInterceptor] and answers it without any network. */
    private val client = OkHttpClient.Builder()
        .addInterceptor(BaseUrlInterceptor(sessionStore))
        .addInterceptor { chain ->
            sent += chain.request()
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("".toResponseBody())
                .build()
        }
        .build()

    private fun send(request: Request): Request {
        client.newCall(request).execute().close()
        return sent.last()
    }

    private fun send(url: String): String = send(Request.Builder().url(url).build()).url.toString()

    @Test
    fun `placeholder requests go to the configured server under its path prefix`() {
        sessionStore.setBaseUrl("https://media.example.org:8443/loop/")

        assertEquals(
            "https://media.example.org:8443/loop/api/search?q=sun%20set&limit=5",
            send("${BaseUrlInterceptor.PLACEHOLDER_BASE_URL}api/search?q=sun%20set&limit=5"),
        )
    }

    @Test
    fun `a server at the root keeps the path as is`() {
        sessionStore.setBaseUrl("192.168.1.20:8080")

        assertEquals("http://192.168.1.20:8080/api/feed/for-you", send("http://loop.invalid/api/feed/for-you"))
    }

    @Test
    fun `encoded path segments stay encoded`() {
        sessionStore.setBaseUrl("http://example.org/loop")

        assertEquals(
            "http://example.org/loop/api/hashtags/caf%C3%A9/videos",
            send("http://loop.invalid/api/hashtags/caf%C3%A9/videos"),
        )
    }

    @Test
    fun `requests to other hosts pass through untouched`() {
        sessionStore.setBaseUrl("http://example.org:9000/loop/")

        assertEquals("https://cdn.example.net/a.mp4", send("https://cdn.example.net/a.mp4"))
        assertEquals("http://example.org:9000/loop/media/a.mp4", send("http://example.org:9000/loop/media/a.mp4"))
    }

    @Test
    fun `a new server URL applies from the next request on`() {
        sessionStore.setBaseUrl("http://10.0.2.2:8080/")
        val first = send("http://loop.invalid/api/me")
        sessionStore.setBaseUrl("http://192.168.1.20:9090/")
        val second = send("http://loop.invalid/api/me")

        assertEquals("http://10.0.2.2:8080/api/me", first)
        assertEquals("http://192.168.1.20:9090/api/me", second)
    }

    @Test
    fun `method, headers and body are kept`() {
        sessionStore.setBaseUrl("http://example.org/")
        val request = Request.Builder()
            .url("http://loop.invalid/api/videos/7/comments")
            .header("X-Trace", "abc")
            .post("""{"text":"hi"}""".toRequestBody())
            .build()

        val rewritten = send(request)

        assertEquals("POST", rewritten.method)
        assertEquals("abc", rewritten.header("X-Trace"))
        assertEquals("""{"text":"hi"}""", Buffer().also { rewritten.body!!.writeTo(it) }.readUtf8())
    }

    @Test
    fun `an unusable server URL fails the request with an IOException`() {
        sessionStore.setBaseUrl("http://bad host/")

        val error = assertFailsWith<IOException> { send("http://loop.invalid/api/me") }

        assertTrue(error.message!!.startsWith("Invalid server URL"), error.message)
        assertTrue(sent.isEmpty())
    }
}
