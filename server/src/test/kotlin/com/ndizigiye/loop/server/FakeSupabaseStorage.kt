package com.ndizigiye.loop.server

import com.ndizigiye.loop.server.media.SupabaseStorageSettings
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

/**
 * A local stand-in for the Supabase Storage endpoints the server uses, on a JDK HttpServer at
 * 127.0.0.1: upload (`POST /storage/v1/object/<bucket>/<path>`), batch delete
 * (`DELETE /storage/v1/object/<bucket>`), bucket details (`GET /storage/v1/bucket/<bucket>`) and
 * public downloads (`GET /storage/v1/object/public/<bucket>/<path>`). Objects live in memory.
 *
 * Errors look like Storage's: HTTP 400 with the real status in the body
 * (`{"statusCode": "413", "error": "Payload too large", ...}`). Every request is recorded; whether
 * it carried the service key is recorded as a flag, never the key, so test output cannot leak it.
 */
class FakeSupabaseStorage(val bucket: String = "loop-media") : Closeable {
    /** Random per instance; tests compare against it but never print it. */
    val serviceKey: String = "sb_secret_test_" + UUID.randomUUID().toString().replace("-", "")

    class Request(
        val method: String,
        /** Raw path, e.g. `/storage/v1/object/loop-media/videos/x.mp4`. */
        val path: String,
        /** `Authorization: Bearer <key>` and `apikey: <key>` were both present and correct. */
        val authorized: Boolean,
        val contentType: String?,
        val upsert: String?,
        val cacheControl: String?,
        val contentLength: Long?,
        /** Bytes actually received in the body. */
        val bodyBytes: Long,
        /** The body as text when it is JSON (deletes), else null. */
        val json: String?,
    ) {
        override fun toString() = "$method $path ($bodyBytes bytes)"
    }

    class StoredObject(val bytes: ByteArray, val contentType: String?, val cacheControl: String?)

    private class Scripted(val method: String, val status: Int, val body: String, val afterHandling: Boolean)

    /** Stored objects by path inside the bucket (`videos/x.mp4`). */
    val objects: MutableMap<String, StoredObject> = ConcurrentHashMap()
    val requests = CopyOnWriteArrayList<Request>()

    @Volatile var bucketExists = true
    @Volatile var bucketPublic = true
    /** The bucket's `file_size_limit` as reported by the bucket endpoint (null: only the global limit). */
    @Volatile var reportedSizeLimit: Long? = 50L * 1024 * 1024
    /** Uploads above this many bytes are refused with Storage's 413 (independent of [reportedSizeLimit]). */
    @Volatile var refuseAbove: Long = Long.MAX_VALUE

