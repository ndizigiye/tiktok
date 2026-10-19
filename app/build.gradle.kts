import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/** Gradle property and test system property naming the Loop server of the end-to-end tests. */
val e2eBaseUrlProperty = "loop.e2e.baseUrl"

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.ndizigiye.loop"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ndizigiye.loop"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        // Android emulator alias for the host machine; changeable at runtime in Settings.
        buildConfigField("String", "DEFAULT_BASE_URL", "\"http://10.0.2.2:8080/\"")

        // Device end-to-end tests (src/androidTest) against a live Loop server, by default the
        // app's DEFAULT_BASE_URL; another with -Pandroid.testInstrumentationRunnerArguments.loopBaseUrl=<url>.
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    sourceSets {
        // The end-to-end harness that the JVM suite (src/test) and the device suite (src/androidTest)
        // share: the server client, the forwarding proxy and the feed helpers.
        getByName("test").java.srcDir("src/sharedTest/java")
        getByName("androidTest").java.srcDir("src/sharedTest/java")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            // Robolectric UI tests inflate the real resources (themes, Media3's PlayerView layout).
            isIncludeAndroidResources = true
            all { test ->
                test.maxHeapSize = "2g"
                // The end-to-end suite (com.ndizigiye.loop.e2e) runs only against a live server:
                // ./gradlew :app:testDebugUnitTest -Ploop.e2e.baseUrl=http://127.0.0.1:18080/
                // (scripts/e2e-server.sh starts one). Without the property those tests are skipped.
                val e2eBaseUrl = providers.gradleProperty(e2eBaseUrlProperty).orNull
                if (!e2eBaseUrl.isNullOrBlank()) {
                    test.systemProperty(e2eBaseUrlProperty, e2eBaseUrl)
                    // The server is not a task input: never skip an end-to-end run or take it from the cache.
                    test.outputs.upToDateWhen { false }
                    test.outputs.cacheIf { false }
                }
            }
        }
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/LICENSE.md",
                "/META-INF/LICENSE-notice.md",
                "/META-INF/NOTICE.md",
                "/META-INF/DEPENDENCIES",
            )
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":shared"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.savedstate)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.datasource.okhttp)
    implementation(libs.androidx.media3.database)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.video)
    implementation(libs.androidx.camera.view)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.uiautomator)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.okhttp.mockwebserver)
}
