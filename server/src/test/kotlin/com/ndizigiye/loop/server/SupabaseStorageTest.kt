package com.ndizigiye.loop.server

import com.ndizigiye.loop.server.media.MediaKind
import com.ndizigiye.loop.server.media.MediaObject
import com.ndizigiye.loop.server.media.MediaStoreException
import com.ndizigiye.loop.server.media.SupabaseMediaStore
import com.ndizigiye.loop.server.media.SupabaseStorageSettings
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.ServerSocket
import java.util.Base64
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/** [SupabaseMediaStore] and its settings against [FakeSupabaseStorage]; no server or database involved. */
class SupabaseStorageTest {
    private val storage = FakeSupabaseStorage()
    private val temp = mutableListOf<File>()

    @AfterTest
    fun cleanUp() {
        storage.close()
        temp.forEach { it.delete() }
    }

    private fun store(settings: SupabaseStorageSettings = storage.settings) =
        SupabaseMediaStore(settings, retryDelays = listOf(1.milliseconds, 1.milliseconds))

    private fun file(bytes: ByteArray): File = File.createTempFile("loop-storage-test", ".bin").also {
        temp += it
        it.writeBytes(bytes)
    }

    @Test
    fun `the environment switches media to Supabase only with a service key`() {
        assertNull(ServerConfig.fromEnv(emptyMap()).supabase, "local disk by default")
        assertNull(ServerConfig.fromEnv(mapOf("LOOP_SUPABASE_URL" to "https://abc.supabase.co")).supabase, "the URL alone changes nothing")

        val defaults = ServerConfig.fromEnv(mapOf("LOOP_SUPABASE_SERVICE_KEY" to " sb_secret_abc ")).supabase!!
        assertEquals("https://qzqkxwtbajifzsdkotud.supabase.co", defaults.url)
        assertEquals("loop-media", defaults.bucket)
        assertEquals("sb_secret_abc", defaults.serviceKey)

        val custom = ServerConfig.fromEnv(
            mapOf(
                "LOOP_SUPABASE_SERVICE_KEY" to "sb_secret_abc",
                "LOOP_SUPABASE_URL" to "https://other.supabase.co/",
                "LOOP_SUPABASE_BUCKET" to "media.test",
            ),
        ).supabase!!
        assertEquals("https://other.supabase.co", custom.url)
        assertEquals("media.test", custom.bucket)
        val legacyServiceRole = jwt("""{"iss":"supabase","role":"service_role"}""")
        assertEquals(legacyServiceRole, SupabaseStorageSettings.create(legacyServiceRole).serviceKey)
    }

    @Test
    fun `the service key never appears in descriptions or errors`() {
        val key = "sb_secret_DoNotLeak123"
        val settings = SupabaseStorageSettings.create(key)
        val config = ServerConfig(supabase = settings)
        listOf(settings.toString(), config.toString(), SupabaseMediaStore(settings).description).forEach { text ->
            assertFalse(key in text, text)
        }
        assertTrue("serviceKey=<redacted>" in settings.toString())

        val invalid = listOf(
            { SupabaseStorageSettings.create("$key with spaces") },
            { SupabaseStorageSettings.create("$key\nsecond line") },
            { SupabaseStorageSettings.create("") },
            { SupabaseStorageSettings.create(key, url = "http://qzqkxwtbajifzsdkotud.supabase.co") },
            { SupabaseStorageSettings.create(key, url = "https://user:$key@example.supabase.co") },
            { SupabaseStorageSettings.create(key, url = "not a url") },
            { SupabaseStorageSettings.create(key, bucket = "../other") },
            { SupabaseStorageSettings.create("sb_publishable_$key") },
            { SupabaseStorageSettings.create(jwt("""{"role":"anon","note":"$key"}""")) },
        )
        for (attempt in invalid) {
            val error = assertFailsWith<IllegalArgumentException> { attempt() }
            assertFalse(key in error.message.orEmpty(), error.message)
        }
    }

