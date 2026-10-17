package com.ndizigiye.loop.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Copies the content behind [uri] (content:// or file://) into a new file in the
 * cache directory, named "<prefix>…<.ext>", on the IO dispatcher.
 * @throws IOException when the content cannot be read.
 */
suspend fun Context.copyUriToCache(uri: Uri, prefix: String): File = withContext(Dispatchers.IO) {
    val extension = mimeTypeOf(uri)
        ?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
        ?: uri.lastPathSegment?.substringAfterLast('.', "")?.takeIf { it.length in 1..5 }
    val file = File.createTempFile(prefix.padEnd(3, '_'), extension?.let { ".$it" }, cacheDir)
    try {
        val input = contentResolver.openInputStream(uri) ?: throw IOException("Cannot open $uri")
        input.use { source -> file.outputStream().use { target -> source.copyTo(target) } }
        file
    } catch (e: Exception) {
        file.delete()
        throw e
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
