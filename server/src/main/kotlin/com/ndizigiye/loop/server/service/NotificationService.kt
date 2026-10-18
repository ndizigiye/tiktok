package com.ndizigiye.loop.server.service

import com.ndizigiye.loop.api.CountDto
import com.ndizigiye.loop.api.NotificationDto
import com.ndizigiye.loop.api.PageDto
import com.ndizigiye.loop.api.VideoSummaryDto
import com.ndizigiye.loop.server.ApiException
import com.ndizigiye.loop.server.db.LoopDatabase
import com.ndizigiye.loop.server.db.Notifications
import com.ndizigiye.loop.server.db.Videos
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

class NotificationService(private val db: LoopDatabase, private val dto: DtoAssembler) {
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
        val actors = dto.users(rows.rows.map { it[Notifications.actorId].value }, viewerId = userId)
        val items = rows.rows.mapNotNull { row ->
            NotificationDto(
                id = row[Notifications.id].value,
                type = Notifier.typeOf(row[Notifications.type]) ?: return@mapNotNull null,
                actor = actors[row[Notifications.actorId].value] ?: return@mapNotNull null,
                video = row[Notifications.videoId]?.let { video ->
                    VideoSummaryDto(video.value, row[Videos.thumbFile]?.let(dto::thumbnailUrl))
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

    /**
     * Marks the recipient's notifications as read: all of them, or with [upTo] only that one and
     * those after it in the inbox order (older ones), so notifications that arrived after the
     * client loaded its list stay unread. `404` when [upTo] is not one of the recipient's.
     */
    suspend fun markRead(userId: Long, upTo: Long?) {
        db.tx {
            val mine = Notifications.recipientId eq userId
            val upToPosition = upTo?.let { id ->
                val createdAt = Notifications.select(Notifications.createdAt)
                    .where { mine and (Notifications.id eq id) }
                    .firstOrNull()
                    ?.get(Notifications.createdAt)
                    ?: throw ApiException.notFound("Notification not found.")
                order.atOrAfter(Cursors.Key(createdAt, id))
            }
            Notifications.update({ (mine and (Notifications.read eq false)) andIfPresent upToPosition }) {
                it[read] = true
            }
        }
    }
}