    @Test
    fun `public URLs point at the bucket`() {
        val video = MediaObject(MediaKind.VIDEOS, "abc.mp4")
        assertEquals(
            "https://qzqkxwtbajifzsdkotud.supabase.co/storage/v1/object/public/loop-media/videos/abc.mp4",
            SupabaseMediaStore(SupabaseStorageSettings.create("sb_secret_x")).url(video),
        )
        assertEquals(storage.publicUrl("videos/abc.mp4"), store().url(video))
        assertEquals(MediaObject(MediaKind.THUMBS, "abc.jpg"), MediaObject.thumbnailOf(video))
        val avatar = MediaObject.newAvatar(42, "png")
        assertTrue(Regex("42-[0-9a-f]{24}\\.png").matches(avatar.name), avatar.name)
        assertTrue(Regex("[0-9a-f]{24}\\.webm").matches(MediaObject.newVideo("webm").name))
    }

    @Test
    fun `uploads stream the file with the service key and its content type`() = runBlocking {
        val bytes = Random(7).nextBytes(3 * 1024 * 1024 + 17)
        val source = file(bytes)
        val video = MediaObject.newVideo("mp4")
        store().put(video, source)

        val request = storage.requests.single()
        assertEquals("POST", request.method)
        assertEquals("/storage/v1/object/loop-media/${video.path}", request.path)
        assertTrue(request.authorized, "Authorization: Bearer and apikey carry the service key")
        assertEquals("video/mp4", request.contentType)
        assertEquals("false", request.upsert)
        assertEquals("max-age=604800", request.cacheControl)
        assertEquals(bytes.size.toLong(), request.contentLength, "sent with a Content-Length, not chunked")
        assertEquals(bytes.size.toLong(), request.bodyBytes)
        assertContentEquals(bytes, storage.objects.getValue(video.path).bytes)
        assertTrue(source.isFile, "the caller deletes the source")

        val types = mapOf("x.jpg" to "image/jpeg", "x.png" to "image/png", "x.webp" to "image/webp", "x.mov" to "video/quicktime")
        for ((name, type) in types) {
            store().put(MediaObject(MediaKind.THUMBS, name), file(byteArrayOf(1)), replace = true)
            assertEquals(type, storage.requests.last().contentType)
            assertEquals("true", storage.requests.last().upsert)
        }
        assertFailsWith<IllegalArgumentException> { store().put(MediaObject(MediaKind.VIDEOS, "../loop.db"), source) }
        assertEquals(1 + types.size, storage.requests.size, "unsafe names are never sent")
    }

    @Test
    fun `deletes are one batch request and skip unsafe names`() = runBlocking {
        val video = MediaObject.newVideo("mp4")
        val thumbnail = MediaObject.thumbnailOf(video)
        store().put(video, file(byteArrayOf(1, 2)))
        store().put(thumbnail, file(byteArrayOf(3)))
        storage.requests.clear()

        store().delete(listOf(video, thumbnail, MediaObject(MediaKind.VIDEOS, "../../other/x.mp4")))
        val request = storage.requests.single()
        assertEquals("DELETE", request.method)
        assertEquals("/storage/v1/object/loop-media", request.path)
        assertTrue(request.authorized)
        val prefixes = Json.parseToJsonElement(request.json!!).jsonObject.getValue("prefixes").jsonArray.map { it.jsonPrimitive.content }
        assertEquals(listOf(video.path, thumbnail.path), prefixes)
        assertTrue(storage.objects.isEmpty())

        store().delete(emptyList())
        store().delete(listOf(video))
        assertEquals(2, storage.requests.size, "nothing to send for no objects; missing objects are fine")

        storage.failNext("DELETE", 400, """{"statusCode":"403","error":"Unauthorized","message":"new row violates row-level security policy"}""")
        val error = assertFailsWith<MediaStoreException> { store().delete(listOf(video)) }
        assertTrue("HTTP 400 (statusCode 403, Unauthorized" in error.message.orEmpty(), error.message)
    }

