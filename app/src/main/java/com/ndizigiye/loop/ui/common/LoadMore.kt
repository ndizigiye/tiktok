package com.ndizigiye.loop.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ndizigiye.loop.ui.theme.LoopColors
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

private const val LOAD_MORE_THRESHOLD = 6

/**
 * Calls [onLoadMore] when [state] is scrolled near its end, and again each time the
 * grid grows while still there. Pass [enabled] false while the last page load failed:
 * a footer that comes and goes with the failure changes the item count, which would
 * otherwise request the page again and again (e.g. while offline). The user retries
 * explicitly instead, e.g. from a [LoadMoreErrorFooter].
 */
@Composable
fun LoadMoreEffect(state: LazyGridState, enabled: Boolean = true, onLoadMore: () -> Unit) {
    NearEndEffect(key = state, enabled = enabled, onNearEnd = onLoadMore) {
        val info = state.layoutInfo
        (info.visibleItemsInfo.lastOrNull()?.index ?: -1) to info.totalItemsCount
    }
}

/** [LoadMoreEffect] for a lazy list. */
@Composable
fun LoadMoreEffect(state: LazyListState, enabled: Boolean = true, onLoadMore: () -> Unit) {
    NearEndEffect(key = state, enabled = enabled, onNearEnd = onLoadMore) {
        val info = state.layoutInfo
        (info.visibleItemsInfo.lastOrNull()?.index ?: -1) to info.totalItemsCount
    }
}

/** [position] returns (index of the last visible item, total item count). */
@Composable
private fun NearEndEffect(key: Any, enabled: Boolean, onNearEnd: () -> Unit, position: () -> Pair<Int, Int>) {
    val currentOnNearEnd by rememberUpdatedState(onNearEnd)
    val currentEnabled by rememberUpdatedState(enabled)
    LaunchedEffect(key) {
        snapshotFlow {
            val (lastVisible, total) = position()
            if (currentEnabled && total > 0 && lastVisible >= total - LOAD_MORE_THRESHOLD) total else -1
        }
            .distinctUntilChanged()
            .filter { it >= 0 }
            .collect { currentOnNearEnd() }
    }
}

/** Small centred spinner at the end of a list or grid whose next page is loading. */
@Composable
fun LoadingMoreFooter(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 2.dp,
        )
    }
}

/** End of a list or grid whose next page failed to load: the error and a retry button. */
@Composable
fun LoadMoreErrorFooter(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = LoopColors.TextSecondary,
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = onRetry) { Text("Try again") }
    }
}
