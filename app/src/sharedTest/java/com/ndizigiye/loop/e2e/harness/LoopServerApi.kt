package com.ndizigiye.loop.e2e.harness

import com.ndizigiye.loop.api.AuthResponse
import com.ndizigiye.loop.api.CommentDto
import com.ndizigiye.loop.api.CountDto
import com.ndizigiye.loop.api.CreateCommentRequest
import com.ndizigiye.loop.api.NotificationDto
import com.ndizigiye.loop.api.PageDto
import com.ndizigiye.loop.api.RegisterRequest
import com.ndizigiye.loop.api.SearchResultDto
import com.ndizigiye.loop.api.TrendingDto
import com.ndizigiye.loop.api.UserDto
import com.ndizigiye.loop.api.VideoDto
import com.ndizigiye.loop.data.remote.LoopJson
import kotlinx.serialization.encodeToString
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

/** An account created for one test through the HTTP API. */
data class TestAccount(val token: String, val user: UserDto, val password: String) {
    val username: String get() = user.username
    val displayName: String get() = user.displayName
}

/** A response of the live server that is not a success. */
class ServerCallException(val status: Int, message: String) : RuntimeException(message)

/**
 * The live Loop server under test, called directly with OkHttp rather than through the app:
 * it creates the test accounts, acts as the second user whose likes, comments and follows
 * reach the app's inbox, and reads server-side state to verify what the UI did.
 */
class LoopServerApi(val baseUrl: HttpUrl) {

    private val client = OkHttpClient.Builder()
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    // ---- accounts

    /** Registers a new account with a unique username starting with "e2e_". */
    fun register(displayName: String = "E2E Tester", password: String = DEFAULT_PASSWORD): TestAccount {
        val request = RegisterRequest(uniqueUsername(), displayName, password)
        val auth: AuthResponse = call("POST", url("api/auth/register"), body = json(request))
        return TestAccount(auth.token, auth.user, password)
    }

    fun me(viewer: TestAccount): UserDto = call("GET", url("api/me"), viewer)

    fun user(username: String, viewer: TestAccount? = null): UserDto = call("GET", url("api/users/$username"), viewer)

    /** HTTP status of `GET /api/users/{username}`: 404 while no such account exists. */
    fun userStatus(username: String): Int = status(url("api/users/$username"))

    fun followers(username: String): List<UserDto> = page("api/users/$username/followers")

    fun following(username: String): List<UserDto> = page("api/users/$username/following")

    fun follow(viewer: TestAccount, username: String) {
        execute("PUT", url("api/users/$username/follow"), viewer)
    }

    // ---- videos

    fun video(id: Long, viewer: TestAccount? = null): VideoDto = call("GET", url("api/videos/$id"), viewer)

    /** HTTP status of `GET /api/videos/{id}`: 200 while it exists, 404 once deleted. */
    fun videoStatus(id: Long): Int = status(url("api/videos/$id"))

    fun userVideos(username: String): List<VideoDto> = page("api/users/$username/videos")

    fun likedVideos(username: String): List<VideoDto> = page("api/users/$username/liked")

    fun bookmarks(viewer: TestAccount): List<VideoDto> = page("api/me/bookmarks", viewer)

    fun followingFeed(viewer: TestAccount): List<VideoDto> = page("api/feed/following", viewer)

    fun hashtagVideos(name: String): List<VideoDto> = page("api/hashtags/$name/videos")

    /** Every video of one For You pass for [viewer]; the feed itself starts a new pass when it runs out. */
    fun forYouPass(viewer: TestAccount): List<VideoDto> {
        val seen = LinkedHashMap<Long, VideoDto>()
        var cursor: String? = null
        while (true) {
            val pageUrl = url("api/feed/for-you").newBuilder()
                .addQueryParameter("limit", "50")
                .apply { cursor?.let { addQueryParameter("cursor", it) } }
                .build()
            val page: PageDto<VideoDto> = call("GET", pageUrl, viewer)
            for (video in page.items) {
                if (seen.put(video.id, video) != null) return seen.values.toList()
            }
            cursor = page.nextCursor ?: return seen.values.toList()
        }
    }

