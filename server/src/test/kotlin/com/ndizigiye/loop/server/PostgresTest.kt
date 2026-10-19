package com.ndizigiye.loop.server

import com.ndizigiye.loop.server.db.LoopDatabase
import com.ndizigiye.loop.server.db.PostgresSchema
import com.ndizigiye.loop.server.db.PostgresSettings
import com.ndizigiye.loop.server.db.allTables
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.SchemaUtils
import org.junit.Assume
import org.junit.Before
import java.net.URLEncoder
import java.sql.Connection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** PostgreSQL-only checks. They run under `testPostgres`; the rest of the suite runs on both backends. */
class PostgresTest {
    @Before
    fun requirePostgres() = Assume.assumeTrue("runs under testPostgres", PostgresTests.enabled)

    private fun Connection.strings(sql: String): List<String> = createStatement().use { statement ->
        statement.executeQuery(sql).use { rows -> buildList { while (rows.next()) add(rows.getString(1)) } }
    }

    @Test
    fun `rows are stored in the loop schema`() = loopTest {
        val ann = register("ann")
        val video = postVideo(ann, "hello #world")
        comment(ann, video.id, "first")
        assertEquals(1, queryLong("SELECT count(*) FROM loop.users"))
        assertEquals(1, queryLong("SELECT count(*) FROM loop.videos"))
        assertEquals(1, queryLong("SELECT count(*) FROM loop.hashtags"))
        assertEquals(1, queryLong("SELECT count(*) FROM loop.comments"))
        assertEquals(1, queryLong("SELECT count(*) FROM loop.meta"), "the token key")
        assertEquals(0, queryLong("SELECT count(*) FROM pg_tables WHERE schemaname = 'public'"))
    }

    @Test
    fun `the migration creates exactly the Exposed schema, locked down`() = PostgresTests.withDatabase { settings ->
        LoopDatabase.postgres(settings).use { db ->
            runBlocking {
                db.tx { assertEquals(emptyList(), SchemaUtils.statementsRequiredToActualizeScheme(*allTables, withLogs = false)) }
            }
        }
        PostgresTests.connect(settings).use { connection ->
            assertEquals(
                allTables.map { it.tableName }.sorted(),
                connection.strings("SELECT tablename FROM pg_tables WHERE schemaname = 'loop' ORDER BY tablename"),
            )
            assertEquals(
                emptyList(),
                connection.strings(
                    "SELECT c.relname FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace " +
                        "WHERE n.nspname = 'loop' AND c.relkind = 'r' AND NOT c.relrowsecurity",
                ),
                "row level security on every table",
            )
            assertEquals(emptyList(), connection.strings("SELECT policyname FROM pg_policies WHERE schemaname = 'loop'"))
            assertEquals(
                emptyList(),
                connection.strings(
                    "SELECT c.relname FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace, aclexplode(c.relacl) a " +
                        "WHERE n.nspname = 'loop' AND a.grantee = 0 " +
                        "UNION ALL SELECT n.nspname FROM pg_namespace n, aclexplode(n.nspacl) a WHERE n.nspname = 'loop' AND a.grantee = 0",
                ),
                "nothing granted to PUBLIC",
            )
        }
    }

    @Test
    fun `startup fails with instructions and creates nothing without the migration`() =
        PostgresTests.withDatabase(migrate = false) { settings ->
            val error = assertFailsWith<IllegalStateException> { LoopDatabase.postgres(settings) }
            assertTrue(PostgresSchema.MIGRATION in error.message.orEmpty(), error.message)
            assertTrue("schema \"loop\" does not exist" in error.message.orEmpty(), error.message)
            PostgresTests.connect(settings).use { connection ->
                assertEquals(
                    emptyList(),
                    connection.strings("SELECT tablename FROM pg_tables WHERE schemaname NOT IN ('pg_catalog', 'information_schema')"),
                )
            }
        }

    @Test
    fun `startup fails when the schema is incomplete`() = PostgresTests.withDatabase { settings ->
        PostgresTests.connect(settings).use { it.createStatement().use { statement -> statement.execute("DROP TABLE loop.notifications") } }
        val error = assertFailsWith<IllegalStateException> { LoopDatabase.postgres(settings) }
        assertTrue("notifications" in error.message.orEmpty(), error.message)
        assertTrue(PostgresSchema.MIGRATION in error.message.orEmpty(), error.message)
        PostgresTests.connect(settings).use { connection ->
            assertEquals(emptyList(), connection.strings("SELECT tablename FROM pg_tables WHERE tablename = 'notifications'"))
        }
    }

    @Test
    fun `connection errors never contain the password`() = PostgresTests.withDatabase { settings ->
        val password = "Wr0ng+p@ss/w0rd"
        val wrong = PostgresSettings.parse(
            "postgresql://${settings.user}:${URLEncoder.encode(password, Charsets.UTF_8)}@${settings.hosts}/${settings.database}",
        )
        val error = assertFailsWith<Exception> { LoopDatabase.postgres(wrong) }
        generateSequence<Throwable>(error) { it.cause }.forEach { cause ->
            assertFalse(password in cause.toString(), cause.toString())
        }
    }
}
