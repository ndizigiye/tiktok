package com.ndizigiye.loop.server.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.DatabaseConfig
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.sqlite.SQLiteConfig
import org.sqlite.SQLiteDataSource
import java.io.Closeable
import java.io.File
import java.sql.Connection
import java.util.concurrent.Executors

/**
 * The server's database: SQLite in the data directory, or PostgreSQL (Supabase) when
 * `LOOP_DATABASE_URL` is set. Services only see [tx], so the same queries run on both.
 */
class LoopDatabase private constructor(
    private val database: Database,
    private val dispatcher: CoroutineDispatcher,
    /** Credential-free description for the startup log. */
    val description: String,
    private val release: () -> Unit,
) : Closeable {
    /** Runs [block] in a transaction, off the caller's thread. */
    suspend fun <T> tx(block: Transaction.() -> T): T =
        withContext(dispatcher) { transaction(database) { block() } }

    override fun close() {
        TransactionManager.closeAndUnregister(database)
        release()
    }

    companion object {
        /**
         * SQLite in WAL mode with foreign keys enforced; creates missing tables.
         *
         * SQLite allows a single writer, and a deferred transaction that reads before it writes
         * can fail with "database is locked" when another connection commits in between. All
         * transactions therefore run on one dedicated thread, which serialises them without
         * blocking request threads.
         */
        fun sqlite(file: File): LoopDatabase {
            file.parentFile?.mkdirs()
            val executor = Executors.newSingleThreadExecutor { runnable ->
                Thread(runnable, "loop-db").apply { isDaemon = true }
            }
            val database = Database.connect(
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
            executor.submit { transaction(database) { SchemaUtils.create(*allTables) } }.get()
            return LoopDatabase(database, executor.asCoroutineDispatcher(), "SQLite ${file.absolutePath}") {
                executor.shutdown()
            }
        }

        /**
         * PostgreSQL through a small connection pool, sized for Supabase's session pooler, where
         * every open connection holds a server connection. Transactions run concurrently, up to
         * one per pooled connection, at READ COMMITTED (the PostgreSQL default); conflicting
         * writes rely on constraints and single atomic statements, and Exposed retries a failed
         * transaction (three attempts), e.g. after a deadlock.
         *
         * The server never creates or alters tables here: they come from the Supabase migration
         * ([PostgresSchema.MIGRATION]). Startup fails with instructions when they are missing.
         */
        fun postgres(settings: PostgresSettings): LoopDatabase {
            val pool = HikariDataSource(
                HikariConfig().apply {
                    poolName = "loop-db"
                    jdbcUrl = settings.jdbcUrl
                    username = settings.user
                    password = settings.password
                    maximumPoolSize = settings.poolSize
                    minimumIdle = 1
                    // Set once per connection. Exposed reads it from the pool instead of setting an
                    // isolation level on every transaction, which would cost a round trip each time.
                    transactionIsolation = "TRANSACTION_READ_COMMITTED"
                    // currentSchema in the URL is a startup parameter, which a connection pooler may not
                    // forward; setting the search path on each new connection works either way.
                    connectionInitSql = "SET search_path TO ${PostgresSettings.SCHEMA}"
                },
            )
            val database = Database.connect(pool)
            try {
                transaction(database) { PostgresSchema.verify() }
            } catch (e: Throwable) {
                TransactionManager.closeAndUnregister(database)
                pool.close()
                throw e
            }
            return LoopDatabase(database, Dispatchers.IO.limitedParallelism(settings.poolSize), settings.describe(), pool::close)
        }
    }
}