    fun like(viewer: TestAccount, videoId: Long) {
        execute("PUT", url("api/videos/$videoId/like"), viewer)
    }

    fun notInterested(viewer: TestAccount, videoId: Long) {
        execute("POST", url("api/videos/$videoId/not-interested"), viewer)
    }

    fun upload(viewer: TestAccount, video: File, caption: String): VideoDto {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("video", video.name, video.asRequestBody("video/mp4".toMediaType()))
            .addFormDataPart("caption", caption)
            .build()
        return call("POST", url("api/videos"), viewer, body)
    }

    // ---- comments

    fun comments(videoId: Long): List<CommentDto> = page("api/videos/$videoId/comments")

    fun replies(commentId: Long, viewer: TestAccount? = null): List<CommentDto> =
        page("api/comments/$commentId/replies", viewer)

    fun comment(viewer: TestAccount, videoId: Long, text: String, parentId: Long? = null): CommentDto =
        call("POST", url("api/videos/$videoId/comments"), viewer, json(CreateCommentRequest(text, parentId)))

    fun likeComment(viewer: TestAccount, commentId: Long) {
        execute("PUT", url("api/comments/$commentId/like"), viewer)
    }

    // ---- discover

    fun discover(): TrendingDto = call("GET", url("api/discover"))

    fun search(query: String): SearchResultDto =
        call("GET", url("api/search").newBuilder().addQueryParameter("q", query).build())

    // ---- inbox

    fun notifications(viewer: TestAccount): List<NotificationDto> = page("api/notifications", viewer)

    fun unreadCount(viewer: TestAccount): Long =
        call<CountDto>("GET", url("api/notifications/unread-count"), viewer).count

    // ---- web

    /** GETs [path] (e.g. the share page "v/12") and returns its status and body. */
    fun webPage(path: String): Pair<Int, String> =
        client.newCall(Request.Builder().url(url(path)).build()).execute().use { response ->
            response.code to response.body!!.string()
        }

    // ---- plumbing

    fun url(path: String): HttpUrl = baseUrl.resolve(path) ?: error("Bad path $path")

    /** The first 50 items of a paginated list. */
    private inline fun <reified T> page(path: String, viewer: TestAccount? = null): List<T> =
        call<PageDto<T>>("GET", url(path).newBuilder().addQueryParameter("limit", "50").build(), viewer).items

    private inline fun <reified T> call(
        method: String,
        url: HttpUrl,
        viewer: TestAccount? = null,
        body: RequestBody? = null,
    ): T = LoopJson.decodeFromString(execute(method, url, viewer, body))

    private fun execute(method: String, url: HttpUrl, viewer: TestAccount? = null, body: RequestBody? = null): String {
        val payload = body ?: if (method == "GET" || method == "DELETE") null else ByteArray(0).toRequestBody()
        val request = Request.Builder()
            .url(url)
            .method(method, payload)
            .apply { if (viewer != null) header("Authorization", "Bearer ${viewer.token}") }
            .build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw ServerCallException(response.code, "$method $url: HTTP ${response.code} $text")
            return text
        }
    }

    private fun status(url: HttpUrl): Int = client.newCall(Request.Builder().url(url).build()).execute().use { it.code }

    private inline fun <reified T> json(value: T): RequestBody = LoopJson.encodeToString(value).toRequestBody(JSON)

    companion object {
        const val DEFAULT_PASSWORD = "e2e-secret"
        private val JSON = "application/json; charset=utf-8".toMediaType()

        /** "e2e_" and ten random characters: unique per run, within the server's username rules. */
        fun uniqueUsername(): String = "e2e_" + UUID.randomUUID().toString().replace("-", "").take(10)
    }
}
