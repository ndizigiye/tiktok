package com.ndizigiye.loop.data.remote

import com.ndizigiye.loop.data.session.SessionStore
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * Retrofit is built once with [PLACEHOLDER_BASE_URL]; this interceptor sends those
 * requests to the server URL currently configured in [SessionStore] (scheme, host,
 * port and path prefix), so changing the server in Settings needs no rebuild.
 * Requests to any other host (Coil, Media3, absolute "test connection" calls) pass
 * through untouched.
 */
class BaseUrlInterceptor(private val sessionStore: SessionStore) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.host != PLACEHOLDER_HOST) return chain.proceed(request)

        val baseUrl = sessionStore.baseUrl.value
        val base = baseUrl.toHttpUrlOrNull() ?: throw IOException("Invalid server URL: $baseUrl")
        val url = request.url.newBuilder()
            .scheme(base.scheme)
            .host(base.host)
            .port(base.port)
            .encodedPath(base.encodedPath.trimEnd('/') + request.url.encodedPath)
            .build()
        return chain.proceed(request.newBuilder().url(url).build())
    }

    companion object {
        const val PLACEHOLDER_HOST = "loop.invalid"
        const val PLACEHOLDER_BASE_URL = "http://$PLACEHOLDER_HOST/"
    }
}
