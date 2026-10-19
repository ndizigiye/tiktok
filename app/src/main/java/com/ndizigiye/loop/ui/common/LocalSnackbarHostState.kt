package com.ndizigiye.loop.ui.common

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

/** App-wide snackbar host for one-off messages, provided by LoopApp. */
val LocalSnackbarHostState: ProvidableCompositionLocal<SnackbarHostState> =
    staticCompositionLocalOf { error("LocalSnackbarHostState not provided") }
