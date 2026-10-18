package com.ndizigiye.loop.e2e.harness

import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.ndizigiye.loop.LoopApplication
import com.ndizigiye.loop.MainActivity
import com.ndizigiye.loop.data.session.SessionStore
import com.ndizigiye.loop.di.AppContainer
import kotlinx.coroutines.cancel
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assume.assumeTrue
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.io.Closeable

/**
 * Runs the real app against the live Loop server named by the system property
 * [BASE_URL_PROPERTY] (wired from the Gradle property of the same name), and skips the
 * test when it is not set. Before the test it points the app's [SessionStore] at that
 * server; [launch] then starts [MainActivity]. [server] reaches the same server over
 * plain HTTP for test accounts, a second user and server-side checks. Activities and the
 * app's background work are closed afterwards.
 *
 * Use it inside the Compose rule (a higher rule order), so the activity closes while the
 * Compose test environment is still up.
 */
class LoopAppRule : TestRule {

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
            val baseUrl = System.getProperty(BASE_URL_PROPERTY).orEmpty()
            assumeTrue(
                "End-to-end test skipped: pass -P$BASE_URL_PROPERTY=<server URL> (see scripts/e2e-server.sh)",
                baseUrl.isNotBlank(),
            )
            val url = SessionStore.normalizeBaseUrl(baseUrl).toHttpUrl()
            server = LoopServerApi(url)
            container.sessionStore.setBaseUrl(url.toString())
            try {
                base.evaluate()
            } finally {
                scenarios.forEach { it.close() }
                resources.forEach { it.close() }
                container.applicationScope.cancel()
                container.okHttpClient.dispatcher.executorService.shutdown()
                container.okHttpClient.connectionPool.evictAll()
            }
        }
    }

    companion object {
        const val BASE_URL_PROPERTY = "loop.e2e.baseUrl"
    }
}
