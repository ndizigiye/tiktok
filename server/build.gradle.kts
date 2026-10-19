plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

group = "com.ndizigiye.loop"
version = "0.1.0"

application {
    mainClass.set("com.ndizigiye.loop.server.ApplicationKt")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":shared"))

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.auth.jwt)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.partial.content)
    implementation(libs.ktor.server.auto.head.response)
    implementation(libs.ktor.server.default.headers)

    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.sqlite.jdbc)
    implementation(libs.postgresql)
    implementation(libs.hikaricp)
    implementation(libs.logback.classic)

    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.content.negotiation)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
}

tasks.withType<Test>().configureEach {
    useJUnit()
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

// The same suite on PostgreSQL. LOOP_TEST_DATABASE_URL names a role that may create databases,
// e.g. postgresql://loop_test:<password>@localhost:5432/postgres; every test gets its own
// database with supabase/migrations applied, dropped afterwards.
tasks.register<Test>("testPostgres") {
    description = "Runs the server tests against PostgreSQL (needs LOOP_TEST_DATABASE_URL)."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    systemProperty("loop.test.postgres", "true")
    // The database is not a task input, so never skip or reuse a run.
    outputs.upToDateWhen { false }
    outputs.cacheIf { false }
    doFirst {
        check(!System.getenv("LOOP_TEST_DATABASE_URL").isNullOrBlank()) {
            "testPostgres needs LOOP_TEST_DATABASE_URL: a PostgreSQL URL whose role may create databases."
        }
    }
    shouldRunAfter(tasks.test)
}

tasks.named<JavaExec>("run") {
    workingDir = projectDir
}

// Prints the PostgreSQL DDL of the Exposed tables, the body of supabase/migrations/*_loop_schema.sql.
// Needs LOOP_DATABASE_URL pointing at any PostgreSQL server; it only reads dialect details.
tasks.register<JavaExec>("printPostgresSchema") {
    group = "application"
    description = "Prints the PostgreSQL DDL for the Supabase migration."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.ndizigiye.loop.server.db.PostgresSchemaKt")
}
