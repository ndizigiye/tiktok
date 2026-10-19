package com.ndizigiye.loop.server.media

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** Display size and length of a video, with rotation metadata already applied. */
data class VideoProbe(val durationMs: Long, val width: Int, val height: Int)

/** ffprobe / ffmpeg wrapper. Every operation degrades gracefully when the tools are missing. */
class MediaTools(private val ffmpeg: String, private val ffprobe: String) {
    private val log = LoggerFactory.getLogger(MediaTools::class.java)
    private val availability = mutableMapOf<String, Boolean>()
    private val availabilityLock = Mutex()

    suspend fun ffmpegAvailable(): Boolean = isAvailable(ffmpeg)

    suspend fun ffprobeAvailable(): Boolean = isAvailable(ffprobe)

    /** Duration and display size of [file], or null when ffprobe is unavailable or cannot read it. */
    suspend fun probe(file: File): VideoProbe? {
        if (!ffprobeAvailable()) return null
        val result = ProcessRunner.run(
            listOf(
                ffprobe, "-v", "error",
                "-select_streams", "v:0",
                "-show_entries", "stream=width,height:stream_tags=rotate:stream_side_data=rotation:format=duration",
                "-of", "json",
                file.absolutePath,
            ),
            PROBE_TIMEOUT,
        )
        if (result == null || !result.ok) {
            log.info("ffprobe could not read {}: {}", file.name, result?.stderr?.trim())
            return null
        }
        return runCatching { parseProbe(result.stdout) }.getOrNull()
    }

    /** Writes a 360 px wide JPEG frame from about 0.5 s into [video]; returns false if none was written. */
    suspend fun thumbnail(video: File, target: File): Boolean {
        if (!ffmpegAvailable()) return false
        for (offset in listOf("0.5", "0")) {
            val result = runFfmpeg(
                listOf(
                    "-ss", offset, "-i", video.absolutePath,
                    "-frames:v", "1", "-vf", "scale=360:-2", "-q:v", "4",
                    target.absolutePath,
                ),
                THUMBNAIL_TIMEOUT,
            )
            if (result?.ok == true && target.length() > 0) return true
        }
        target.delete()
        return false
    }

    /** Runs ffmpeg quietly with [args] (overwriting outputs); null when ffmpeg cannot be started. */
    suspend fun runFfmpeg(args: List<String>, timeout: Duration): ProcessRunner.Result? =
        ProcessRunner.run(listOf(ffmpeg, "-nostdin", "-hide_banner", "-v", "error", "-y") + args, timeout)

    private suspend fun isAvailable(tool: String): Boolean = availabilityLock.withLock {
        availability.getOrPut(tool) {
            val ok = ProcessRunner.run(listOf(tool, "-version"), VERSION_TIMEOUT)?.ok == true
            if (!ok) log.warn("'{}' is not available; media processing that needs it is skipped", tool)
            ok
        }
    }

    internal companion object {
        val PROBE_TIMEOUT = 30.seconds
        val THUMBNAIL_TIMEOUT = 1.minutes
        val VERSION_TIMEOUT = 10.seconds

        fun parseProbe(json: String): VideoProbe? {
            val root = Json.parseToJsonElement(json).jsonObject
            val stream = (root["streams"] as? JsonArray)?.firstOrNull()?.jsonObject ?: return null
            val width = stream["width"]?.jsonPrimitive?.intOrNull ?: return null
            val height = stream["height"]?.jsonPrimitive?.intOrNull ?: return null
            val seconds = (root["format"] as? JsonObject)?.get("duration")?.jsonPrimitive?.content?.toDoubleOrNull()
            val quarterTurn = rotationOf(stream) % 180 != 0
            return VideoProbe(
                durationMs = seconds?.let { (it * 1000).roundToLong() } ?: 0,
                width = if (quarterTurn) height else width,
                height = if (quarterTurn) width else height,
            )
        }

        private fun rotationOf(stream: JsonObject): Int {
            val sideData = (stream["side_data_list"] as? JsonArray)
                ?.firstNotNullOfOrNull { it.jsonObject["rotation"]?.jsonPrimitive?.doubleOrNull }
            val tag = (stream["tags"] as? JsonObject)?.get("rotate")?.jsonPrimitive?.content?.toDoubleOrNull()
            return abs((sideData ?: tag ?: 0.0).toInt())
        }
    }
}