    @Test
    fun `transient failures are retried`() = runBlocking {
        storage.failNext("POST", 503, "Service Unavailable")
        storage.failNext("POST", 400, """{"statusCode":"544","error":"DatabaseTimeout","message":"timeout"}""")
        val first = MediaObject.newVideo("mp4")
        store().put(first, file(byteArrayOf(1, 2, 3)))
        assertEquals(3, storage.requests("POST").size, "two transient failures, then success")
        assertTrue(first.path in storage.objects)

        repeat(3) { storage.failNext("POST", 500) }
        assertFailsWith<MediaStoreException> { store().put(MediaObject.newVideo("mp4"), file(byteArrayOf(1))) }
        assertEquals(6, storage.requests("POST").size, "three attempts in all")

        storage.failNext("POST", 400, """{"statusCode":"403","error":"Unauthorized","message":"invalid signature"}""")
        assertFailsWith<MediaStoreException> { store().put(MediaObject.newVideo("mp4"), file(byteArrayOf(1))) }
        assertEquals(7, storage.requests("POST").size, "client errors are not retried")

        // The object was stored, but the answer was lost: the retry finds it and succeeds.
        storage.failNext("POST", 502, "Bad Gateway", afterHandling = true)
        val lost = MediaObject.newVideo("mp4")
        store().put(lost, file(byteArrayOf(9)))
        assertEquals(9, storage.requests("POST").size)
        assertTrue(lost.path in storage.objects)
    }

    @Test
    fun `network failures are retried and reported`() = runBlocking {
        val closedPort = ServerSocket(0).use { it.localPort }
        val unreachable = SupabaseStorageSettings.create(storage.serviceKey, url = "http://127.0.0.1:$closedPort", bucket = "loop-media")
        val error = assertFailsWith<MediaStoreException> { store(unreachable).put(MediaObject.newVideo("mp4"), file(byteArrayOf(1))) }
        assertTrue("ConnectException" in error.message.orEmpty(), error.message)
        val startup = assertFailsWith<IllegalStateException> { store(unreachable).verify() }
        assertTrue("not reachable" in startup.message.orEmpty(), startup.message)
    }

    @Test
    fun `size errors from storage become too_large`() = runBlocking {
        storage.refuseAbove = 10
        val legacy = assertFailsWith<ApiException> { store().put(MediaObject.newVideo("mp4"), file(ByteArray(11))) }
        assertEquals("too_large", legacy.code)
        assertEquals(413, legacy.status.value)

        storage.failNext("POST", 413, """{"code":"EntityTooLarge","message":"The object exceeded the maximum allowed size"}""")
        assertEquals("too_large", assertFailsWith<ApiException> { store().put(MediaObject.newVideo("mp4"), file(ByteArray(5))) }.code)
        assertEquals(2, storage.requests("POST").size, "not retried")
        assertTrue(storage.objects.isEmpty())

        // After the startup check the bucket's own limit is known, so nothing is sent.
        storage.reportedSizeLimit = 4
        val checked = store().apply { verify() }
        assertEquals("too_large", assertFailsWith<ApiException> { checked.put(MediaObject.newVideo("mp4"), file(ByteArray(5))) }.code)
        assertEquals(2, storage.requests("POST").size)
    }

    @Test
    fun `the startup check names the problem`() = runBlocking {
        store().verify()
        val check = storage.requests.single()
        assertEquals("GET", check.method)
        assertEquals("/storage/v1/bucket/loop-media", check.path)
        assertTrue(check.authorized)

        storage.bucketPublic = false
        assertTrue("is not public" in assertFailsWith<IllegalStateException> { store().verify() }.message.orEmpty())
        storage.bucketExists = false
        assertTrue("no bucket \"loop-media\"" in assertFailsWith<IllegalStateException> { store().verify() }.message.orEmpty())

        val wrongKey = "sb_secret_wrong_${storage.serviceKey.takeLast(6)}"
        val wrong = SupabaseStorageSettings.create(wrongKey, url = storage.url, bucket = "loop-media")
        val rejected = assertFailsWith<IllegalStateException> { store(wrong).verify() }.message.orEmpty()
        assertTrue("rejected LOOP_SUPABASE_SERVICE_KEY" in rejected, rejected)
        assertFalse(wrongKey in rejected || storage.serviceKey in rejected)
    }

    @Test
    fun `storage error bodies are redacted`() = runBlocking {
        storage.failNext("POST", 400, """{"statusCode":"403","error":"Unauthorized","message":"bad key ${storage.serviceKey}"}""")
        val error = assertFailsWith<MediaStoreException> { store().put(MediaObject.newVideo("mp4"), file(byteArrayOf(1))) }
        val message = error.message.orEmpty()
        assertFalse(storage.serviceKey in message)
        assertTrue("bad key <redacted>" in message, message)
    }

    private fun jwt(payload: String): String {
        fun part(json: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(json.toByteArray())
        return "${part("""{"alg":"HS256","typ":"JWT"}""")}.${part(payload)}.c2lnbmF0dXJl"
    }
}
