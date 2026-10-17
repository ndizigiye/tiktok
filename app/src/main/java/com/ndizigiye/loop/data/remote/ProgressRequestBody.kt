package com.ndizigiye.loop.data.remote

import okhttp3.MediaType
import okhttp3.RequestBody
import okio.Buffer
import okio.BufferedSink
import okio.source
import java.io.File

/**
 * Streams [file] in 8 KB chunks and reports upload progress in 0f..1f.
 * [onProgress] runs on OkHttp's I/O thread, once per whole-percent change.
 */
class ProgressRequestBody(
    private val file: File,
    private val contentType: MediaType?,
    private val onProgress: (Float) -> Unit,
) : RequestBody() {

    override fun contentType(): MediaType? = contentType

    override fun contentLength(): Long = file.length()

    override fun writeTo(sink: BufferedSink) {
        val total = contentLength()
        var written = 0L
        var lastPercent = -1
        val buffer = Buffer()
        file.source().use { source ->
            while (true) {
                val read = source.read(buffer, CHUNK_SIZE)
                if (read == -1L) break
                sink.write(buffer, read)
                written += read
                val percent = if (total > 0) ((written * 100) / total).toInt().coerceIn(0, 100) else 0
                if (percent != lastPercent) {
                    lastPercent = percent
                    onProgress(percent / 100f)
                }
            }
        }
    }

    private companion object {
        const val CHUNK_SIZE = 8L * 1024
    }
}
