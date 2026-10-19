package com.ndizigiye.loop.server.media

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.util.Base64

/**
 * Supabase Storage settings from `LOOP_SUPABASE_URL`, `LOOP_SUPABASE_BUCKET` and the secret
 * `LOOP_SUPABASE_SERVICE_KEY`. [toString] leaves the key out and error messages never contain it.
 */
class SupabaseStorageSettings private constructor(
    /** Project URL without a trailing slash, e.g. `https://<ref>.supabase.co`. */
    val url: String,
    val bucket: String,
    /** Secret (`sb_secret_...`) or legacy `service_role` key; it bypasses row level security. */
    val serviceKey: String,
) {
    override fun toString(): String = "SupabaseStorageSettings(url=$url, bucket=$bucket, serviceKey=<redacted>)"

    companion object {
        /** The Loop project (`qzqkxwtbajifzsdkotud`, eu-central-1). */
        const val DEFAULT_URL = "https://qzqkxwtbajifzsdkotud.supabase.co"
        const val DEFAULT_BUCKET = "loop-media"

        private val BUCKET_NAME = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,99}$")
        /** Visible ASCII: anything else cannot be sent in an HTTP header. */
        private val HEADER_SAFE = Regex("^[\\x21-\\x7E]+$")
        private val LOCAL_HOSTS = setOf("localhost", "127.0.0.1", "[::1]", "::1")

        /**
         * Validates the settings. The service key travels in request headers, so plain `http://` is
         * only accepted for local hosts (test doubles); everything else must be `https://`.
         */
        fun create(serviceKey: String, url: String = DEFAULT_URL, bucket: String = DEFAULT_BUCKET): SupabaseStorageSettings {
            val key = serviceKey.trim()
            require(key.isNotEmpty() && HEADER_SAFE.matches(key)) {
                "LOOP_SUPABASE_SERVICE_KEY must be the project's secret key on one line, without spaces."
            }
            require(!isClientKey(key)) {
                "LOOP_SUPABASE_SERVICE_KEY is a publishable (anon) key. Use the project's secret key " +
                    "(sb_secret_..., or the legacy service_role key): the server uploads and deletes media with it."
            }
            require(BUCKET_NAME.matches(bucket)) {
                "LOOP_SUPABASE_BUCKET must be a bucket name of letters, digits, '.', '_' and '-'."
            }
            return SupabaseStorageSettings(normalizeUrl(url), bucket, key)
        }

        private fun normalizeUrl(raw: String): String {
            // The URL is not echoed in errors: a mistake could put credentials in it.
            val invalid = "LOOP_SUPABASE_URL must be the project URL, like $DEFAULT_URL"
            val uri = runCatching { URI(raw.trim()) }.getOrNull()
            require(uri != null && uri.host != null && uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null) {
                "$invalid."
            }
            val scheme = uri.scheme?.lowercase()
            require(scheme == "https" || (scheme == "http" && uri.host.lowercase() in LOCAL_HOSTS)) {
                "$invalid (https; http only for localhost)."
            }
            return raw.trim().trimEnd('/')
        }

        /** A publishable key, or a legacy JWT key whose role is `anon`. */
        private fun isClientKey(key: String): Boolean {
            if (key.startsWith("sb_publishable_")) return true
            val payload = key.split('.').takeIf { it.size == 3 }?.get(1) ?: return false
            val role = runCatching {
                val json = String(Base64.getUrlDecoder().decode(payload.trimEnd('=')), Charsets.UTF_8)
                Json.parseToJsonElement(json).jsonObject["role"]?.jsonPrimitive?.contentOrNull
            }.getOrNull()
            return role == "anon"
        }
    }
}
