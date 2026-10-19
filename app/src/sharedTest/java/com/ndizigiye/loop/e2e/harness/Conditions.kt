package com.ndizigiye.loop.e2e.harness

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import kotlin.time.Duration

// Conditions shared by the JVM and the device suites. Both build on `waitFor` and
// `ServerTimeout` from the suite's own Waits.kt: Robolectric moves its clocks by hand, a
// device runs in real time.

/** Waits until a node matching [matcher] exists (in any window, e.g. also in bottom sheets and dialogs). */
fun ComposeTestRule.waitUntilExists(matcher: SemanticsMatcher, timeout: Duration = ServerTimeout) {
    waitFor("a node ${matcher.description} exists", timeout) { exists(matcher) }
}

fun ComposeTestRule.waitUntilGone(matcher: SemanticsMatcher, timeout: Duration = ServerTimeout) {
    waitFor("no node ${matcher.description} exists", timeout) { !exists(matcher) }
}

/** Whether a node matching [matcher] exists in the merged semantics tree of any window. */
fun ComposeTestRule.exists(matcher: SemanticsMatcher): Boolean =
    onAllNodes(matcher).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()

fun ComposeTestRule.waitForText(text: String, substring: Boolean = false, timeout: Duration = ServerTimeout) =
    waitUntilExists(hasText(text, substring = substring), timeout)

/**
 * Retries [block] until it stops throwing an [AssertionError], e.g. to check server-side
 * state after a UI action whose request is still in flight; returns its result. The last
 * failure is rethrown when [timeout] runs out.
 */
fun <T> ComposeTestRule.eventually(timeout: Duration = ServerTimeout, block: () -> T): T {
    var result: Result<T>? = null
    var lastFailure: AssertionError? = null
    try {
        waitFor("the assertion passes", timeout) {
            try {
                result = Result.success(block())
                true
            } catch (e: AssertionError) {
                lastFailure = e
                false
            }
        }
    } catch (timedOut: AssertionError) {
        throw lastFailure ?: timedOut
    }
    return result!!.getOrThrow()
}
