package com.ndizigiye.loop.server.media

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Media files below the data directory (`videos/`, `thumbs/`, `avatars/`), served by the
 * server itself at `/media/<kind>/<name>` with range requests (see `MediaRoutes`). Used when
 * `LOOP_SUPABASE_SERVICE_KEY` is not set.
 */
class LocalMediaStore(dataDir: File) : MediaStore {
    private val dirs: Map<MediaKind, File> = MediaKind.entries.associateWith { File(dataDir, it.dir) }

    init {
        dirs.values.forEach { it.mkdirs() }
    }

    override val description: String = "local disk ${dataDir.absolutePath}"

    override fun url(obj: MediaObject): String = "/media/${obj.path}"

    /** An existing regular file named [name] in [kind]'s folder, rejecting anything path-like. */
    fun existing(kind: MediaKind, name: String): File? {
        val obj = MediaObject(kind, name)
        if (!obj.isSafe) return null
        return fileOf(obj).takeIf { it.isFile }
    }

    /** Moves [source] into place: a rename, since [MediaStaging] lives in the same data directory. */
    override suspend fun put(obj: MediaObject, source: File, replace: Boolean) {
        require(obj.isSafe) { "Unsafe media name: ${obj.path}" }
        withContext(Dispatchers.IO) {
            val options = if (replace) arrayOf(StandardCopyOption.REPLACE_EXISTING) else emptyArray()
            Files.move(source.toPath(), fileOf(obj).toPath(), *options)
        }
    }

    override suspend fun delete(objects: Collection<MediaObject>) {
        withContext(Dispatchers.IO) {
            objects.filter { it.isSafe }.forEach { Files.deleteIfExists(fileOf(it).toPath()) }
        }
    }

    private fun fileOf(obj: MediaObject): File = File(dirs.getValue(obj.kind), obj.name)
}
