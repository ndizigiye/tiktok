package com.ndizigiye.loop.ui.navigation

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import com.ndizigiye.loop.di.AppContainer

/** The process-wide [AppContainer], provided by LoopApp. */
val LocalAppContainer: ProvidableCompositionLocal<AppContainer> =
    staticCompositionLocalOf { error("LocalAppContainer not provided") }

/** App navigation, provided by LoopApp. */
val LocalNavigator: ProvidableCompositionLocal<LoopNavigator> =
    staticCompositionLocalOf { error("LocalNavigator not provided") }
