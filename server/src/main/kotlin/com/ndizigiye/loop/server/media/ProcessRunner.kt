package com.ndizigiye.loop.server.media

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.time.Duration

/** Runs external tools without blocking coroutine threads; cancelling the caller kills the process. */
object ProcessRunner {
    data class Result(val exitCode: Int, val stdout: String, val stderr: String) {
        val ok: Boolean get() = exitCode == 0
    }

    /** Returns null when the executable cannot be started (for example, it is not installed). */
    suspend fun run(command: List<String>, timeout: Duration): Result? = runInterruptible(Dispatchers.IO) {
        val stdout = File.createTempFile("loop-proc", ".out")
        val stderr = File.createTempFile("loop-proc", ".err")
        try {
            val process = try {
                ProcessBuilder(command).redirectOutput(stdout).redirectError(stderr).start()
            } catch (e: IOException) {
                return@runInterruptible null
            }
            try {
                process.outputStream.close()
                if (!process.waitFor(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)) {
                    Result(-1, "", "timed out after $timeout")
                } else {
                    Result(process.exitValue(), stdout.readText(), stderr.readText().takeLast(MAX_ERROR_CHARS))
                }
            } finally {
                if (process.isAlive) process.destroyForcibly()
            }
        } finally {
            stdout.delete()
            stderr.delete()
        }
    }

    private const val MAX_ERROR_CHARS = 2_000
}
