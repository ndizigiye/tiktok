package com.ndizigiye.loop.data.session

import com.ndizigiye.loop.BuildConfig
import com.ndizigiye.loop.testutil.FakeContext
import com.ndizigiye.loop.testutil.user
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionStoreTest {

    private val context = FakeContext()
    private val store = SessionStore(context)

    @Test
    fun `normalizeBaseUrl adds a missing scheme and trailing slash`() {
        assertEquals("http://192.168.1.20:8080/", SessionStore.normalizeBaseUrl("  192.168.1.20:8080 "))
        assertEquals("https://loop.example.org/proxy/", SessionStore.normalizeBaseUrl("https://loop.example.org/proxy"))
        assertEquals("http://10.0.2.2:8080/", SessionStore.normalizeBaseUrl("http://10.0.2.2:8080/"))
        assertEquals("HTTPS://Loop.Example.org/", SessionStore.normalizeBaseUrl("HTTPS://Loop.Example.org"))
    }

    @Test
    fun `the build's default server is used until another is set`() {
        assertEquals(BuildConfig.DEFAULT_BASE_URL, store.baseUrl.value)
        assertTrue(store.baseUrl.value.endsWith("/"))
    }

    @Test
    fun `a new server URL is normalised and survives a restart`() {
        store.setBaseUrl(" example.org:9000/loop ")

        assertEquals("http://example.org:9000/loop/", store.baseUrl.value)
        assertEquals("http://example.org:9000/loop/", SessionStore(context).baseUrl.value)
    }

    @Test
    fun `resolveUrl joins server-relative paths with the server URL and its prefix`() {
        store.setBaseUrl("http://example.org:9000/loop/")

        assertEquals("http://example.org:9000/loop/media/videos/a.mp4", store.resolveUrl("/media/videos/a.mp4"))
        assertEquals("http://example.org:9000/loop/media/thumbs/a.jpg", store.resolveUrl("media/thumbs/a.jpg"))
    }

    @Test
    fun `resolveUrl keeps absolute URLs and maps null or blank to null`() {
        assertEquals("https://cdn.example.net/a.jpg", store.resolveUrl("https://cdn.example.net/a.jpg"))
        assertEquals("HTTP://CDN.example.net/a.jpg", store.resolveUrl("HTTP://CDN.example.net/a.jpg"))
        assertNull(store.resolveUrl(null))
        assertNull(store.resolveUrl(""))
        assertNull(store.resolveUrl("   "))
    }

    @Test
    fun `resolveUrl follows server URL changes`() {
        store.setBaseUrl("http://10.0.2.2:8080")
        val before = store.resolveUrl("/media/a.mp4")
        store.setBaseUrl("http://192.168.1.20:8080/")

        assertEquals("http://10.0.2.2:8080/media/a.mp4", before)
        assertEquals("http://192.168.1.20:8080/media/a.mp4", store.resolveUrl("/media/a.mp4"))
    }

    @Test
    fun `a saved session marks the user as me and survives a restart`() {
        store.save("token-1", user(id = 5, username = "ana"))

        val restored = SessionStore(context)
        assertTrue(restored.isLoggedIn)
        assertEquals("token-1", restored.token)
        assertEquals(user(id = 5, username = "ana", isMe = true), restored.currentUser)
        assertEquals(store.session.value, restored.session.value)
    }

    @Test
    fun `updateUser only replaces the signed-in user`() {
        store.updateUser(user(id = 5))
        assertNull(store.session.value)

        store.save("token-1", user(id = 5, username = "ana"))
        store.updateUser(user(id = 6, username = "bo"))
        assertEquals("ana", store.currentUser?.username)

        store.updateUser(user(id = 5, username = "ana", followerCount = 12))
        assertEquals(12, store.currentUser?.followerCount)
        assertTrue(store.currentUser!!.isMe)
        assertEquals(12, SessionStore(context).currentUser?.followerCount)
    }

    @Test
    fun `clear signs out for good but keeps the server URL`() {
        store.setBaseUrl("http://example.org/")
        store.save("token-1", user(id = 5))

        store.clear()

        assertFalse(store.isLoggedIn)
        assertNull(store.token)
        val restored = SessionStore(context)
        assertNull(restored.session.value)
        assertEquals("http://example.org/", restored.baseUrl.value)
    }

    @Test
    fun `an unreadable stored user counts as signed out`() {
        context.getSharedPreferences("loop_session", 0).edit()
            .putString("token", "token-1")
            .putString("user", "{not json")
            .apply()

        assertNull(SessionStore(context).session.value)
    }
}
