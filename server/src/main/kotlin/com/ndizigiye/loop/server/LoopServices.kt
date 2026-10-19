package com.ndizigiye.loop.server

import com.ndizigiye.loop.server.auth.TokenService
import com.ndizigiye.loop.server.db.LoopDatabase
import com.ndizigiye.loop.server.db.MetaStore
import com.ndizigiye.loop.server.feed.ForYouService
import com.ndizigiye.loop.server.media.LocalMediaStore
import com.ndizigiye.loop.server.media.MediaStaging
import com.ndizigiye.loop.server.media.MediaStore
import com.ndizigiye.loop.server.media.MediaTools
import com.ndizigiye.loop.server.media.SupabaseMediaStore
import com.ndizigiye.loop.server.service.CommentService
import com.ndizigiye.loop.server.service.DiscoverService
import com.ndizigiye.loop.server.service.DtoAssembler
import com.ndizigiye.loop.server.service.NotificationService
import com.ndizigiye.loop.server.service.UploadService
import com.ndizigiye.loop.server.service.UserService
import com.ndizigiye.loop.server.service.VideoService
import java.io.Closeable
import java.io.File
import java.util.UUID

/** Wires the server's components for one database, media store and data directory. */
class LoopServices private constructor(
    val config: ServerConfig,
    val db: LoopDatabase,
    /** Local disk, or Supabase Storage when `LOOP_SUPABASE_SERVICE_KEY` is set. */
    val media: MediaStore,
    tokenKey: String,
) : Closeable {
    val staging = MediaStaging(File(config.dataDir, "tmp"))
    val tools = MediaTools(config.ffmpeg, config.ffprobe)
    val tokens = TokenService(config.jwtSecret, tokenKey)
    private val dto = DtoAssembler(media)
    val users = UserService(db, tokens, dto)
    val videos = VideoService(db, dto)
    val comments = CommentService(db, dto)
    val forYou = ForYouService(db, dto)
    val discover = DiscoverService(db, dto)
    val notifications = NotificationService(db, dto)
    val uploads = UploadService(media, staging, tools, videos, users)

    override fun close() = db.close()

    companion object {
        suspend fun open(config: ServerConfig): LoopServices {
            config.dataDir.mkdirs()
            val media = config.supabase?.let { SupabaseMediaStore(it) } ?: LocalMediaStore(config.dataDir)
            media.verify()
            val db = config.postgres?.let(LoopDatabase::postgres) ?: LoopDatabase.sqlite(File(config.dataDir, "loop.db"))
            val tokenKey = try {
                db.tx { MetaStore.getOrPut("token_key") { UUID.randomUUID().toString() } }
            } catch (e: Throwable) {
                db.close()
                throw e
            }
            return LoopServices(config, db, media, tokenKey)
        }
    }
}
