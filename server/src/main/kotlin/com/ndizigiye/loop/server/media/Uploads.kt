package com.ndizigiye.loop.server.media

import com.ndizigiye.loop.api.ApiLimits
import com.ndizigiye.loop.server.ApiException
import io.ktor.http.ContentType
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.contentLength
import io.ktor.server.request.isMultipart
import io.ktor.server.request.receiveMultipart
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** What a multipart endpoint accepts in its single file part. */
data class UploadSpec(
    val fileField: String,
    val maxBytes: Long,
    /** Accepted MIME type -> stored file extension. */
    val types: Map<String, String>,
    val label: String,
) {
    private val extensions: Set<String> = types.values.toSet()

    internal fun extensionFor(contentType: ContentType?, originalName: String?): String? {
        contentType?.withoutParameters()?.toString()?.lowercase()?.let { mime -> types[mime]?.let { return it } }
        val generic = contentType == null || contentType.match(ContentType.Application.OctetStream)
        val fromName = originalName?.substringAfterLast('.', "")?.lowercase()
        return fromName?.takeIf { generic && it in extensions }
    }

    companion object {
        val VIDEO = UploadSpec(
            fileField = "video",
            maxBytes = ApiLimits.MAX_UPLOAD_BYTES,
            types = mapOf(
                "video/mp4" to "mp4",
                "video/webm" to "webm",
                "video/3gpp" to "3gp",
                "video/quicktime" to "mov",
            ),
            label = "Videos must be MP4, WebM, 3GP or MOV",
        )

        val AVATAR = UploadSpec(
            fileField = "avatar",
            maxBytes = 5L * 1024 * 1024,
            types = mapOf(
                "image/jpeg" to "jpg",
                "image/png" to "png",
                "image/webp" to "webp",
            ),
            label = "Profile photos must be JPEG, PNG or WebP",
        )
    }
}

/** Text fields of a multipart request plus the stored file, if the request had one. */
class Upload(val file: File?, val fields: Map<String, String>)

/**
 * Streams the [spec] file part to `destination(extension)` and collects text fields.
 * The stored file is deleted again if anything fails.
 */
suspend fun ApplicationCall.receiveUpload(spec: UploadSpec, destination: (extension: String) -> File): Upload {
    if (!request.isMultipart()) throw ApiException.validation("Expected a multipart/form-data upload.")
    val sizeMessage = "Files must be at most ${spec.maxBytes / (1024 * 1024)} MB."
    request.contentLength()?.let { if (it > spec.maxBytes + MULTIPART_OVERHEAD) throw ApiException.tooLarge(sizeMessage) }

    val fields = mutableMapOf<String, String>()
    var stored: File? = null
    try {
        receiveMultipart(formFieldLimit = spec.maxBytes + MULTIPART_OVERHEAD).forEachPart { part ->
            try {
                when (part) {
                    is PartData.FormItem -> part.name?.let { fields[it] = part.value }
                    is PartData.FileItem -> if (part.name == spec.fileField && stored == null) {
                        val extension = spec.extensionFor(part.contentType, part.originalFileName)
                            ?: throw ApiException.validation("${spec.label}.")
                        val target = destination(extension)
                        stored = target
                        part.provider().copyToFile(target, spec.maxBytes, sizeMessage)
                    }
                    else -> Unit
                }
            } finally {
                part.dispose()
            }
        }
    } catch (e: Throwable) {
        stored?.delete()
        throw e
    }
    return Upload(stored, fields)
}

private suspend fun ByteReadChannel.copyToFile(target: File, maxBytes: Long, sizeMessage: String) {
    val buffer = ByteArray(COPY_BUFFER_BYTES)
    var total = 0L
    withContext(Dispatchers.IO) {
        target.outputStream().use { out ->
            while (true) {
                val read = readAvailable(buffer, 0, buffer.size)
                if (read < 0) break
                total += read
                if (total > maxBytes) throw ApiException.tooLarge(sizeMessage)
                out.write(buffer, 0, read)
            }
        }
    }
}

private const val MULTIPART_OVERHEAD = 1L * 1024 * 1024
private const val COPY_BUFFER_BYTES = 64 * 1024
