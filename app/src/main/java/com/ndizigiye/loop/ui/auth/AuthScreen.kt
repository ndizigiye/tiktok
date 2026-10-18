package com.ndizigiye.loop.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ndizigiye.loop.ui.common.LocalSnackbarHostState
import com.ndizigiye.loop.ui.common.rememberBackAction
import com.ndizigiye.loop.ui.navigation.LocalAppContainer
import com.ndizigiye.loop.ui.navigation.LocalNavigator
import com.ndizigiye.loop.ui.theme.LoopColors
import com.ndizigiye.loop.ui.theme.LoopViolet
import kotlinx.coroutines.launch

/** Log in or create an account; closes with a welcome message on success. */
@Composable
fun AuthScreen() {
    val container = LocalAppContainer.current
    val navigator = LocalNavigator.current
    val snackbarHostState = LocalSnackbarHostState.current
    val viewModel: AuthViewModel = viewModel { AuthViewModel(container.authRepository) }
    val submission by viewModel.state.collectAsStateWithLifecycle()
    val baseUrl by container.sessionStore.baseUrl.collectAsStateWithLifecycle()
    val formState = rememberAuthFormState()
    val focusManager = LocalFocusManager.current
    val back = rememberBackAction()
    val messageScope = rememberMessageScope()

    val currentBack by rememberUpdatedState(back)
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(viewModel, lifecycleOwner, messageScope, snackbarHostState) {
        // Only while started: the back action ignores a stopped screen, and the event waits in the channel.
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.signedIn.collect { user ->
                messageScope.launch { snackbarHostState.showSnackbar("Welcome, ${user.displayName}") }
                currentBack()
            }
        }
    }

    val loading = submission.isLoading
    val submit: () -> Unit = {
        if (!loading) {
            formState.markSubmitted()
            val form = formState.form
            if (form.isValid) {
                focusManager.clearFocus()
                viewModel.submit(form)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        IconButton(onClick = back, modifier = Modifier.padding(start = 4.dp, top = 4.dp)) {
            Icon(imageVector = Icons.Filled.Close, contentDescription = "Close")
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            AuthHeader(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 32.dp),
            )
            AuthModeTabs(
                mode = formState.mode,
                enabled = !loading,
                onSelect = { mode ->
                    formState.selectMode(mode)
                    viewModel.clearError()
                },
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                UsernameField(
                    value = formState.username,
                    onValueChange = { value ->
                        formState.updateUsername(value)
                        viewModel.clearError()
                    },
                    error = formState.errorFor(AuthField.USERNAME),
                    enabled = !loading,
                )
                if (formState.mode == AuthMode.SIGN_UP) {
                    DisplayNameField(
                        value = formState.displayName,
                        onValueChange = { value ->
                            formState.updateDisplayName(value)
                            viewModel.clearError()
                        },
                        error = formState.errorFor(AuthField.DISPLAY_NAME),
                        enabled = !loading,
                    )
                }
                PasswordField(
                    value = formState.password,
                    onValueChange = { value ->
                        formState.updatePassword(value)
                        viewModel.clearError()
                    },
                    error = formState.errorFor(AuthField.PASSWORD),
                    enabled = !loading,
                    onDone = submit,
                )
                SubmitButton(
                    label = formState.mode.label,
                    loading = loading,
                    onClick = submit,
                    modifier = Modifier.padding(top = 8.dp),
                )
                val serverError = submission.error
                if (serverError != null) {
                    Text(
                        text = serverError,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                    )
                }
            }
        }
        ServerFooter(baseUrl = baseUrl, onChange = navigator::openSettings)
    }
}

@Composable
private fun AuthHeader(modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "Loop",
            color = LoopViolet,
            fontSize = 64.sp,
            lineHeight = 68.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = (-2).sp,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Watch, make and share short videos.",
            style = MaterialTheme.typography.bodyLarge,
            color = LoopColors.TextSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthModeTabs(
    mode: AuthMode,
    enabled: Boolean,
    onSelect: (AuthMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val modes = AuthMode.entries
    PrimaryTabRow(
        selectedTabIndex = modes.indexOf(mode),
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        divider = { HorizontalDivider(thickness = 0.5.dp, color = LoopColors.Divider) },
    ) {
        modes.forEach { tab ->
            Tab(
                selected = tab == mode,
                onClick = { onSelect(tab) },
                enabled = enabled,
                text = { Text(tab.label) },
                selectedContentColor = MaterialTheme.colorScheme.onBackground,
                unselectedContentColor = LoopColors.TextMuted,
            )
        }
    }
}

/** Full-width primary button that shows a small spinner (and ignores taps) while [loading]. */
@Composable
private fun SubmitButton(
    label: String,
    loading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp,
            )
        } else {
            Text(label)
        }
    }
}

@Composable
private fun ServerFooter(baseUrl: String, onChange: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Server: $baseUrl",
            style = MaterialTheme.typography.bodySmall,
            color = LoopColors.TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onChange) { Text("Change") }
    }
}