    private val scripted = ConcurrentLinkedQueue<Scripted>()
    private val executor = Executors.newCachedThreadPool { runnable -> Thread(runnable, "fake-storage").apply { isDaemon = true } }
    private val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0).apply {
        createContext("/") { exchange -> exchange.use(::handle) }
        executor = this@FakeSupabaseStorage.executor
        start()
    }

    val url: String = "http://127.0.0.1:${server.address.port}"

    val settings: SupabaseStorageSettings = SupabaseStorageSettings.create(serviceKey = serviceKey, url = url, bucket = bucket)

    /** Public URL of [path], as the server should put it in DTOs. */
    fun publicUrl(path: String) = "$url/storage/v1/object/public/$bucket/$path"

    /**
     * The next [method] request gets [status] and [body]. With [afterHandling] the request is
     * handled normally first (e.g. the object is stored) and only the answer is replaced.
     */
    fun failNext(method: String, status: Int, body: String = INTERNAL_ERROR, afterHandling: Boolean = false) {
        scripted += Scripted(method, status, body, afterHandling)
    }

    fun requests(method: String) = requests.filter { it.method == method }

    private fun handle(exchange: HttpExchange) {
        val method = exchange.requestMethod
        val path = exchange.requestURI.rawPath
        val bytes = exchange.requestBody.use { input -> ByteArrayOutputStream().also { input.copyTo(it) }.toByteArray() }
        val headers = exchange.requestHeaders
        val contentType = headers.getFirst("Content-Type")
        val request = Request(
            method = method,
            path = path,
            authorized = headers.getFirst("Authorization") == "Bearer $serviceKey" && headers.getFirst("apikey") == serviceKey,
            contentType = contentType,
            upsert = headers.getFirst("x-upsert"),
            cacheControl = headers.getFirst("Cache-Control"),
            contentLength = headers.getFirst("Content-Length")?.toLongOrNull(),
            bodyBytes = bytes.size.toLong(),
            json = if (contentType?.startsWith("application/json") == true) bytes.toString(Charsets.UTF_8) else null,
        )
        requests += request

        val script = scripted.firstOrNull { it.method == method }?.also { scripted.remove(it) }
        if (script != null && !script.afterHandling) return exchange.respond(script.status, script.body)
        val (status, body, type) = route(request, bytes)
        if (script != null) return exchange.respond(script.status, script.body)
        exchange.respond(status, body, type)
    }

    private fun route(request: Request, bytes: ByteArray): Triple<Int, Any, String> {
        val objectPrefix = "/storage/v1/object/$bucket"
        val publicPrefix = "/storage/v1/object/public/$bucket/"
        return when {
            request.method == "GET" && request.path.startsWith(publicPrefix) -> {
                val stored = objects[request.path.removePrefix(publicPrefix)] ?: return error(404, "not_found", "Object not found")
                Triple(200, stored.bytes, stored.contentType ?: "application/octet-stream")
            }
            !request.authorized -> error(403, "Unauthorized", "invalid signature")
            !bucketExists -> error(404, "Bucket not found", "Bucket not found")
            request.method == "GET" && request.path == "/storage/v1/bucket/$bucket" -> json(
                buildJsonObject {
                    put("id", bucket)
                    put("name", bucket)
                    put("public", bucketPublic)
                    put("file_size_limit", reportedSizeLimit?.let(::JsonPrimitive) ?: JsonNull)
                    put("allowed_mime_types", JsonNull)
                }.toString(),
            )
            request.method == "POST" && request.path.startsWith("$objectPrefix/") -> {
                val key = request.path.removePrefix("$objectPrefix/")
                when {
                    bytes.size > refuseAbove -> error(413, "Payload too large", "The object exceeded the maximum allowed size")
                    key in objects && request.upsert != "true" -> error(409, "Duplicate", "The resource already exists")
                    else -> {
                        objects[key] = StoredObject(bytes, request.contentType, request.cacheControl)
                        json("""{"Key":"$bucket/$key","Id":"${UUID.randomUUID()}"}""")
                    }
                }
            }
            request.method == "DELETE" && request.path == objectPrefix -> {
                val prefixes = Json.parseToJsonElement(request.json ?: "{}").jsonObject["prefixes"]?.jsonArray.orEmpty()
                val deleted = prefixes.map { it.jsonPrimitive.content }.filter { objects.remove(it) != null }
                json(buildJsonArray { deleted.forEach { add(buildJsonObject { put("name", it); put("bucket_id", bucket) }) } }.toString())
            }
            else -> error(404, "not_found", "Route not found")
        }
    }

    private fun json(body: String) = Triple(200, body, "application/json")

    private fun error(status: Int, error: String, message: String) =
        Triple(400, """{"statusCode":"$status","error":"$error","message":"$message"}""", "application/json")

    private fun HttpExchange.respond(status: Int, body: Any, type: String = "application/json") {
        val bytes = if (body is ByteArray) body else body.toString().toByteArray()
        responseHeaders.add("Content-Type", type)
        sendResponseHeaders(status, if (bytes.isEmpty()) -1 else bytes.size.toLong())
        if (bytes.isNotEmpty()) responseBody.use { it.write(bytes) }
    }

    private fun JsonArray?.orEmpty(): JsonArray = this ?: JsonArray(emptyList())

    override fun close() {
        server.stop(0)
        executor.shutdownNow()
    }

    companion object {
        const val INTERNAL_ERROR = """{"statusCode":"500","error":"internal","message":"Internal Server Error"}"""
    }
}
