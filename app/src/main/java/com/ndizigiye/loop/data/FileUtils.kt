package com.ndizigiye.loop.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** Every temporary file Loop writes to the cache directory starts with this; see [deleteStaleTempFiles]. */
const val TEMP_FILE_PREFIX = "loop-"

/**
 * Copies the content behind [uri] (content:// or file://) into a new file in the
 * cache directory, named "loop-<prefix>…<.ext>", on the IO dispatcher. The file is
 * deleted again when the copy fails or the caller is cancelled, including a
 * cancellation that arrives just after the copy completed.
 * @throws IOException when the content cannot be read.
 */
suspend fun Context.copyUriToCache(uri: Uri, prefix: String): File {
    var file: File? = null
    try {
        return withContext(Dispatchers.IO) {
            val extension = mimeTypeOf(uri)
                ?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
                ?: uri.lastPathSegment?.substringAfterLast('.', "")?.takeIf { it.length in 1..5 }
            val target = File.createTempFile(TEMP_FILE_PREFIX + prefix, extension?.let { ".$it" }, cacheDir)
            file = target
            val input = contentResolver.openInputStream(uri) ?: throw IOException("Cannot open $uri")
            input.use { source -> target.outputStream().use { sink -> source.copyTo(sink) } }
            target
        }
    } catch (e: Throwable) {
        // withContext can complete the copy and still throw CancellationException to a cancelled caller.
        file?.let { withContext(NonCancellable + Dispatchers.IO) { it.delete() } }
        throw e
    }
}

/**
 * Deletes Loop's own temporary files in the cache directory (camera takes, upload and
 * avatar copies) last modified more than [maxAgeMs] ago. A screen abandoned at process
 * death never deletes its files itself. Runs on the IO dispatcher.
 */
suspend fun Context.deleteStaleTempFiles(maxAgeMs: Long) {
    withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - maxAgeMs
        cacheDir.listFiles()
            ?.filter { it.isFile && it.name.startsWith(TEMP_FILE_PREFIX) && it.lastModified() < cutoff }
            ?.forEach { it.delete() }
    }
}

/** The MIME type of [uri] from the content resolver, or guessed from its file extension. */
fun Context.mimeTypeOf(uri: Uri): String? {
    if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
        contentResolver.getType(uri)?.let { return it }
    }
    val extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
        .ifEmpty { uri.lastPathSegment?.substringAfterLast('.', "").orEmpty() }
        .lowercase()
    if (extension.isEmpty()) return null
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
}
