package com.ndizigiye.loop.server.media

import com.ndizigiye.loop.api.ApiLimits
import com.ndizigiye.loop.server.ApiException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.add
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.net.ProxySelector
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

/** Supabase Storage failed or answered unexpectedly; the message names the status, never the key. */
class MediaStoreException(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * Media in a public Supabase Storage bucket, through the Storage REST API with the service key:
 *
 * - upload: `POST /storage/v1/object/<bucket>/<path>`, the file streamed from disk as the body, with
 *   its `Content-Type`, `Cache-Control` and `x-upsert`;
 * - delete: `DELETE /storage/v1/object/<bucket>` with `{"prefixes": [paths]}` (what the SDKs' `remove` sends);
 * - startup check: `GET /storage/v1/bucket/<bucket>`;
 * - clients read `<url>/storage/v1/object/public/<bucket>/<path>`, which needs no key.
 *
 * Every request carries `Authorization: Bearer <key>` and `apikey: <key>`. Network errors, 429 and
 * 5xx answers are retried after [retryDelays]. The JDK client uses the JVM's proxy settings.
 */
class SupabaseMediaStore(
    private val settings: SupabaseStorageSettings,
    private val retryDelays: List<Duration> = DEFAULT_RETRY_DELAYS,
    private val client: HttpClient = sharedClient,
) : MediaStore {
    private val log = LoggerFactory.getLogger(SupabaseMediaStore::class.java)
    private val storageUrl = "${settings.url}/storage/v1"

    /** The bucket's own file size limit, read by [verify], so larger uploads fail before sending anything. */
    @Volatile
    private var bucketSizeLimit: Long? = null

    override val description: String = "Supabase Storage ${settings.url}, bucket ${settings.bucket}"

    override fun url(obj: MediaObject): String = "$storageUrl/object/public/${settings.bucket}/${obj.path}"

    /** Checks the key and that the bucket exists and is public; warns about limits that refuse some uploads. */
    override suspend fun verify() {
        val response = try {
            send { request("bucket/${settings.bucket}", REQUEST_TIMEOUT).GET() }
        } catch (e: IOException) {
            val reason = (e.cause ?: e).describe()
            error("Supabase Storage at ${settings.url} is not reachable ($reason). Check LOOP_SUPABASE_URL and the network.")
        }
        when (response.status) {
            in 200..299 -> Unit
            401, 403 -> error(
                "Supabase Storage rejected LOOP_SUPABASE_SERVICE_KEY (${response.summary()}). " +
                    "Use the project's secret key (sb_secret_...) or its legacy service_role key.",
            )
            400, 404 -> error(
                "Supabase Storage has no bucket \"${settings.bucket}\" (${response.summary()}). Create it as a public " +
                    "bucket (server/README.md) or set LOOP_SUPABASE_BUCKET.",
            )
            else -> error("Supabase Storage could not be checked: ${response.summary()}.")
        }
        val bucket = response.json ?: error("Supabase Storage answered the bucket check with something other than JSON.")
        check(bucket.primitive("public")?.booleanOrNull == true) {
            "The Supabase Storage bucket \"${settings.bucket}\" is not public, but clients load media from its public " +
                "URLs. Make it public (dashboard: Storage, bucket settings)."
        }
        bucketSizeLimit = bucket.primitive("file_size_limit")?.longOrNull?.takeIf { it > 0 }
        bucketSizeLimit?.takeIf { it < ApiLimits.MAX_UPLOAD_BYTES }?.let { limit ->
            log.warn(
                "The bucket \"{}\" accepts files up to {} bytes, less than the upload limit of {} bytes: larger uploads fail with too_large.",
                settings.bucket, limit, ApiLimits.MAX_UPLOAD_BYTES,
            )
        }
        val allowedTypes = (bucket["allowed_mime_types"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        if (!allowedTypes.isNullOrEmpty()) {
            val refused = MediaObject.STORED_CONTENT_TYPES.filter { type -> allowedTypes.none { allows(it, type) } }
            if (refused.isNotEmpty()) {
                log.warn("The bucket \"{}\" refuses {}; such uploads fail. Allow them in the bucket settings.", settings.bucket, refused)
            }
        }
    }

    override suspend fun put(obj: MediaObject, source: File, replace: Boolean) {
        require(obj.isSafe) { "Unsafe media name: ${obj.path}" }
        val size = withContext(Dispatchers.IO) { source.length() }
        bucketSizeLimit?.let { limit -> if (size > limit) throw ApiException.tooLarge(TOO_LARGE_MESSAGE) }
        val response = send {
            request("object/${settings.bucket}/${obj.path}", uploadTimeout(size))
                .header("Content-Type", obj.contentType)
                .header("Cache-Control", CACHE_CONTROL)
                .header("x-upsert", replace.toString())
                // Streamed from disk with a Content-Length; a 50 MB video is never held in memory.
                .POST(HttpRequest.BodyPublishers.ofFile(source.toPath()))
        }
        when {
            response.isSuccess -> Unit
            response.status == 413 -> throw ApiException.tooLarge(TOO_LARGE_MESSAGE)
            // An earlier attempt stored the object before failing with a 5xx; the name is unique, so it is ours.
            response.status == 409 && response.attempts > 1 ->
                log.info("{} was stored by an earlier attempt that reported an error", obj.path)
            else -> throw MediaStoreException("Supabase Storage could not store ${obj.path}: ${response.summary()}")
        }
    }

    override suspend fun delete(objects: Collection<MediaObject>) {
        val paths = objects.filter { it.isSafe }.map { it.path }.distinct()
        for (batch in paths.chunked(MAX_DELETE_BATCH)) {
            val body = buildJsonObject { putJsonArray("prefixes") { batch.forEach { add(it) } } }.toString()
            val response = send {
                request("object/${settings.bucket}", REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .method("DELETE", HttpRequest.BodyPublishers.ofString(body))
            }
            if (!response.isSuccess) {
                throw MediaStoreException("Supabase Storage could not delete ${batch.joinToString()}: ${response.summary()}")
            }
        }
    }

    private fun request(path: String, timeout: Duration): HttpRequest.Builder =
        HttpRequest.newBuilder(URI.create("$storageUrl/$path"))
            .timeout(timeout.toJavaDuration())
            .header("Authorization", "Bearer ${settings.serviceKey}")
            .header("apikey", settings.serviceKey)

    /**
     * Sends a request built by [build] (anew for each attempt), retrying network errors and transient
     * answers; returns the last answer, or throws [MediaStoreException] when the last attempt failed.
     */
    private suspend fun send(build: () -> HttpRequest.Builder): StorageResponse {
        var attempt = 1
        while (true) {
            val request = build().build()
            val (response, failure) = try {
                val answer = client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).await()
                StorageResponse(answer.statusCode(), answer.body().orEmpty().replace(settings.serviceKey, "<redacted>"), attempt) to null
            } catch (e: IOException) {
                null to e
            }
            val last = attempt > retryDelays.size
            if (response != null && (!response.isTransient || last)) return response
            if (failure != null && last) {
                throw MediaStoreException("Supabase Storage ${request.method()} ${request.uri().path} failed: ${failure.describe()}", failure)
            }
            val wait = retryDelays[attempt - 1]
            log.warn(
                "Supabase Storage {} {} failed ({}); attempt {} of {} in {}",
                request.method(), request.uri().path, response?.summary() ?: failure?.describe(), attempt + 1, retryDelays.size + 1, wait,
            )
            delay(wait)
            attempt++
        }
    }

    private fun Throwable.describe(): String = listOfNotNull(javaClass.simpleName, message?.take(200)).joinToString(": ")

    private fun JsonObject.primitive(key: String): JsonPrimitive? = this[key] as? JsonPrimitive

    private fun allows(pattern: String, type: String): Boolean =
        pattern == type || (pattern.endsWith("/*") && type.startsWith(pattern.dropLast(1)))

    companion object {
        /** Three attempts in all. */
        val DEFAULT_RETRY_DELAYS = listOf(500.milliseconds, 2.seconds)
        const val TOO_LARGE_MESSAGE = "That file is too large to store."

        /** Names are never reused for other content (local media is served the same way). */
        private const val CACHE_CONTROL = "max-age=604800"
        private const val MAX_DELETE_BATCH = 1_000
        private val CONNECT_TIMEOUT = 10.seconds
        private val REQUEST_TIMEOUT = 30.seconds

        /** A minute plus the time the file takes at 256 KiB/s. */
        private fun uploadTimeout(bytes: Long): Duration = 60.seconds + (bytes / (256 * 1024)).seconds

        /**
         * HTTP/1.1, so every upload streams over its own connection. Without an explicit selector the
         * client would also use the default one; it applies `https.proxyHost`, `http.nonProxyHosts`, ...
         */
        private val sharedClient: HttpClient by lazy {
            HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(CONNECT_TIMEOUT.toJavaDuration())
                .followRedirects(HttpClient.Redirect.NEVER)
                .apply { ProxySelector.getDefault()?.let { proxy(it) } }
                .build()
        }
    }
}

/**
 * A Storage API answer. Storage reports most errors as HTTP 400 with the real status in the body
 * (`{"statusCode": "413", "error": "Payload too large", "message": "..."}`); newer versions send the
 * real HTTP status with `{"code": "EntityTooLarge", "message": "..."}`. [status] is the real one either way.
 */
internal class StorageResponse(val httpStatus: Int, val body: String, val attempts: Int) {
    val json: JsonObject? = runCatching { Json.parseToJsonElement(body) as? JsonObject }.getOrNull()
    private val bodyStatus: Int? = text("statusCode")?.toIntOrNull()
    private val code: String? = text("code") ?: text("error")

    val status: Int = when {
        httpStatus == 400 && bodyStatus != null && bodyStatus in 400..599 -> bodyStatus
        code == "EntityTooLarge" -> 413
        else -> httpStatus
    }

    val isSuccess: Boolean get() = httpStatus in 200..299

    /** Worth another attempt: rate limiting and server errors (including Storage's 544 database timeout). */
    val isTransient: Boolean get() = status == 429 || status in 500..599

    /** Status plus Storage's error code and message, for logs and exceptions. */
    fun summary(): String {
        val detail = listOfNotNull(
            bodyStatus?.takeIf { it != httpStatus }?.let { "statusCode $it" },
            listOfNotNull(code, text("message")).joinToString(": ").ifEmpty { null },
        ).joinToString(", ")
        return ("HTTP $httpStatus" + if (detail.isEmpty()) "" else " ($detail)").take(MAX_SUMMARY)
    }

    private fun text(key: String): String? = (json?.get(key) as? JsonPrimitive)?.contentOrNull

    private companion object {
        const val MAX_SUMMARY = 300
    }
}
