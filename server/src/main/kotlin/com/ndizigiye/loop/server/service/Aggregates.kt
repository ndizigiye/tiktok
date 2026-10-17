package com.ndizigiye.loop.server.service

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ColumnSet
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.count

/*
 * Batched aggregate queries used to build DTOs: one query per count for a whole page
 * instead of one per row. Call inside a transaction.
 */

/** Number of rows per [key] value among [ids] (all keys when [ids] is null). */
internal fun countBy(
    source: ColumnSet,
    key: Column<EntityID<Long>>,
    ids: Collection<Long>?,
    filter: Op<Boolean>? = null,
): Map<Long, Long> {
    if (ids != null && ids.isEmpty()) return emptyMap()
    val count = key.count()
    val conditions = listOfNotNull(ids?.let { key inList it }, filter)
    val query = source.select(key, count)
    if (conditions.isNotEmpty()) query.where(conditions.reduce { a, b -> a and b })
    return query.groupBy(key).associate { it[key].value to it[count] }
}

/** The [key] values among [ids] that appear in a row owned by [userId] (e.g. "liked by me"). */
internal fun ownedBy(
    source: ColumnSet,
    key: Column<EntityID<Long>>,
    owner: Column<EntityID<Long>>,
    userId: Long?,
    ids: Collection<Long>,
): Set<Long> {
    if (userId == null || ids.isEmpty()) return emptySet()
    return source.select(key)
        .where { (owner eq userId) and (key inList ids) }
        .mapTo(HashSet()) { it[key].value }
}
