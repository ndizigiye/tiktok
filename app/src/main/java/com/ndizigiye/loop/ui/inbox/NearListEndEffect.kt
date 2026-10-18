package com.ndizigiye.loop.ui.inbox

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

private const val NEAR_END_THRESHOLD = 5

/** Calls [onNearEnd] when [state] is scrolled close to its end, and again each time the list grows while there. */
@Composable
internal fun NearListEndEffect(state: LazyListState, onNearEnd: () -> Unit) {
    val currentOnNearEnd by rememberUpdatedState(onNearEnd)
    LaunchedEffect(state) {
        snapshotFlow {
            val info = state.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            val total = info.totalItemsCount
            if (total > 0 && lastVisible >= total - NEAR_END_THRESHOLD) total else -1
        }
            .distinctUntilChanged()
            .filter { it >= 0 }
            .collect { currentOnNearEnd() }
    }
}
