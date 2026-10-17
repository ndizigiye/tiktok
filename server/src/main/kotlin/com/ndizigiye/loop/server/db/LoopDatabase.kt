package com.ndizigiye.loop.server.db

import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.DatabaseConfig
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.sqlite.SQLiteConfig
import org.sqlite.SQLiteDataSource
import java.io.Closeable
import java.io.File
import java.sql.Connection
import java.util.concurrent.Executors

/**
 * SQLite database in WAL mode with foreign keys enforced.
 *
 * SQLite allows a single writer, and a deferred transaction that reads before it writes can
 * fail with "database is locked" when another connection commits in between. All
 * transactions therefore run on one dedicated thread, which serialises them without
 * blocking request threads.
 */
class LoopDatabase(file: File) : Closeable {
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "loop-db").apply { isDaemon = true }
    }
    private val dispatcher = executor.asCoroutineDispatcher()

    private val database: Database = Database.connect(
        datasource = SQLiteDataSource(
            SQLiteConfig().apply {
                enforceForeignKeys(true)
                setJournalMode(SQLiteConfig.JournalMode.WAL)
                setSynchronous(SQLiteConfig.SynchronousMode.NORMAL)
                setBusyTimeout(10_000)
            },
        ).apply { url = "jdbc:sqlite:${file.absolutePath}" },
        databaseConfig = DatabaseConfig {
            defaultIsolationLevel = Connection.TRANSACTION_SERIALIZABLE
        },
    )

    init {
        file.parentFile?.mkdirs()
        executor.submit { transaction(database) { SchemaUtils.create(*allTables) } }.get()
    }

    /** Runs [block] in a transaction on the database thread. */
    suspend fun <T> tx(block: Transaction.() -> T): T =
        withContext(dispatcher) { transaction(database) { block() } }

    override fun close() {
        executor.shutdown()
    }
}
