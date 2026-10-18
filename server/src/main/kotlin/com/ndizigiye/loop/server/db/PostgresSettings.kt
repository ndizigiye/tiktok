package com.ndizigiye.loop.server.db

import java.io.ByteArrayOutputStream
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Connection settings for PostgreSQL (Supabase's session pooler in production), parsed from
 * `LOOP_DATABASE_URL`. The JDBC URL never contains credentials: [user] and [password] are
 * passed to the driver separately, and [toString] and [describe] leave the password out, so
 * these settings are safe to log.
 */
class PostgresSettings private constructor(
    /** `host[:port]`, or several of them separated by commas, exactly as the driver expects. */
    val hosts: String,
    val database: String,
    val user: String?,
    val password: String?,
    /** Driver parameters except credentials, including the enforced `sslmode` and `currentSchema`. */
    val parameters: Map<String, String>,
    val poolSize: Int,
) {
    val jdbcUrl: String
        get() = "jdbc:postgresql://$hosts/${database.urlEncoded()}" +
            parameters.entries.joinToString("&", prefix = "?") { (key, value) -> "${key.urlEncoded()}=${value.urlEncoded()}" }

    /** The same server and options with another database name (the PostgreSQL test suite creates one per test). */
    fun withDatabase(name: String): PostgresSettings = PostgresSettings(hosts, name, user, password, parameters, poolSize)

    /** A one-line, credential-free summary for logs. */
    fun describe(): String =
        "PostgreSQL $hosts/$database (user ${user ?: "<driver default>"}, sslmode ${parameters[SSL_MODE] ?: "driver default"}, " +
            "schema $SCHEMA, pool $poolSize)"

    override fun toString(): String =
        "PostgresSettings(url=$jdbcUrl, user=$user, password=${if (password == null) "none" else "<redacted>"}, poolSize=$poolSize)"

    companion object {
        /** Schema holding every Loop table; created by the Supabase migration, never by the server. */
        const val SCHEMA = "loop"
        const val DEFAULT_POOL_SIZE = 5
        val POOL_SIZES = 1..50

        private const val SSL_MODE = "sslmode"
        private const val CURRENT_SCHEMA = "currentSchema"

        /** `sslmode` values that encrypt the connection; anything weaker is upgraded to `require` for remote hosts. */
        private val ENCRYPTED_SSL_MODES = setOf("require", "verify-ca", "verify-full")
        private val LOCAL_HOSTS = setOf("localhost", "127.0.0.1", "::1")

        /**
         * Parses a Supabase connection string (`postgresql://user:password@host:5432/postgres`, also
         * `postgres://`) or a JDBC URL (`jdbc:postgresql://host:5432/postgres?user=...&password=...`).
         *
         * User name and password are percent-decoded; a password with unencoded special characters
         * still works because the credentials end at the last `@`. Remote hosts always get an
         * encrypted connection (`sslmode=require` unless a stricter mode is given) and every
         * connection uses the [SCHEMA] schema. Error messages never repeat the URL, because it
         * contains the password.
         */
        fun parse(raw: String, poolSize: Int = DEFAULT_POOL_SIZE): PostgresSettings {
            require(poolSize in POOL_SIZES) { "The database pool size must be between ${POOL_SIZES.first} and ${POOL_SIZES.last}." }
            val url = raw.trim()
            val jdbc = url.startsWith("jdbc:postgresql://", ignoreCase = true)
            val rest = listOf("jdbc:postgresql://", "postgresql://", "postgres://")
                .firstOrNull { url.startsWith(it, ignoreCase = true) }
                ?.let { url.substring(it.length) }
                ?: throw invalid("it must start with postgresql:// (a Supabase connection string) or jdbc:postgresql://")

            // Credentials end at the last '@'. JDBC URLs have no user info in the driver, so only an
            // '@' before their path counts there.
            val searchEnd = if (jdbc) rest.indexOfAny(charArrayOf('/', '?')).takeIf { it >= 0 } ?: rest.length else rest.length
            val at = rest.lastIndexOf('@', searchEnd - 1)
            val userInfo = if (at >= 0) rest.substring(0, at) else null
            val location = rest.substring(at + 1)

            val queryStart = location.indexOf('?').takeIf { it >= 0 } ?: location.length
            val path = location.substring(0, queryStart)
            val slash = path.indexOf('/').takeIf { it >= 0 } ?: path.length
            val hosts = path.substring(0, slash)
            if (hosts.isEmpty() || hosts.split(',').any { it.isBlank() }) throw invalid("it has no host")
            // JDBC URLs follow the driver's decoding ('+' is a space); connection strings follow libpq's.
            val decode: (String) -> String = if (jdbc) ::formDecode else ::percentDecode
            val database = path.substring(slash).removePrefix("/").let(decode).ifEmpty { "postgres" }

            val parameters = LinkedHashMap<String, String>()
            location.substring(queryStart).removePrefix("?").split('&').filter { it.isNotEmpty() }.forEach { pair ->
                val key = decode(pair.substringBefore('='))
                parameters[key] = decode(pair.substringAfter('=', ""))
            }
            val userFromQuery = parameters.remove("user")
            val passwordFromQuery = parameters.remove("password")
            val user = userInfo?.substringBefore(':')?.let(::percentDecode)?.ifEmpty { null } ?: userFromQuery?.ifEmpty { null }
            val password = userInfo?.takeIf { ':' in it }?.substringAfter(':')?.let(::percentDecode)?.ifEmpty { null }
                ?: passwordFromQuery?.ifEmpty { null }

            if (!isLocal(hosts) && parameters[SSL_MODE]?.lowercase() !in ENCRYPTED_SSL_MODES) {
                parameters[SSL_MODE] = "require"
            }
            parameters[CURRENT_SCHEMA] = SCHEMA
            return PostgresSettings(hosts, database, user, password, parameters, poolSize)
        }

        private fun invalid(reason: String) = IllegalArgumentException("LOOP_DATABASE_URL is not a valid PostgreSQL URL: $reason.")

        private fun isLocal(hosts: String): Boolean = hosts.split(',').all { entry ->
            val host = if (entry.startsWith("[")) entry.substringAfter('[').substringBefore(']') else entry.substringBefore(':')
            host.lowercase() in LOCAL_HOSTS
        }

        /** RFC 3986 percent-decoding as libpq does it: '+' stays '+', malformed escapes stay literal. */
        private fun percentDecode(value: String): String {
            if ('%' !in value) return value
            val bytes = ByteArrayOutputStream(value.length)
            var i = 0
            while (i < value.length) {
                val high = hexValue(value.getOrNull(i + 1))
                val low = hexValue(value.getOrNull(i + 2))
                if (value[i] == '%' && high >= 0 && low >= 0) {
                    bytes.write(high * 16 + low)
                    i += 3
                } else {
                    val end = if (value[i].isHighSurrogate() && i + 1 < value.length) i + 2 else i + 1
                    bytes.write(value.substring(i, end).toByteArray(Charsets.UTF_8))
                    i = end
                }
            }
            return bytes.toString(Charsets.UTF_8)
        }

        private fun hexValue(c: Char?): Int = if (c == null) -1 else HEX_DIGITS.indexOf(c.lowercaseChar())

        private const val HEX_DIGITS = "0123456789abcdef"

        private fun formDecode(value: String): String =
            runCatching { URLDecoder.decode(value, Charsets.UTF_8) }.getOrElse { throw invalid("it contains a malformed %-escape") }

        private fun String.urlEncoded(): String = URLEncoder.encode(this, Charsets.UTF_8)
    }
}
