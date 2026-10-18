package com.ndizigiye.loop.data.remote

import com.ndizigiye.loop.data.session.SessionStore
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Adds `Authorization: Bearer <token>` to requests for the Loop server only (the
 * placeholder host or the configured server's host and port), and calls
 * [onUnauthorized] when the server rejects the token with a 401 on an `/api/`
 * endpoint other than `/api/auth/` (where a 401 just means wrong credentials).
 *
 * Install it before [BaseUrlInterceptor] so it sees the placeholder URL.
 */
class AuthInterceptor(
    private val sessionStore: SessionStore,
    private val onUnauthorized: () -> Unit,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val path = serverPath(request.url) ?: return chain.proceed(request)
        val token = sessionStore.token
        val authorized = if (token != null && request.header(AUTHORIZATION) == null) {
            request.newBuilder().header(AUTHORIZATION, "Bearer $token").build()
        } else {
            request
        }
        val response = chain.proceed(authorized)
        val sessionScoped = path.startsWith("/api/") && !path.startsWith("/api/auth/")
        if (response.code == 401 && token != null && sessionScoped && sessionStore.token == token) {
            onUnauthorized()
        }
        return response
    }

    /** The request path relative to the Loop server, or null if [url] points elsewhere. */
    private fun serverPath(url: HttpUrl): String? {
        if (url.host == BaseUrlInterceptor.PLACEHOLDER_HOST) return url.encodedPath
        val base = sessionStore.baseUrl.value.toHttpUrlOrNull() ?: return null
        if (url.host != base.host || url.port != base.port) return null
        // Match whole segments: with the server under "/loop", "/loopy/..." is someone else.
        val prefix = base.encodedPath.trimEnd('/')
        return if (url.encodedPath.startsWith("$prefix/")) url.encodedPath.removePrefix(prefix) else null
    }

    private companion object {
        const val AUTHORIZATION = "Authorization"
    }
}
