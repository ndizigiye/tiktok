// Standalone Gradle build for the Loop backend. Run from the repository root with
//   ./gradlew -p server run      (start the server)
//   ./gradlew -p server test     (tests)
// It shares the :shared module (API DTOs) with the Android build.
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "loop-server"
include(":shared")
project(":shared").projectDir = file("../shared")
