package com.ndizigiye.loop.server.db

import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.upsert

/** Reads and writes [Meta] rows; call inside a transaction. */
object MetaStore {
    fun get(key: String): String? =
        Meta.selectAll().where { Meta.key eq key }.firstOrNull()?.get(Meta.value)

    fun put(key: String, value: String) {
        Meta.upsert {
            it[Meta.key] = key
            it[Meta.value] = value
        }
    }

    fun getOrPut(key: String, create: () -> String): String =
        get(key) ?: create().also { put(key, it) }
}
