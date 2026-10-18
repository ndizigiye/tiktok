package com.ndizigiye.loop.server

import com.ndizigiye.loop.server.db.PostgresSettings
import com.ndizigiye.loop.server.media.SupabaseStorageSettings
import java.io.File

/** Runtime settings, normally read from the environment (see docs/api.md, "Server configuration"). */
data class ServerConfig(
    val port: Int = DEFAULT_PORT,
    val dataDir: File = File("data"),
    val jwtSecret: String = DEV_JWT_SECRET,
    val seed: Boolean = true,
    val ffmpeg: String = "ffmpeg",
    val ffprobe: String = "ffprobe",
    /** Where rows live: PostgreSQL (Supabase) when set, otherwise SQLite in [dataDir]. */
    val postgres: PostgresSettings? = null,
    /** Where media lives: Supabase Storage when set, otherwise files in [dataDir]. */
    val supabase: SupabaseStorageSettings? = null,
) {
    val usesDevJwtSecret: Boolean get() = jwtSecret == DEV_JWT_SECRET

    /**
     * Like the generated one, but without the JWT secret; [PostgresSettings] and [SupabaseStorageSettings]
     * redact their password and key themselves.
     */
    override fun toString(): String =
        "ServerConfig(port=$port, dataDir=$dataDir, jwtSecret=<redacted>, seed=$seed, ffmpeg=$ffmpeg, ffprobe=$ffprobe, " +
            "postgres=$postgres, supabase=$supabase)"

    companion object {
        const val DEFAULT_PORT = 8080
        internal const val DEV_JWT_SECRET = "loop-dev-secret-change-me"

        fun fromEnv(env: Map<String, String> = System.getenv()): ServerConfig {
            fun value(name: String): String? = env[name]?.trim()?.takeIf { it.isNotEmpty() }
            return ServerConfig(
                port = value("PORT")?.let { raw ->
                    raw.toIntOrNull()?.takeIf { it in 1..65535 }
                        ?: error("PORT must be a number between 1 and 65535, was '$raw'")
                } ?: DEFAULT_PORT,
                dataDir = File(value("LOOP_DATA_DIR") ?: "data"),
                jwtSecret = value("LOOP_JWT_SECRET") ?: DEV_JWT_SECRET,
                seed = value("LOOP_SEED")?.let(::parseFlag) ?: true,
                ffmpeg = value("LOOP_FFMPEG") ?: "ffmpeg",
                ffprobe = value("LOOP_FFPROBE") ?: "ffprobe",
                postgres = value("LOOP_DATABASE_URL")?.let { url ->
                    val poolSize = value("LOOP_DATABASE_POOL_SIZE")?.let { raw ->
                        raw.toIntOrNull()?.takeIf { it in PostgresSettings.POOL_SIZES }
                            ?: error(
                                "LOOP_DATABASE_POOL_SIZE must be a number between ${PostgresSettings.POOL_SIZES.first} and " +
                                    "${PostgresSettings.POOL_SIZES.last}, was '$raw'",
                            )
                    } ?: PostgresSettings.DEFAULT_POOL_SIZE
                    PostgresSettings.parse(url, poolSize)
                },
                supabase = value("LOOP_SUPABASE_SERVICE_KEY")?.let { key ->
                    SupabaseStorageSettings.create(
                        serviceKey = key,
                        url = value("LOOP_SUPABASE_URL") ?: SupabaseStorageSettings.DEFAULT_URL,
                        bucket = value("LOOP_SUPABASE_BUCKET") ?: SupabaseStorageSettings.DEFAULT_BUCKET,
                    )
                },
            )
        }

        private fun parseFlag(raw: String): Boolean = when (raw.lowercase()) {
            "1", "true", "yes", "on" -> true
            "0", "false", "no", "off" -> false
            else -> error("LOOP_SEED must be true or false, was '$raw'")
        }
    }
}
