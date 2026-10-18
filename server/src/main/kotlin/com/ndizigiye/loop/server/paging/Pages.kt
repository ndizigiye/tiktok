package com.ndizigiye.loop.server.paging

import com.ndizigiye.loop.server.paging.Cursors.Key
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ColumnSet
import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.ExpressionWithColumnType
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
    fun after(cursor: Key?): Op<Boolean>? = cursor?.let(::strictlyAfter)

    /** Rows strictly after [position] in this order. */
    fun strictlyAfter(position: Key): Op<Boolean> =
        if (descending) {
            (key less position.key) or ((key eq position.key) and (id less position.id))
        } else {
            (key greater position.key) or ((key eq position.key) and (id greater position.id))
        }

    /** The row at [position] and every row after it in this order. */
    fun atOrAfter(position: Key): Op<Boolean> = (id eq position.id) or strictlyAfter(position)

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

/**
 * Keyset ordering on (score, sort key, id), all descending ("most liked, then newest"), where
 * [score] is an aggregate of a grouped query, e.g. a like count. The cursor holds the last row's
 * three values, so rows deleted between pages never shift the next page. A row whose score
 * changes meanwhile can move across the cursor and be served twice or not at all.
 */
class RankedOrder(
    val score: ExpressionWithColumnType<Long>,
    private val key: Column<Long>,
    private val id: Column<EntityID<Long>>,
) {
    private val ties = KeysetOrder(key, id)

    val orderBy: Array<Pair<Expression<*>, SortOrder>>
        get() = arrayOf(score to SortOrder.DESC, *ties.orderBy)

    /** Rows strictly after [cursor] in this order, or `null` for the first page; a `HAVING` condition. */
    fun after(cursor: Cursors.Ranked?): Op<Boolean>? = cursor?.let {
        (score less it.score) or ((score eq it.score) and ties.strictlyAfter(Key(it.key, it.id)))
    }

    /**
     * The [id] values of the requested page of [query]: a query grouped by [id] that selects
     * [score], the sort key and [id], without ordering or limit (both are added here).
     */
    fun ids(query: Query, page: PageRequest): RowPage<Long> {
        after(Cursors.parseRanked(page.cursor))?.let { condition -> query.having { condition } }
        val rows = query.orderBy(*orderBy)
            .limit(page.limit + 1)
            .map { Cursors.Ranked(it[score], it[key], it[id].value) }
            .toRowPage(page.limit, Cursors::ranked)
        return RowPage(rows.rows.map(Cursors.Ranked::id), rows.nextCursor)
    }
}

/** Offset pagination for rankings that change while scrolling (most viewed). */
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
