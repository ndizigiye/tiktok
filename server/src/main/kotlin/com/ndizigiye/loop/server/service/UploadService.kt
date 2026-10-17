package com.ndizigiye.loop.server.service

import com.ndizigiye.loop.api.UserDto
import com.ndizigiye.loop.api.VideoDto
import com.ndizigiye.loop.server.ApiException
import com.ndizigiye.loop.server.media.MediaKind
import com.ndizigiye.loop.server.media.MediaStorage
import com.ndizigiye.loop.server.media.MediaTools
import com.ndizigiye.loop.server.media.Upload
import java.io.File

/** Turns stored multipart uploads into videos and avatars; cleans up files on failure. */
class UploadService(
    private val storage: MediaStorage,
    private val tools: MediaTools,
    private val videos: VideoService,
    private val users: UserService,
) {
    suspend fun publishVideo(userId: Long, upload: Upload): VideoDto {
        val file = upload.file ?: throw ApiException.validation("Attach a video file in the 'video' field.")
        var thumbnail: File? = null
        try {
            val caption = VideoRules.caption(upload.fields["caption"].orEmpty())
            val soundId = upload.fields["soundId"]?.trim()?.takeIf { it.isNotEmpty() }?.let {
                it.toLongOrNull() ?: throw ApiException.validation("soundId must be a number.")
            }
            val probe = tools.probe(file)
            thumbnail = storage.thumbnailFor(file).takeIf { tools.thumbnail(file, it) }
            return videos.create(userId, NewVideo(file.name, thumbnail?.name, probe, caption, soundId))
        } catch (e: Throwable) {
            file.delete()
            thumbnail?.delete()
            throw e
        }
    }

    suspend fun replaceAvatar(userId: Long, upload: Upload): UserDto {
        val file = upload.file ?: throw ApiException.validation("Attach an image in the 'avatar' field.")
        val (user, previous) = try {
            users.replaceAvatar(userId, file.name)
        } catch (e: Throwable) {
            file.delete()
            throw e
        }
        storage.delete(MediaKind.AVATARS, previous)
        return user
    }

    fun deleteVideoFiles(files: DeletedVideoFiles) {
        storage.delete(MediaKind.VIDEOS, files.videoFile)
        storage.delete(MediaKind.THUMBS, files.thumbFile)
    }
}
