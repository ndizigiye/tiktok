package com.ndizigiye.loop.server.seed

import com.ndizigiye.loop.api.CreateCommentRequest
import com.ndizigiye.loop.api.NotificationType
import com.ndizigiye.loop.server.LoopServices
import com.ndizigiye.loop.server.auth.PasswordHasher
import com.ndizigiye.loop.server.db.Bookmarks
import com.ndizigiye.loop.server.db.CommentLikes
import com.ndizigiye.loop.server.db.Follows
import com.ndizigiye.loop.server.db.MetaStore
import com.ndizigiye.loop.server.db.Notifications
import com.ndizigiye.loop.server.db.Sounds
import com.ndizigiye.loop.server.db.Users
import com.ndizigiye.loop.server.db.VideoLikes
import com.ndizigiye.loop.server.db.Videos
import com.ndizigiye.loop.server.db.ViewHistory
import com.ndizigiye.loop.server.media.MediaKind
import com.ndizigiye.loop.server.media.MediaObject
import com.ndizigiye.loop.server.media.VideoProbe
import com.ndizigiye.loop.server.service.NewVideo
import com.ndizigiye.loop.server.service.Notifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

/**
 * Fills an empty database with fictional demo creators, generated clips and social activity.
 *
 * Every step checks which rows already exist (users by name, clips by file name, avatars), so an
 * interrupted run resumes where it stopped. Media renders locally into [LoopServices.staging] and
 * goes to the media store under fixed names (`videos/demo-01.mp4`, `avatars/demo-<user>.jpg`),
 * replacing whatever an interrupted run stored. Clips render in parallel, up to one ffmpeg
 * process per CPU, and each clip is inserted with its likes and comments as soon as it is
 * stored, so the feed fills while the server is already serving.
 */
