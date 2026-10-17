package com.ndizigiye.loop.server.media

import java.io.File
import java.security.SecureRandom

/** Media folders below the data directory, each served at `/media/<dir>/<file>`. */
enum class MediaKind(val dir: String) {
    VIDEOS("videos"),
    THUMBS("thumbs"),
    AVATARS("avatars"),
    ;

    fun url(fileName: String): String = "/media/$dir/$fileName"
}

/** File layout for uploaded and generated media under the data directory. */
class MediaStorage(dataDir: File) {
    private val dirs: Map<MediaKind, File> = MediaKind.entries.associateWith { File(dataDir, it.dir) }
    private val random = SecureRandom()

    init {
        dirs.values.forEach { it.mkdirs() }
    }

    fun dir(kind: MediaKind): File = dirs.getValue(kind)

    /** A new file with an unguessable name; it is not created yet. */
    fun newFile(kind: MediaKind, extension: String): File = File(dir(kind), "${randomName()}.$extension")

    /** The thumbnail location that belongs to [video]. */
    fun thumbnailFor(video: File): File = File(dir(MediaKind.THUMBS), "${video.nameWithoutExtension}.jpg")

    /** An existing regular file named [name] in [kind]'s folder, rejecting anything path-like. */
    fun existing(kind: MediaKind, name: String): File? {
        if (!SAFE_NAME.matches(name)) return null
        return File(dir(kind), name).takeIf { it.isFile }
    }

    fun delete(kind: MediaKind, name: String?) {
        name?.let { existing(kind, it)?.delete() }
    }

    private fun randomName(): String {
        val bytes = ByteArray(12).also(random::nextBytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private companion object {
        val SAFE_NAME = Regex("^[A-Za-z0-9][A-Za-z0-9_-]{0,100}\\.[a-z0-9]{1,5}$")
    }
}
