package com.ndizigiye.loop.e2e.harness

import android.os.Looper
import androidx.compose.ui.test.junit4.ComposeTestRule
import org.robolectric.Shadows.shadowOf
import java.time.Duration as JavaDuration
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** How long the app may take to show the outcome of requests to the live server. */
val ServerTimeout: Duration = 20.seconds

private val PollInterval = 50.milliseconds

/**
 * Waits until [condition] holds while the app keeps working: each round moves both test
 * clocks on by about the real time that passed and runs what became due, i.e. the main
 * looper under Robolectric's clock (results of network calls posted from OkHttp threads,
 * delayed coroutines such as the search debounce) and Compose under its own clock
 * (animations, timeouts of snackbars), and then checks [condition].
 */
fun ComposeTestRule.waitFor(description: String, timeout: Duration = ServerTimeout, condition: () -> Boolean) {
    val start = TimeSource.Monotonic.markNow()
    while (true) {
        shadowOf(Looper.getMainLooper()).idleFor(JavaDuration.ofMillis(PollInterval.inWholeMilliseconds))
        mainClock.advanceTimeBy(PollInterval.inWholeMilliseconds)
        waitForIdle()
        if (condition()) return
        if (start.elapsedNow() > timeout) throw AssertionError("Timed out after $timeout waiting until $description")
        Thread.sleep(PollInterval.inWholeMilliseconds)
    }
}
