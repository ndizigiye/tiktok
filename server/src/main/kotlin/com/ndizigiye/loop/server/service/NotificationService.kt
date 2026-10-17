package com.ndizigiye.loop.server.service

import com.ndizigiye.loop.api.CountDto
import com.ndizigiye.loop.api.NotificationDto
import com.ndizigiye.loop.api.PageDto
import com.ndizigiye.loop.api.VideoSummaryDto
import com.ndizigiye.loop.server.db.LoopDatabase
import com.ndizigiye.loop.server.db.Notifications
import com.ndizigiye.loop.server.db.Videos
import com.ndizigiye.loop.server.media.MediaKind
import com.ndizigiye.loop.server.paging.Cursors
import com.ndizigiye.loop.server.paging.KeysetOrder
import com.ndizigiye.loop.server.paging.PageRequest
import com.ndizigiye.loop.server.paging.andIfPresent
import com.ndizigiye.loop.server.paging.toRowPage
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

class NotificationService(private val db: LoopDatabase) {
    private val order = KeysetOrder(Notifications.createdAt, Notifications.id)

    /** The recipient's inbox, newest first. */
    suspend fun list(userId: Long, page: PageRequest): PageDto<NotificationDto> = db.tx {
        val after = order.after(Cursors.parseKey(page.cursor))
        val rows = Notifications.join(Videos, JoinType.LEFT, Notifications.videoId, Videos.id)
            .select(Notifications.columns + Videos.thumbFile)
            .where { (Notifications.recipientId eq userId) andIfPresent after }
            .orderBy(*order.orderBy)
            .limit(page.limit + 1)
            .toList()
            .toRowPage(page.limit) { Cursors.key(it[Notifications.createdAt], it[Notifications.id].value) }
        val actors = DtoAssembler.users(rows.rows.map { it[Notifications.actorId].value }, viewerId = userId)
        val items = rows.rows.mapNotNull { row ->
            NotificationDto(
                id = row[Notifications.id].value,
                type = Notifier.typeOf(row[Notifications.type]) ?: return@mapNotNull null,
                actor = actors[row[Notifications.actorId].value] ?: return@mapNotNull null,
                video = row[Notifications.videoId]?.let { video ->
                    VideoSummaryDto(video.value, row[Videos.thumbFile]?.let(MediaKind.THUMBS::url))
                },
                text = row[Notifications.text],
                read = row[Notifications.read],
                createdAt = row[Notifications.createdAt],
            )
        }
        PageDto(items, rows.nextCursor)
    }

    suspend fun unreadCount(userId: Long): CountDto = db.tx {
        CountDto(
            Notifications.selectAll()
                .where { (Notifications.recipientId eq userId) and (Notifications.read eq false) }
                .count(),
        )
    }

    suspend fun markAllRead(userId: Long) {
        db.tx {
            Notifications.update({ (Notifications.recipientId eq userId) and (Notifications.read eq false) }) {
                it[read] = true
            }
        }
    }
}
