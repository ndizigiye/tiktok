package com.ndizigiye.loop.server.db

import org.jetbrains.exposed.sql.insertIgnore
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

    /** The stored value, or [create]'s value stored now; concurrent callers all get the value stored first. */
    fun getOrPut(key: String, create: () -> String): String =
        get(key) ?: run {
            Meta.insertIgnore {
                it[Meta.key] = key
                it[Meta.value] = create()
            }
            checkNotNull(get(key))
        }
}
