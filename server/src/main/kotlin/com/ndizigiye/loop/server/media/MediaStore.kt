package com.ndizigiye.loop.server.media

import java.io.File
import java.security.SecureRandom

/** Media folders, the first segment of every stored object's path (`videos/<name>`). */
enum class MediaKind(val dir: String) {
    VIDEOS("videos"),
    THUMBS("thumbs"),
    AVATARS("avatars"),
}

/**
 * One stored media file: [name] in [kind]'s folder. The database keeps only the name; the
 * [MediaStore] turns it into a URL.
 */
data class MediaObject(val kind: MediaKind, val name: String) {
    /** The object's key in the store, e.g. `videos/3f2a….mp4`. */
    val path: String get() = "${kind.dir}/$name"

    /** Only names the server generates; anything path-like is never stored, served or deleted. */
    val isSafe: Boolean get() = SAFE_NAME.matches(name)

    /** The MIME type stored with the object, from its extension. */
    val contentType: String get() = CONTENT_TYPES[name.substringAfterLast('.', "")] ?: "application/octet-stream"

    companion object {
        private val SAFE_NAME = Regex("^[A-Za-z0-9][A-Za-z0-9_-]{0,100}\\.[a-z0-9]{1,5}$")
        private val random = SecureRandom()

        /** Stored extension -> MIME type, the reverse of the accepted upload types plus thumbnails. */
        private val CONTENT_TYPES: Map<String, String> =
            (UploadSpec.VIDEO.types + UploadSpec.AVATAR.types).entries.associate { (mime, extension) -> extension to mime }

        /** Every MIME type the server stores. */
        internal val STORED_CONTENT_TYPES: Set<String> = CONTENT_TYPES.values.toSet()

        /** A new video with an unguessable name: `videos/<random>.<extension>`. */
        fun newVideo(extension: String) = MediaObject(MediaKind.VIDEOS, "${randomId()}.$extension")

        /** A new profile photo of [userId]: `avatars/<userId>-<random>.<extension>`. */
        fun newAvatar(userId: Long, extension: String) = MediaObject(MediaKind.AVATARS, "$userId-${randomId()}.$extension")

        /** The thumbnail that belongs to [video]: `thumbs/<video name without extension>.jpg`. */
        fun thumbnailOf(video: MediaObject) = MediaObject(MediaKind.THUMBS, "${video.name.substringBeforeLast('.')}.jpg")

        private fun randomId(): String {
            val bytes = ByteArray(12).also(random::nextBytes)
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}

/**
 * Where uploaded and generated media live: [LocalMediaStore] (the data directory, served by
 * `/media/...`) or [SupabaseMediaStore] (a public Supabase Storage bucket). ffmpeg and
 * ffprobe always work on local files ([MediaStaging]); a store only receives finished files.
 */
interface MediaStore {
    /** Credential-free summary for the startup log. */
    val description: String

    /** Fails with instructions when the store cannot be used; called once at startup. */
    suspend fun verify() {}

    /** The URL clients load [obj] from: server-relative (`/media/...`) or absolute. */
    fun url(obj: MediaObject): String

    /**
     * Stores the local file [source] as [obj]. The store may move [source]; the caller deletes it
     * afterwards if it still exists. Without [replace] an existing object is an error, which never
     * happens with the random names of uploads. A file the store refuses as too large is
     * [com.ndizigiye.loop.server.ApiException.tooLarge].
     */
    suspend fun put(obj: MediaObject, source: File, replace: Boolean = false)

    /** Deletes [objects]; missing ones and unsafe names are ignored. Throws when the store fails. */
    suspend fun delete(objects: Collection<MediaObject>)
}

fun MediaStore.url(kind: MediaKind, name: String): String = url(MediaObject(kind, name))
