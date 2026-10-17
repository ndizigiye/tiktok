package com.ndizigiye.loop.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.rememberNavController
import com.ndizigiye.loop.LoopApplication
import com.ndizigiye.loop.ui.common.LocalSnackbarHostState
import com.ndizigiye.loop.ui.main.MainBottomBarHeight
import com.ndizigiye.loop.ui.theme.LoopTheme

/** Root composable: theme, composition locals, navigation and the app-wide snackbar host. */
@Composable
fun LoopApp() {
    val context = LocalContext.current
    val container = remember(context) { (context.applicationContext as LoopApplication).container }
    val navController = rememberNavController()
    val navigator = remember(navController, container) { LoopNavigator(navController, container) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(container) {
        if (container.sessionStore.isLoggedIn) container.authRepository.refreshMe()
    }

    LoopTheme {
        CompositionLocalProvider(
            LocalAppContainer provides container,
            LocalNavigator provides navigator,
            LocalSnackbarHostState provides snackbarHostState,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
            ) {
                LoopNavHost(navController = navController, modifier = Modifier.fillMaxSize())
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(bottom = MainBottomBarHeight),
                )
            }
        }
    }
}
