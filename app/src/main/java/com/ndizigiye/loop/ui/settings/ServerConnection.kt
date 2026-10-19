package com.ndizigiye.loop.ui.settings

import com.ndizigiye.loop.data.remote.NETWORK_ERROR_MESSAGE
import com.ndizigiye.loop.data.session.SessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

private const val HEALTH_TIMEOUT_SECONDS = 10L

/** Result of the "Test connection" action. */
sealed interface ConnectionCheck {
    data object Idle : ConnectionCheck
    data object Running : ConnectionCheck
    data object Connected : ConnectionCheck
    data class Failed(val message: String) : ConnectionCheck
}

/**
 * Parses what the user typed as the server's root URL ("http://" is assumed when no
 * scheme is given); null unless it is an http(s) URL without query or fragment.
 */
internal fun parseServerUrl(input: String): HttpUrl? {
    if (input.isBlank()) return null
    val url = SessionStore.normalizeBaseUrl(input).toHttpUrlOrNull() ?: return null
    return url.takeIf { it.query == null && it.fragment == null }
}

/** GETs `<baseUrl>health` with a plain OkHttp request (not through LoopApi) on the IO dispatcher. */
internal suspend fun checkServerHealth(client: OkHttpClient, baseUrl: HttpUrl): ConnectionCheck =
    withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(baseUrl.newBuilder().addPathSegment("health").build())
            .get()
            .build()
        val healthClient = client.newBuilder()
            .callTimeout(HEALTH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
        try {
            healthClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    ConnectionCheck.Connected
                } else {
                    ConnectionCheck.Failed("The server answered HTTP ${response.code}. Is this a Loop server?")
                }
            }
        } catch (e: IOException) {
            ConnectionCheck.Failed(e.describe(baseUrl))
        }
    }

private fun IOException.describe(url: HttpUrl): String = when (this) {
    is UnknownHostException -> "Unknown host \"${url.host}\". Check the address."
    is ConnectException -> "Couldn't connect to ${url.host}:${url.port}. Is the server running?"
    is InterruptedIOException -> "The server didn't answer in time."
    else -> message?.takeIf { it.isNotBlank() } ?: NETWORK_ERROR_MESSAGE
}
