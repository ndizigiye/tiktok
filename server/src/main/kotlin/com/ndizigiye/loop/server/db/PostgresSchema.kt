package com.ndizigiye.loop.server.db

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.exists
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction

/**
 * The PostgreSQL schema of [allTables]. The Supabase migrations create it: [MIGRATION] from the
 * DDL printed by [main], later ones with the differences since. The server only checks it at startup.
 */
object PostgresSchema {
    /** Path of the first migration, which creates the schema, relative to the repository root. */
    const val MIGRATION = "server/supabase/migrations/20261018090945_loop_schema.sql"

    /**
     * The generated part of the migration, for the current transaction's dialect: Exposed's
     * `CREATE` statements for every table, index and foreign key, then row level security
     * (without policies) on every table.
     */
    fun statements(): List<String> {
        val transaction = TransactionManager.current()
        return SchemaUtils.createStatements(*allTables) +
            allTables.map { "ALTER TABLE ${transaction.identity(it)} ENABLE ROW LEVEL SECURITY" }
    }

    /**
     * Fails with instructions unless the current transaction's schema is [PostgresSettings.SCHEMA] and
     * has every table, column, index and foreign key of [allTables].
     */
    fun verify() {
        val schema = TransactionManager.current().exec("SELECT current_schema()") { rs -> if (rs.next()) rs.getString(1) else null }
        val problem = if (schema != PostgresSettings.SCHEMA) {
            "schema \"${PostgresSettings.SCHEMA}\" does not exist"
        } else {
            val changes = SchemaUtils.statementsRequiredToActualizeScheme(*allTables, withLogs = false)
            if (changes.isEmpty()) return
            val missing = allTables.filterNot { it.exists() }.map { it.tableName }
            if (missing.isNotEmpty()) {
                "missing tables ${missing.joinToString()}"
            } else {
                "it differs from the server's tables (${changes.size} change(s) needed, first: ${changes.first().take(160)})"
            }
        }
        error(
            "The PostgreSQL database is not ready for Loop: $problem. Apply $MIGRATION and any later migrations " +
                "in server/supabase/migrations (Supabase SQL editor, `supabase db push` or psql; see server/README.md), " +
                "then start the server again. The server never creates tables in PostgreSQL.",
        )
    }
}

/**
 * Prints [PostgresSchema.statements] for the Supabase migration: `./gradlew -p server printPostgresSchema`.
 * Exposed reads dialect details from a live connection, so this needs `LOOP_DATABASE_URL` pointing at any
 * PostgreSQL server (a local one is fine); nothing is written to it.
 */
fun main() {
    val url = System.getenv("LOOP_DATABASE_URL")?.takeIf { it.isNotBlank() }
        ?: error("Set LOOP_DATABASE_URL to any PostgreSQL database; it is only used to read dialect details.")
    val settings = PostgresSettings.parse(url)
    val database = Database.connect(
        url = settings.jdbcUrl,
        driver = "org.postgresql.Driver",
        user = settings.user.orEmpty(),
        password = settings.password.orEmpty(),
    )
    transaction(database) {
        PostgresSchema.statements().forEach { println("$it;") }
        rollback()
    }
}
