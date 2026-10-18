package com.ndizigiye.loop.e2e.harness

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.IdlingPolicies
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.ndizigiye.loop.BuildConfig
import com.ndizigiye.loop.LoopApplication
import com.ndizigiye.loop.MainActivity
import com.ndizigiye.loop.data.session.SessionStore
import com.ndizigiye.loop.di.AppContainer
import com.ndizigiye.loop.ui.navigation.MainTab
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.io.Closeable
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Runs the real app on a device against a live Loop server: the instrumentation argument
 * [BASE_URL_ARGUMENT], by default the app's own default (the emulator's alias for the host,
 * `http://10.0.2.2:8080/`; scripts/e2e-server.sh starts one with `--port 8080`). [server]
 * reaches the same server over plain HTTP for test accounts and server-side checks.
 *
 * The app's process, and with it AppContainer, outlives a test, so each test starts signed
 * out, on the home tab, pointed at the server. Activities, including those started from a
 * shell (deep links), are finished afterwards.
 *
 * Use it inside the Compose rule (a higher rule order), so the activity closes while the
 * Compose test environment is still up.
 */
class DeviceAppRule : TestRule {

    lateinit var server: LoopServerApi
        private set

    private val scenarios = mutableListOf<ActivityScenario<MainActivity>>()
    private val resources = mutableListOf<Closeable>()

    val application: LoopApplication get() = ApplicationProvider.getApplicationContext()

    val container: AppContainer get() = application.container

    /** Signs the app in as [account], as a successful log-in would; call before [launch]. */
    fun signIn(account: TestAccount) {
        container.sessionStore.save(account.token, account.user)
    }

    /** Closes [resource] after the test, once the app's activities are gone. */
    fun <T : Closeable> closeAfterTest(resource: T): T = resource.also { resources += it }

    /** Starts MainActivity, from the launcher or, with [deepLink], from a `loop://` link. */
    fun launch(deepLink: Uri? = null): ActivityScenario<MainActivity> {
        val intent = Intent(application, MainActivity::class.java)
        if (deepLink != null) intent.setAction(Intent.ACTION_VIEW).setData(deepLink)
        return ActivityScenario.launch<MainActivity>(intent).also { scenarios += it }
    }

    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            val baseUrl = InstrumentationRegistry.getArguments().getString(BASE_URL_ARGUMENT) ?: BuildConfig.DEFAULT_BASE_URL
            val url = SessionStore.normalizeBaseUrl(baseUrl).toHttpUrl()
            server = LoopServerApi(url)
            try {
                server.webPage("health")
            } catch (e: IOException) {
                throw AssertionError(
                    "No Loop server at $url. Start one on the host (scripts/e2e-server.sh start --port 8080) " +
                        "or pass -Pandroid.testInstrumentationRunnerArguments.$BASE_URL_ARGUMENT=<url>",
                    e,
                )
            }
            // A first frame can take seconds on a software-emulated device; Espresso's defaults
            // (26 s for idling resources such as Compose's) are made for real hardware.
            IdlingPolicies.setIdlingResourceTimeout(IDLING_TIMEOUT_MINUTES, TimeUnit.MINUTES)
            IdlingPolicies.setMasterPolicyTimeout(IDLING_TIMEOUT_MINUTES, TimeUnit.MINUTES)
            container.authRepository.logout()
            container.mainTab.value = MainTab.HOME
            container.sessionStore.setBaseUrl(url.toString())
            try {
                base.evaluate()
            } catch (failure: Throwable) {
                // The screen as the test failed, before the activities close.
                saveScreenshot("failed-${description.testClass.simpleName}-${description.methodName}")
                throw failure
            } finally {
                scenarios.forEach { it.close() }
                finishRemainingActivities()
                resources.forEach { it.close() }
            }
        }
    }

    /** Finishes activities the test did not launch itself, e.g. one a shell deep link started. */
    private fun finishRemainingActivities() {
        fun liveActivities(): List<Activity> = onMainThread {
            val monitor = ActivityLifecycleMonitorRegistry.getInstance()
            LIVE_STAGES.flatMap { monitor.getActivitiesInStage(it) }
        }
        val remaining = liveActivities()
        onMainThread { remaining.filterNot { it.isFinishing }.forEach { it.finish() } }
        val deadline = System.currentTimeMillis() + ACTIVITY_FINISH_TIMEOUT_MS
        while (liveActivities().isNotEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(100)
        }
    }

    companion object {
        /** Instrumentation argument naming the server, as the device reaches it. */
        const val BASE_URL_ARGUMENT = "loopBaseUrl"

        private const val ACTIVITY_FINISH_TIMEOUT_MS = 30_000L

        private val LIVE_STAGES = listOf(
            Stage.PRE_ON_CREATE, Stage.CREATED, Stage.STARTED, Stage.RESUMED, Stage.PAUSED, Stage.STOPPED, Stage.RESTARTED,
        )

        private const val IDLING_TIMEOUT_MINUTES = 3L
    }
}
