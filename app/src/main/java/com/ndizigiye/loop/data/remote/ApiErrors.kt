package com.ndizigiye.loop.data.remote

import com.ndizigiye.loop.api.ErrorDto
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException

/**
 * A failed API call. [status] is the HTTP status (0 when the server was not reached
 * or did not send a usable response), [code] the server's `ErrorDto.code` or a
 * client-side code (`network`, `parse`, `http`), and [message] is safe to show.
 */
class ApiException(
    val status: Int,
    val code: String,
    override val message: String,
) : Exception(message)

const val NETWORK_ERROR_MESSAGE = "Can't reach the server. Check your connection and server URL."
private const val PARSE_ERROR_MESSAGE = "The server sent an unexpected response."
private const val GENERIC_ERROR_MESSAGE = "Something went wrong. Please try again."

/**
 * Runs a Retrofit call and converts failures into [ApiException]s.
 * [CancellationException] is rethrown so structured concurrency keeps working.
 */
suspend fun <T> apiCall(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: HttpException) {
        Result.failure(e.toApiException())
    } catch (e: IOException) {
        Result.failure(ApiException(status = 0, code = "network", message = NETWORK_ERROR_MESSAGE))
    } catch (e: SerializationException) {
        Result.failure(ApiException(status = 0, code = "parse", message = PARSE_ERROR_MESSAGE))
    } catch (e: Exception) {
        Result.failure(e)
    }

/** Text for showing [this] to the user. */
fun Throwable.userMessage(): String = when (this) {
    is ApiException -> message
    is IOException -> NETWORK_ERROR_MESSAGE
    else -> GENERIC_ERROR_MESSAGE
}

private fun HttpException.toApiException(): ApiException {
    val status = code()
    val error = try {
        response()?.errorBody()?.string()?.let { LoopJson.decodeFromString(ErrorDto.serializer(), it) }
    } catch (e: SerializationException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    } catch (e: IOException) {
        null
    }
    return if (error != null && error.message.isNotBlank()) {
        ApiException(status = status, code = error.code, message = error.message)
    } else {
        ApiException(status = status, code = error?.code ?: "http", message = fallbackMessage(status))
    }
}

private fun fallbackMessage(status: Int): String = when (status) {
    401 -> "Please log in to continue."
    403 -> "You're not allowed to do that."
    404 -> "Not found."
    413 -> "That file is too large."
    in 500..599 -> "The server had a problem. Please try again."
    else -> "Request failed (HTTP $status)."
}
