package com.ndizigiye.loop.server

import java.io.File

/** Runtime settings, normally read from the environment (see docs/api.md, "Server configuration"). */
data class ServerConfig(
    val port: Int = DEFAULT_PORT,
    val dataDir: File = File("data"),
    val jwtSecret: String = DEV_JWT_SECRET,
    val seed: Boolean = true,
    val ffmpeg: String = "ffmpeg",
    val ffprobe: String = "ffprobe",
) {
    val usesDevJwtSecret: Boolean get() = jwtSecret == DEV_JWT_SECRET

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
            )
        }

        private fun parseFlag(raw: String): Boolean = when (raw.lowercase()) {
            "1", "true", "yes", "on" -> true
            "0", "false", "no", "off" -> false
            else -> error("LOOP_SEED must be true or false, was '$raw'")
        }
    }
}
