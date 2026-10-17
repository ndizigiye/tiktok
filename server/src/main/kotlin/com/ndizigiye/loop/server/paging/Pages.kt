package com.ndizigiye.loop.server.paging

import com.ndizigiye.loop.server.paging.Cursors.Key
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ColumnSet
import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.or

/** One page of ids (or rows) plus the cursor of the next page, before mapping to DTOs. */
data class RowPage<T>(val rows: List<T>, val nextCursor: String?)

/** Trims a result fetched with `limit + 1` rows to [limit] and derives the next cursor. */
fun <T> List<T>.toRowPage(limit: Int, cursorOf: (T) -> String): RowPage<T> =
    if (size > limit) {
        val page = take(limit)
        RowPage(page, cursorOf(page.last()))
    } else {
        RowPage(this, null)
    }

/** Keyset ordering on (sort key, id), both descending ("newest first") or both ascending. */
class KeysetOrder(
    val key: Column<Long>,
    val id: Column<EntityID<Long>>,
    private val descending: Boolean = true,
) {
    val orderBy: Array<Pair<Expression<*>, SortOrder>>
        get() {
            val order = if (descending) SortOrder.DESC else SortOrder.ASC
            return arrayOf(key to order, id to order)
        }

    /** Rows strictly after [cursor] in this order, or `null` for the first page. */
    fun after(cursor: Key?): Op<Boolean>? = cursor?.let {
        if (descending) {
            (key less it.key) or ((key eq it.key) and (id less it.id))
        } else {
            (key greater it.key) or ((key eq it.key) and (id greater it.id))
        }
    }

    /** The [id] values of the requested page of [source] rows matching [condition]. */
    fun ids(source: ColumnSet, condition: Op<Boolean>, page: PageRequest): RowPage<Long> {
        val after = after(Cursors.parseKey(page.cursor))
        val rows = source.select(key, id)
            .where { condition andIfPresent after }
            .orderBy(*orderBy)
            .limit(page.limit + 1)
            .map { Key(it[key], it[id].value) }
            .toRowPage(page.limit) { Cursors.key(it.key, it.id) }
        return RowPage(rows.rows.map(Key::id), rows.nextCursor)
    }
}

/** Offset pagination for rankings that change while scrolling (most liked, most viewed). */
fun Query.offsetIds(id: Column<EntityID<Long>>, page: PageRequest): RowPage<Long> {
    val offset = Cursors.parseOffset(page.cursor)
    val ids = limit(page.limit + 1).offset(offset.toLong()).map { it[id].value }
    return if (ids.size > page.limit) {
        RowPage(ids.take(page.limit), Cursors.offset(offset + page.limit))
    } else {
        RowPage(ids, null)
    }
}

/** `this AND other`, or just `this` when [other] is null (e.g. no cursor on the first page). */
infix fun Op<Boolean>.andIfPresent(other: Op<Boolean>?): Op<Boolean> = other?.let { this and it } ?: this
