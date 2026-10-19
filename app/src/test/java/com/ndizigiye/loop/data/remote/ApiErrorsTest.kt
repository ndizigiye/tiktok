package com.ndizigiye.loop.data.remote

import com.ndizigiye.loop.testutil.TestBackend
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.SocketPolicy
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame

class ApiErrorsTest {

    private fun httpError(status: Int, body: String, contentType: String = "application/json"): HttpException =
        HttpException(Response.error<Unit>(status, body.toResponseBody(contentType.toMediaType())))

    private suspend fun failureOf(block: suspend () -> Unit): Throwable =
        apiCall(block).exceptionOrNull() ?: error("apiCall succeeded")

    private fun assertApiException(status: Int, code: String, message: String, actual: Throwable) {
        assertIs<ApiException>(actual)
        assertEquals(Triple(status, code, message), Triple(actual.status, actual.code, actual.message))
        assertEquals(message, actual.userMessage())
    }

    @Test
    fun `success is wrapped as is`() = runTest {
        assertEquals(Result.success(42), apiCall { 42 })
    }

    @Test
    fun `an ErrorDto body keeps the status, code and message`() = runTest {
        val error = failureOf { throw httpError(404, """{"code":"not_found","message":"That video was deleted."}""") }

        assertApiException(404, "not_found", "That video was deleted.", error)
    }

    @Test
    fun `a blank server message falls back to one for the status but keeps the code`() = runTest {
        val error = failureOf { throw httpError(413, """{"code":"too_large","message":"  "}""") }

        assertApiException(413, "too_large", "That file is too large.", error)
    }

    @Test
    fun `a body that is not an ErrorDto gives code http and a message for the status`() = runTest {
        val expected = mapOf(
            401 to "Please log in to continue.",
            403 to "You're not allowed to do that.",
            404 to "Not found.",
            413 to "That file is too large.",
            500 to "The server had a problem. Please try again.",
            503 to "The server had a problem. Please try again.",
            429 to "Request failed (HTTP 429).",
        )
        for ((status, message) in expected) {
            val error = failureOf { throw httpError(status, "<html>Bad gateway</html>", "text/html") }
            assertApiException(status, "http", message, error)
        }
        assertApiException(400, "http", "Request failed (HTTP 400).", failureOf { throw httpError(400, "") })
    }

    @Test
    fun `network failures map to the network code`() = runTest {
        assertApiException(0, "network", NETWORK_ERROR_MESSAGE, failureOf { throw IOException("reset") })
    }

    @Test
    fun `undecodable responses map to the parse code`() = runTest {
        val error = failureOf { throw SerializationException("Unexpected JSON token") }

        assertApiException(0, "parse", "The server sent an unexpected response.", error)
    }

    @Test
    fun `cancellation is rethrown, not wrapped`() = runTest {
        assertFailsWith<CancellationException> { apiCall { throw CancellationException("screen closed") } }
    }

    @Test
    fun `other exceptions pass through and get a generic message`() = runTest {
        val bug = IllegalStateException("bug")

        val error = failureOf { throw bug }

        assertSame(bug, error)
        assertEquals("Something went wrong. Please try again.", error.userMessage())
        assertEquals(NETWORK_ERROR_MESSAGE, IOException("raw").userMessage())
    }

    @Test
    fun `failures from the real HTTP stack are mapped`() = runTest {
        TestBackend().use { backend ->
            val repository = backend.container.discoverRepository
            backend.enqueueError(404, "not_found", "No videos use #nothing yet.")
            backend.server.enqueue(MockResponse().setBody("""{"name": 12"""))
            backend.server.enqueue(MockResponse().setResponseCode(502).setBody("Bad gateway"))
            // Twice: OkHttp may retry a request whose pooled connection was dropped.
            backend.server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
            backend.server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))

            val notFound = repository.hashtag("nothing").exceptionOrNull()!!
            val malformed = repository.hashtag("x").exceptionOrNull()!!
            val badGateway = repository.sound(1).exceptionOrNull()!!
            val disconnected = repository.sound(1).exceptionOrNull()!!

            assertApiException(404, "not_found", "No videos use #nothing yet.", notFound)
            assertApiException(0, "parse", "The server sent an unexpected response.", malformed)
            assertApiException(502, "http", "The server had a problem. Please try again.", badGateway)
            assertApiException(0, "network", NETWORK_ERROR_MESSAGE, disconnected)
        }
    }
}
