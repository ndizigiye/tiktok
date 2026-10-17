package com.ndizigiye.loop.server

import com.ndizigiye.loop.server.auth.TokenService
import com.ndizigiye.loop.server.db.LoopDatabase
import com.ndizigiye.loop.server.db.MetaStore
import com.ndizigiye.loop.server.feed.ForYouService
import com.ndizigiye.loop.server.media.MediaStorage
import com.ndizigiye.loop.server.media.MediaTools
import com.ndizigiye.loop.server.service.CommentService
import com.ndizigiye.loop.server.service.DiscoverService
import com.ndizigiye.loop.server.service.NotificationService
import com.ndizigiye.loop.server.service.UploadService
import com.ndizigiye.loop.server.service.UserService
import com.ndizigiye.loop.server.service.VideoService
import java.io.Closeable
import java.io.File
import java.util.UUID

/** Wires the server's components for one data directory. */
class LoopServices private constructor(
    val config: ServerConfig,
    val db: LoopDatabase,
    tokenKey: String,
) : Closeable {
    val storage = MediaStorage(config.dataDir)
    val tools = MediaTools(config.ffmpeg, config.ffprobe)
    val tokens = TokenService(config.jwtSecret, tokenKey)
    val users = UserService(db, tokens)
    val videos = VideoService(db)
    val comments = CommentService(db)
    val forYou = ForYouService(db)
    val discover = DiscoverService(db)
    val notifications = NotificationService(db)
    val uploads = UploadService(storage, tools, videos, users)

    override fun close() = db.close()

    companion object {
        suspend fun open(config: ServerConfig): LoopServices {
            config.dataDir.mkdirs()
            val db = LoopDatabase(File(config.dataDir, "loop.db"))
            val tokenKey = db.tx { MetaStore.getOrPut("token_key") { UUID.randomUUID().toString() } }
            return LoopServices(config, db, tokenKey)
        }
    }
}
