package com.ndizigiye.loop.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ndizigiye.loop.ui.common.LocalSnackbarHostState
import com.ndizigiye.loop.ui.common.LoopTopBar
import com.ndizigiye.loop.ui.navigation.LocalAppContainer
import com.ndizigiye.loop.ui.navigation.LocalNavigator
import com.ndizigiye.loop.ui.navigation.MainTab
import kotlinx.coroutines.launch

/** Account, server URL, video cache and about. */
@Composable
fun SettingsScreen() {
    val container = LocalAppContainer.current
    val navigator = LocalNavigator.current
    val snackbarHostState = LocalSnackbarHostState.current
    val viewModel: SettingsViewModel = viewModel {
        SettingsViewModel(
            sessionStore = container.sessionStore,
            authRepository = container.authRepository,
            okHttpClient = container.okHttpClient,
            videoCacheStorage = VideoCacheStorage { container.videoCache },
        )
    }
    val session by viewModel.session.collectAsStateWithLifecycle()
    val savedBaseUrl by viewModel.baseUrl.collectAsStateWithLifecycle()
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    val storage by viewModel.storage.collectAsStateWithLifecycle()
    val messageScope = rememberMessageScope()

    LaunchedEffect(viewModel, messageScope, snackbarHostState) {
        viewModel.refreshStorage()
        viewModel.messages.collect { message ->
            messageScope.launch { snackbarHostState.showSnackbar(message) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        LoopTopBar(title = "Settings")
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState()),
        ) {
            SectionHeader(title = "Account")
            AccountSection(
                user = session?.user,
                onOpenProfile = navigator::openProfile,
                onLogIn = navigator::openAuth,
                onLogOut = {
                    viewModel.logout()
                    navigator.selectTab(MainTab.HOME)
                    messageScope.launch { snackbarHostState.showSnackbar("Logged out") }
                },
            )
            SectionHeader(title = "Server")
            ServerSection(
                savedBaseUrl = savedBaseUrl,
                connection = connection,
                onSave = viewModel::saveServer,
                onTest = viewModel::testConnection,
                onInputChange = viewModel::resetConnectionCheck,
            )
            if (storage.available) {
                SectionHeader(title = "Storage")
                StorageSection(storage = storage, onClear = viewModel::clearVideoCache)
            }
            SectionHeader(title = "About")
            AboutSection()
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
