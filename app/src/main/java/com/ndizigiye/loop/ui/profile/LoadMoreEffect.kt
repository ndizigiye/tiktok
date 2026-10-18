package com.ndizigiye.loop.ui.profile

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

private const val LOAD_MORE_THRESHOLD = 6

/** Calls [onLoadMore] when the grid is scrolled near its end, and again each time it grows while there. */
@Composable
internal fun LoadMoreEffect(state: LazyGridState, onLoadMore: () -> Unit) {
    NearEndEffect(key = state, onNearEnd = onLoadMore) {
        val info = state.layoutInfo
        (info.visibleItemsInfo.lastOrNull()?.index ?: -1) to info.totalItemsCount
    }
}

/** Calls [onLoadMore] when the list is scrolled near its end, and again each time it grows while there. */
@Composable
internal fun LoadMoreEffect(state: LazyListState, onLoadMore: () -> Unit) {
    NearEndEffect(key = state, onNearEnd = onLoadMore) {
        val info = state.layoutInfo
        (info.visibleItemsInfo.lastOrNull()?.index ?: -1) to info.totalItemsCount
    }
}

/** [position] returns (index of the last visible item, total item count). */
@Composable
private fun NearEndEffect(key: Any, onNearEnd: () -> Unit, position: () -> Pair<Int, Int>) {
    val currentOnNearEnd by rememberUpdatedState(onNearEnd)
    LaunchedEffect(key) {
        snapshotFlow {
            val (lastVisible, total) = position()
            if (total > 0 && lastVisible >= total - LOAD_MORE_THRESHOLD) total else -1
        }
            .distinctUntilChanged()
            .filter { it >= 0 }
            .collect { currentOnNearEnd() }
    }
}
