package com.ndizigiye.loop.server

import com.ndizigiye.loop.server.db.PostgresSettings
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.util.Properties
import java.util.UUID

/**
 * PostgreSQL for the test suite. `./gradlew -p server testPostgres` runs every test against
 * PostgreSQL instead of SQLite: each test gets a new database, created through
 * `LOOP_TEST_DATABASE_URL` (a role that may create databases, e.g.
 * `postgresql://loop_test:secret@localhost:5432/postgres`) with the Supabase migrations
 * applied, and dropped afterwards. The plain `test` task always uses SQLite.
 */
object PostgresTests {
    /** Set by the `testPostgres` task. */
    val enabled: Boolean = System.getProperty("loop.test.postgres") == "true"

    private val admin: PostgresSettings by lazy {
        val url = System.getenv("LOOP_TEST_DATABASE_URL")?.takeIf { it.isNotBlank() }
            ?: error("testPostgres needs LOOP_TEST_DATABASE_URL: a PostgreSQL URL whose role may create databases.")
        PostgresSettings.parse(url)
    }

    /** `supabase/migrations/` (tests run in the server directory), in the order Supabase applies them. */
    val migrations: List<File> by lazy {
        File("supabase/migrations").listFiles { file -> file.name.endsWith(".sql") }.orEmpty().sortedBy { it.name }
            .also { check(it.isNotEmpty()) { "No migrations in ${File("supabase/migrations").absolutePath}" } }
    }

    /** A new, empty database on the test server; [migrate] applies the Supabase migrations to it. */
    fun createDatabase(migrate: Boolean = true): PostgresSettings {
        val name = "loop_test_" + UUID.randomUUID().toString().replace("-", "")
        connect(admin).use { it.createStatement().use { statement -> statement.execute("CREATE DATABASE $name") } }
        val settings = admin.withDatabase(name)
        if (migrate) {
            connect(settings).use { connection ->
                migrations.forEach { file -> connection.createStatement().use { it.execute(file.readText()) } }
            }
        }
        return settings
    }

    fun dropDatabase(settings: PostgresSettings) {
        connect(admin).use { connection ->
            connection.createStatement().use { it.execute("DROP DATABASE IF EXISTS ${settings.database} WITH (FORCE)") }
        }
    }

    /** A plain JDBC connection (auto-commit), outside the server's pool. */
    fun connect(settings: PostgresSettings): Connection =
        DriverManager.getConnection(
            settings.jdbcUrl,
            Properties().apply {
                settings.user?.let { setProperty("user", it) }
                settings.password?.let { setProperty("password", it) }
            },
        )

    /** Runs [block] with a fresh migrated database, dropping it afterwards. */
    inline fun <T> withDatabase(migrate: Boolean = true, block: (PostgresSettings) -> T): T {
        val settings = createDatabase(migrate)
        try {
            return block(settings)
        } finally {
            dropDatabase(settings)
        }
    }
}