class DemoSeeder(
    private val services: LoopServices,
    /** The clips to create; tests pass a few. */
    private val clips: List<DemoClip> = DemoContent.clips,
) {
    private val log = LoggerFactory.getLogger(DemoSeeder::class.java)
    private val db = services.db
    private val store = services.media
    private val staging = services.staging
    private val renderer = ClipRenderer(services.tools)

    suspend fun seedIfNeeded() {
        try {
            val state = db.tx { MetaStore.get(STATE_KEY) }
            if (state == DONE) {
                log.info("Demo data was seeded before; demo seeding skipped.")
                return
            }
            if (state == null && db.tx { !Users.selectAll().empty() }) {
                log.info("Database already has accounts; demo seeding skipped.")
                return
            }
            db.tx { MetaStore.put(STATE_KEY, RUNNING) }
            seed(resuming = state == RUNNING)
            db.tx { MetaStore.put(STATE_KEY, DONE) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.error("Demo seeding failed; it resumes on the next start.", e)
        }
    }

    private suspend fun seed(resuming: Boolean) {
        val started = System.currentTimeMillis()
        log.info(if (resuming) "Resuming demo seeding." else "Seeding demo data into the empty database.")
        val users = seedUsers(started)
        seedFollows(users, started)
        val videoCount = if (services.tools.ffmpegAvailable()) {
            seedAvatars(users)
            seedClips(users, seedSounds(users, started), started)
        } else {
            log.warn(
                "ffmpeg is not available ({}): demo accounts and follows were created, but no demo videos.",
                services.config.ffmpeg,
            )
            0
        }
        db.tx {
            Notifications.update({
                (Notifications.recipientId inList users.values) and (Notifications.createdAt less started - DAY_MS)
            }) { it[read] = true }
        }
        log.info(
            "Demo seeding finished in {} s: {} accounts (password '{}'), {} new videos.",
            (System.currentTimeMillis() - started) / 1000, users.size, DemoContent.PASSWORD, videoCount,
        )
    }

    /** Creates missing demo accounts; returns username -> id for all of them. */
    private suspend fun seedUsers(now: Long): Map<String, Long> = DemoContent.users.withIndex().associate { (index, user) ->
        val existing = db.tx {
            Users.select(Users.id).where { Users.username eq user.username }.firstOrNull()?.get(Users.id)?.value
        }
        val id = existing ?: run {
            val hashed = withContext(Dispatchers.Default) { PasswordHasher.hash(DemoContent.PASSWORD) }
            db.tx {
                Users.insertAndGetId {
                    it[username] = user.username
                    it[displayName] = user.displayName
                    it[bio] = user.bio
                    it[passwordHash] = hashed.hash
                    it[passwordSalt] = hashed.salt
                    it[avatarColor] = user.avatarColor
                    it[verified] = user.verified
                    it[createdAt] = now - (60 - index) * DAY_MS
                }.value
            }
        }
        user.username to id
    }

    /** A follow graph in which verified creators are followed by almost everyone. */
    private suspend fun seedFollows(users: Map<String, Long>, now: Long) = db.tx {
        val random = Random(FOLLOW_SEED)
        for (follower in DemoContent.users) {
            for (target in DemoContent.users) {
                if (follower == target) continue
                val chance = if (target.verified) 0.85 else 0.45
                if (random.nextDouble() >= chance) continue
                val at = now - random.nextLong(HOUR_MS, 25 * DAY_MS)
                val inserted = Follows.insertIgnore {
                    it[followerId] = users.getValue(follower.username)
                    it[followeeId] = users.getValue(target.username)
                    it[createdAt] = at
                }.insertedCount > 0
                if (inserted) {
                    Notifier.notify(users.getValue(target.username), users.getValue(follower.username), NotificationType.FOLLOW, now = at)
                }
            }
        }
    }

    private suspend fun seedAvatars(users: Map<String, Long>) {
        DemoContent.users.withIndex().forEach { (index, user) ->
            val colors = user.avatarGradient ?: return@forEach
            val userId = users.getValue(user.username)
            val hasAvatar = db.tx {
                Users.select(Users.avatarFile).where { Users.id eq userId }.single()[Users.avatarFile] != null
            }
            if (hasAvatar) return@forEach
            val avatar = MediaObject(MediaKind.AVATARS, "demo-${user.username.replace('.', '-')}.jpg")
            val file = staging.newFile("jpg")
            try {
                renderer.renderAvatar(colors, index + 1, file)?.let { error ->
                    log.warn("Could not render the avatar of {}: {}", user.username, error)
                    return@forEach
                }
                store.put(avatar, file, replace = true)
            } finally {
                file.delete()
            }
            db.tx { Users.update({ Users.id eq userId }) { it[avatarFile] = avatar.name } }
        }
    }

    /** Creates missing shared sounds; returns key -> id. */
    private suspend fun seedSounds(users: Map<String, Long>, now: Long): Map<String, Long> = db.tx {
        DemoContent.sounds.associate { sound ->
            val authorId = users.getValue(sound.author)
            val existing = Sounds.select(Sounds.id)
                .where { (Sounds.title eq sound.title) and (Sounds.authorId eq authorId) }
                .firstOrNull()?.get(Sounds.id)?.value
            sound.key to (existing ?: Sounds.insertAndGetId {
                it[title] = sound.title
                it[authorName] = DemoContent.users.single { user -> user.username == sound.author }.displayName
                it[Sounds.authorId] = authorId
                it[createdAt] = now - 30 * DAY_MS
            }.value)
        }
    }

    /** Renders and inserts every clip that is not in the database yet; returns how many were added. */
    private suspend fun seedClips(users: Map<String, Long>, sounds: Map<String, Long>, now: Long): Int {
        val existing = db.tx { Videos.select(Videos.videoFile).mapTo(HashSet()) { it[Videos.videoFile] } }
        val pending = clips.filter { it.fileName !in existing }
        if (pending.isEmpty()) return 0
        val parallelism = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        log.info("Generating {} demo clips with ffmpeg ({} at a time)...", pending.size, parallelism)
        val permits = Semaphore(parallelism)
        val finished = AtomicInteger()
        val added = AtomicInteger()
        coroutineScope {
            pending.forEach { clip ->
                launch {
                    val media = prepare(clip, permits)
                    if (media != null) {
                        db.tx { insertClip(clip, media, users, sounds, now) }
                        added.incrementAndGet()
                    }
                    log.info("Demo clip {}/{} {}: {}", finished.incrementAndGet(), pending.size,
                        if (media != null) "ready" else "skipped", clip.fileName)
                }
            }
        }
        return added.get()
    }

    private class ClipMedia(val video: MediaObject, val thumbnail: MediaObject?, val probe: VideoProbe?)

    /**
     * Renders the clip, probes it and takes its thumbnail while holding one of [permits], then
     * stores both files. Rendering is deterministic, so storing replaces any earlier copy.
     */
    private suspend fun prepare(clip: DemoClip, permits: Semaphore): ClipMedia? {
        val file = staging.newFile("mp4")
        val thumbnailFile = staging.newFile("jpg")
        try {
            val video = MediaObject(MediaKind.VIDEOS, clip.fileName)
            val (probe, hasThumbnail) = permits.withPermit {
                val tone = clip.soundKey?.let { key -> DemoContent.sounds.single { it.key == key }.tone } ?: clip.tone
                renderer.renderClip(clip, tone, file)?.let { error ->
                    log.warn("Could not render {}: {}", clip.fileName, error)
                    return null
                }
                services.tools.probe(file) to services.tools.thumbnail(file, thumbnailFile)
            }
            val thumbnail = MediaObject.thumbnailOf(video).takeIf { hasThumbnail }
            store.put(video, file, replace = true)
            thumbnail?.let { store.put(it, thumbnailFile, replace = true) }
            return ClipMedia(video, thumbnail, probe)
        } finally {
            file.delete()
            thumbnailFile.delete()
        }
    }

    /** Inserts one clip with its views, likes, bookmarks, watch history and comments. */
    private fun insertClip(clip: DemoClip, media: ClipMedia, users: Map<String, Long>, sounds: Map<String, Long>, now: Long) {
        if (!Videos.selectAll().where { Videos.videoFile eq clip.fileName }.empty()) return
        val random = Random(clip.number * 7919L)
        val authorId = users.getValue(clip.author)
        val createdAt = now - clip.ageHours * HOUR_MS - random.nextLong(0, HOUR_MS)
        val videoId = services.videos.insertVideo(
            authorId,
            NewVideo(media.video.name, media.thumbnail?.name, media.probe, clip.caption, clip.soundKey?.let(sounds::getValue)),
            createdAt,
        )
        fun momentAfter(start: Long): Long = start + (random.nextDouble() * (now - start)).toLong()
        val others = users.values.filter { it != authorId }

        val views = random.nextLong(60, 900)
        Videos.update({ Videos.id eq videoId }) {
            it[viewCount] = views
            it[completionCount] = (views * random.nextDouble(0.2, 0.6)).toLong()
            it[shareCount] = random.nextLong(0, 18)
        }
        for (userId in others) {
            if (random.nextDouble() < 0.6) {
                val at = momentAfter(createdAt)
                VideoLikes.insert {
                    it[VideoLikes.userId] = userId
                    it[VideoLikes.videoId] = videoId
                    it[VideoLikes.createdAt] = at
                }
                Notifier.notify(authorId, userId, NotificationType.LIKE, videoId = videoId, now = at)
            }
            if (random.nextDouble() < 0.2) {
                Bookmarks.insert {
                    it[Bookmarks.userId] = userId
                    it[Bookmarks.videoId] = videoId
                    it[Bookmarks.createdAt] = momentAfter(createdAt)
                }
            }
            if (random.nextDouble() < 0.5) {
                ViewHistory.insert {
                    it[ViewHistory.userId] = userId
                    it[ViewHistory.videoId] = videoId
                    it[watchedMs] = clip.seconds * random.nextLong(300, 2_500)
                    it[completed] = random.nextBoolean()
                    it[viewedAt] = momentAfter(createdAt)
                }
            }
        }
        seedComments(videoId, authorId, others, users, random, createdAt, now)
    }

    private fun seedComments(
        videoId: Long,
        authorId: Long,
        others: List<Long>,
        users: Map<String, Long>,
        random: Random,
        videoCreatedAt: Long,
        now: Long,
    ) {
        val usernames = users.entries.associate { (name, id) -> id to name }
        val comments = services.comments
        repeat(random.nextInt(1, 5)) {
            val commenter = others.random(random)
            val at = videoCreatedAt + (random.nextDouble(0.05, 0.7) * (now - videoCreatedAt)).toLong()
            val text = DemoContent.commentTexts.random(random)
            val commentId = comments.insertComment(commenter, videoId, CreateCommentRequest(text), at)
            for (liker in users.values) {
                if (liker == commenter || random.nextDouble() >= 0.3) continue
                val likedAt = at + (random.nextDouble() * (now - at)).toLong()
                CommentLikes.insert {
                    it[userId] = liker
                    it[CommentLikes.commentId] = commentId
                    it[createdAt] = likedAt
                }
                Notifier.notify(commenter, liker, NotificationType.COMMENT_LIKE, videoId = videoId,
                    commentId = commentId, text = text, now = likedAt)
            }
            var replyAt = at
            if (random.nextDouble() < 0.5) {
                replyAt = minOf(now, replyAt + random.nextLong(MINUTE_MS, HOUR_MS))
                comments.insertComment(authorId, videoId, CreateCommentRequest(DemoContent.replyTexts.random(random), commentId), replyAt)
            }
            if (random.nextDouble() < 0.3) {
                val replier = others.filter { it != commenter }.random(random)
                replyAt = minOf(now, replyAt + random.nextLong(MINUTE_MS, HOUR_MS))
                val reply = "@${usernames.getValue(commenter)} ${DemoContent.replyTexts.random(random)}"
                comments.insertComment(replier, videoId, CreateCommentRequest(reply, commentId), replyAt)
            }
        }
    }

    private companion object {
        const val STATE_KEY = "demo_seed"
        const val RUNNING = "running"
        const val DONE = "done"
        const val FOLLOW_SEED = 2024
        const val MINUTE_MS = 60_000L
        const val HOUR_MS = 60 * MINUTE_MS
        const val DAY_MS = 24 * HOUR_MS
    }
}
