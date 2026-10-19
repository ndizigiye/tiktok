package com.ndizigiye.loop.e2e.harness

import java.io.File
import java.io.IOException
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlin.io.path.createTempDirectory
import kotlin.random.Random

/**
 * Media files for uploads, made once per test JVM with ffmpeg and deleted when it exits: a
 * short portrait MP4 and a PNG profile photo. Without ffmpeg the video is arbitrary bytes
 * (the server then stores it without a thumbnail) and the photo a 1x1 PNG.
 */
object TestMedia {

    private val directory: File by lazy {
        createTempDirectory("loop-e2e-media").toFile().also { dir ->
            Runtime.getRuntime().addShutdownHook(Thread { dir.deleteRecursively() })
        }
    }

    /** A 3-second 270x480 H.264/AAC clip. */
    val video: File by lazy {
        val file = File(directory, "clip.mp4")
        val rendered = runFfmpeg(
            "-f", "lavfi", "-i", "testsrc2=size=270x480:rate=15",
            "-f", "lavfi", "-i", "sine=frequency=440",
            "-t", "3", "-c:v", "libx264", "-pix_fmt", "yuv420p", "-c:a", "aac", "-shortest",
            "-movflags", "+faststart", file.absolutePath,
        )
        if (!rendered) file.writeBytes(Random.nextBytes(64 * 1024))
        file
    }

    /** A 96x96 PNG. */
    val avatar: File by lazy {
        val file = File(directory, "avatar.png")
        val rendered = runFfmpeg("-f", "lavfi", "-i", "gradients=size=96x96:seed=7", "-frames:v", "1", file.absolutePath)
        if (!rendered) file.writeBytes(Base64.getDecoder().decode(ONE_PIXEL_PNG))
        file
    }

    private fun runFfmpeg(vararg args: String): Boolean = try {
        val process = ProcessBuilder(listOf("ffmpeg", "-v", "error", "-y") + args)
            .redirectErrorStream(true)
            .start()
        process.inputStream.use { it.readBytes() }
        process.waitFor(60, TimeUnit.SECONDS) && process.exitValue() == 0
    } catch (e: IOException) {
        false
    }

    private const val ONE_PIXEL_PNG =
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=="
}
