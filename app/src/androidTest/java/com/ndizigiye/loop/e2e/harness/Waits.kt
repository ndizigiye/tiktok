package com.ndizigiye.loop.e2e.harness

import androidx.compose.ui.test.junit4.ComposeTestRule
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * How long the app may take to show the outcome of requests to the live server. Generous,
 * because the suite also runs on software-emulated devices where everything is slow.
 */
val ServerTimeout: Duration = 60.seconds

private val PollInterval = 200.milliseconds

/**
 * Waits until [condition] holds while the app keeps working. Each round moves Compose's test
 * clock on by the poll interval, since timeouts that run on it (a tap waits out the
 * double-tap window, snackbars time out) are only due when it moves, and then checks
 * [condition] once the app is idle.
 */
fun ComposeTestRule.waitFor(description: String, timeout: Duration = ServerTimeout, condition: () -> Boolean) {
    val start = TimeSource.Monotonic.markNow()
    while (true) {
        mainClock.advanceTimeBy(PollInterval.inWholeMilliseconds)
        waitForIdle()
        if (condition()) return
        if (start.elapsedNow() > timeout) throw AssertionError("Timed out after $timeout waiting until $description")
        Thread.sleep(PollInterval.inWholeMilliseconds)
    }
}
