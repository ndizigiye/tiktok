package com.ndizigiye.loop.server.service

import com.ndizigiye.loop.api.UserDto
import com.ndizigiye.loop.api.VideoDto
import com.ndizigiye.loop.server.ApiException
import com.ndizigiye.loop.server.media.MediaKind
import com.ndizigiye.loop.server.media.MediaObject
import com.ndizigiye.loop.server.media.MediaStaging
import com.ndizigiye.loop.server.media.MediaStore
import com.ndizigiye.loop.server.media.MediaTools
import com.ndizigiye.loop.server.media.Upload
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

/**
 * Turns received uploads (local files in [MediaStaging]) into stored media plus rows: probes and
 * thumbnails the local copy, stores the results in [media], then inserts the row. Staged files are
 * always deleted; stored objects are deleted again when a later step fails.
 */
class UploadService(
    private val media: MediaStore,
    private val staging: MediaStaging,
    private val tools: MediaTools,
    private val videos: VideoService,
    private val users: UserService,
) {
    private val log = LoggerFactory.getLogger(UploadService::class.java)

    suspend fun publishVideo(userId: Long, upload: Upload): VideoDto {
        val file = upload.file ?: throw ApiException.validation("Attach a video file in the 'video' field.")
        val thumbnailFile = staging.newFile("jpg")
        val stored = mutableListOf<MediaObject>()
        try {
            val caption = VideoRules.caption(upload.fields["caption"].orEmpty())
            val soundId = upload.fields["soundId"]?.trim()?.takeIf { it.isNotEmpty() }?.let {
                it.toLongOrNull() ?: throw ApiException.validation("soundId must be a number.")
            }
            val probe = tools.probe(file)
            val video = MediaObject.newVideo(file.extension)
            val thumbnail = MediaObject.thumbnailOf(video).takeIf { tools.thumbnail(file, thumbnailFile) }
            media.put(video, file)
            stored += video
            if (thumbnail != null) {
                media.put(thumbnail, thumbnailFile)
                stored += thumbnail
            }
            return videos.create(userId, NewVideo(video.name, thumbnail?.name, probe, caption, soundId))
        } catch (e: Throwable) {
            withContext(NonCancellable) { deleteQuietly(stored) }
            throw e
        } finally {
            file.delete()
            thumbnailFile.delete()
        }
    }

    suspend fun replaceAvatar(userId: Long, upload: Upload): UserDto {
        val file = upload.file ?: throw ApiException.validation("Attach an image in the 'avatar' field.")
        val avatar = MediaObject.newAvatar(userId, file.extension)
        val (user, previous) = try {
            media.put(avatar, file)
            try {
                users.replaceAvatar(userId, avatar.name)
            } catch (e: Throwable) {
                withContext(NonCancellable) { deleteQuietly(listOf(avatar)) }
                throw e
            }
        } finally {
            file.delete()
        }
        previous?.let { deleteQuietly(listOf(MediaObject(MediaKind.AVATARS, it))) }
        return user
    }

    /** Removes the media of a deleted video. */
    suspend fun deleteVideoMedia(files: DeletedVideoFiles) {
        deleteQuietly(
            listOfNotNull(MediaObject(MediaKind.VIDEOS, files.videoFile), files.thumbFile?.let { MediaObject(MediaKind.THUMBS, it) }),
        )
    }

    /** A leftover object only costs space, so a failed delete is logged instead of failing the request. */
    private suspend fun deleteQuietly(objects: List<MediaObject>) {
        if (objects.isEmpty()) return
        try {
            media.delete(objects)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn("Could not delete {}: {}", objects.joinToString { it.path }, e.message)
        }
    }
}
