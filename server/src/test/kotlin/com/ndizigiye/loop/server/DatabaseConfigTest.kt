package com.ndizigiye.loop.server

import com.ndizigiye.loop.server.db.PostgresSchema
import com.ndizigiye.loop.server.db.PostgresSettings
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DatabaseConfigTest {
    @Test
    fun `supabase connection strings become jdbc urls without credentials`() {
        val settings = PostgresSettings.parse(
            "postgresql://postgres.abcdefgh:p%40ss%2Fw%3Ard%2B1@aws-0-eu-central-1.pooler.supabase.com:5432/postgres",
        )
        assertEquals("postgres.abcdefgh", settings.user)
        assertEquals("p@ss/w:rd+1", settings.password, "percent-decoded, '+' kept")
        assertEquals(
            "jdbc:postgresql://aws-0-eu-central-1.pooler.supabase.com:5432/postgres?sslmode=require&currentSchema=loop",
            settings.jdbcUrl,
        )
        assertEquals(PostgresSettings.DEFAULT_POOL_SIZE, settings.poolSize)
    }

    @Test
    fun `unencoded special characters in the password still work`() {
        val settings = PostgresSettings.parse("postgres://postgres:p@ss/w?rd#1:x@db.example.com/postgres")
        assertEquals("postgres", settings.user)
        assertEquals("p@ss/w?rd#1:x", settings.password)
        assertEquals("db.example.com", settings.hosts)
        assertEquals("postgres", settings.database)
        assertEquals("postgres", PostgresSettings.parse("postgresql://u:p@db.example.com").database, "default database")
    }

    @Test
    fun `jdbc urls keep their options and lose their credentials`() {
        val settings = PostgresSettings.parse(
            "jdbc:postgresql://db.example.com:6543/app?user=svc&password=a%2Bb+c&sslmode=verify-full&ApplicationName=loop",
        )
        assertEquals("svc", settings.user)
        assertEquals("a+b c", settings.password, "decoded like the driver decodes JDBC URLs")
        assertEquals(
            "jdbc:postgresql://db.example.com:6543/app?sslmode=verify-full&ApplicationName=loop&currentSchema=loop",
            settings.jdbcUrl,
        )
    }

    @Test
    fun `remote hosts always use TLS and every connection uses the loop schema`() {
        fun parameters(url: String) = PostgresSettings.parse(url).jdbcUrl.substringAfter('?')
        assertEquals("sslmode=require&currentSchema=loop", parameters("postgresql://u:p@db.example.com/postgres?sslmode=disable"))
        assertEquals("sslmode=require&currentSchema=loop", parameters("postgresql://u:p@db.example.com/postgres?sslmode=prefer"))
        assertEquals("sslmode=verify-ca&currentSchema=loop", parameters("postgresql://u:p@db.example.com/x?sslmode=verify-ca"))
        assertEquals("currentSchema=loop&sslmode=require", parameters("postgresql://u:p@db.example.com/x?currentSchema=public"))
        assertEquals("currentSchema=loop", parameters("postgresql://u:p@localhost:5432/postgres"), "local: driver default")
        assertEquals("sslmode=disable&currentSchema=loop", parameters("postgresql://u:p@127.0.0.1/postgres?sslmode=disable"))
        val ipv6 = PostgresSettings.parse("postgresql://u:p@[::1]:5433/postgres")
        assertEquals("jdbc:postgresql://[::1]:5433/postgres?currentSchema=loop", ipv6.jdbcUrl)
        assertEquals(
            "sslmode=require&currentSchema=loop",
            parameters("postgresql://u:p@localhost:5432,db.example.com:5432/postgres"),
            "local only when every host is",
        )
    }

    @Test
    fun `secrets never appear in descriptions or errors`() {
        val secret = "s3cr3t-Pa55"
        val settings = PostgresSettings.parse("postgresql://postgres:$secret@db.example.com:5432/postgres")
        val config = ServerConfig(jwtSecret = "jwt-$secret", postgres = settings)
        listOf(settings.toString(), settings.jdbcUrl, settings.describe(), config.toString()).forEach { text ->
            assertFalse(secret in text, text)
        }
        assertTrue("<redacted>" in settings.toString())
        val invalid = listOf(
            "mysql://root:$secret@db/x",
            "postgresql://u:$secret@/postgres",
            "jdbc:postgresql://db/x?password=$secret&bad=%zz",
        )
        for (url in invalid) {
            val error = assertFailsWith<IllegalArgumentException> { PostgresSettings.parse(url) }
            assertFalse(secret in error.message.orEmpty(), error.message)
        }
    }

    @Test
    fun `the environment chooses the database`() {
        assertNull(ServerConfig.fromEnv(emptyMap()).postgres, "SQLite without LOOP_DATABASE_URL")
        assertNull(ServerConfig.fromEnv(mapOf("LOOP_DATABASE_URL" to "  ")).postgres)
        val url = "postgresql://u:p@db.example.com:5432/postgres"
        assertEquals(PostgresSettings.DEFAULT_POOL_SIZE, ServerConfig.fromEnv(mapOf("LOOP_DATABASE_URL" to url)).postgres?.poolSize)
        val sized = ServerConfig.fromEnv(mapOf("LOOP_DATABASE_URL" to url, "LOOP_DATABASE_POOL_SIZE" to "3"))
        assertEquals(3, sized.postgres?.poolSize)
        for (bad in listOf("0", "51", "many")) {
            assertFailsWith<IllegalStateException> {
                ServerConfig.fromEnv(mapOf("LOOP_DATABASE_URL" to url, "LOOP_DATABASE_POOL_SIZE" to bad))
            }
        }
    }

    @Test
    fun `the migration named in startup errors exists`() {
        // Tests run in server/; the constant is relative to the repository root.
        val migration = File("..", PostgresSchema.MIGRATION)
        assertTrue(migration.isFile, migration.path)
        assertTrue(migration.name in PostgresTests.migrations.map { it.name })
    }
}
