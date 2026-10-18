package com.ndizigiye.loop.testutil

import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartReader
import okhttp3.mockwebserver.RecordedRequest

/** One part of a recorded `multipart/form-data` body. */
class FormPart(val headers: Headers, val bytes: ByteArray) {
    private val disposition = headers["Content-Disposition"].orEmpty()

    val name: String? = Regex("""(?:^|;\s*)name="([^"]*)"""").find(disposition)?.groupValues?.get(1)
    val filename: String? = Regex("""(?:^|;\s*)filename="([^"]*)"""").find(disposition)?.groupValues?.get(1)
    val contentType: String? = headers["Content-Type"]
    val text: String get() = bytes.decodeToString()
}

/** The parts of this request's multipart body, in order. */
fun RecordedRequest.formParts(): List<FormPart> {
    val boundary = getHeader("Content-Type")?.toMediaType()?.parameter("boundary")
        ?: error("Not a multipart request: ${getHeader("Content-Type")}")
    return MultipartReader(body, boundary).use { reader ->
        generateSequence { reader.nextPart() }
            .map { part -> part.use { FormPart(it.headers, it.body.readByteArray()) } }
            .toList()
    }
}
