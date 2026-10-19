package com.ndizigiye.loop.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ndizigiye.loop.data.session.SessionStore
import com.ndizigiye.loop.ui.theme.LoopColors
import okhttp3.HttpUrl

private const val URL_HELP =
    "Android emulator: http://10.0.2.2:8080/ — phone on the same Wi-Fi: http://<your computer's LAN IP>:8080/"

/**
 * The server URL field with "Save" and "Test connection". [onSave] stores the URL and
 * returns it as saved; [onInputChange] is called on every edit.
 */
@Composable
internal fun ServerSection(
    savedBaseUrl: String,
    connection: ConnectionCheck,
    onSave: (HttpUrl) -> String,
    onTest: (HttpUrl) -> Unit,
    onInputChange: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var input by rememberSaveable { mutableStateOf(savedBaseUrl) }
    val url = remember(input) { parseServerUrl(input) }
    val unchanged = url != null && SessionStore.normalizeBaseUrl(url.toString()) == savedBaseUrl
    val focusManager = LocalFocusManager.current
    val canSave = url != null && !unchanged
    val save: () -> Unit = {
        if (url != null && !unchanged) input = onSave(url)
        focusManager.clearFocus()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        OutlinedTextField(
            value = input,
            onValueChange = { value ->
                input = value
                onInputChange()
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Server URL") },
            isError = url == null,
            supportingText = if (url == null) {
                @Composable { Text("Enter a valid http:// or https:// URL") }
            } else {
                null
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { save() }),
        )
        Text(
            text = URL_HELP,
            style = MaterialTheme.typography.bodySmall,
            color = LoopColors.TextMuted,
            modifier = Modifier.padding(top = 4.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(onClick = save, enabled = canSave) {
                Text("Save")
            }
            OutlinedButton(
                onClick = { if (url != null) onTest(url) },
                enabled = url != null && connection != ConnectionCheck.Running,
            ) {
                Text("Test connection")
            }
        }
        ConnectionStatus(check = connection, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun ConnectionStatus(check: ConnectionCheck, modifier: Modifier = Modifier) {
    when (check) {
        ConnectionCheck.Idle -> {}
        ConnectionCheck.Running -> StatusLine(text = "Testing…", color = LoopColors.TextSecondary, modifier = modifier) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 2.dp,
            )
        }
        ConnectionCheck.Connected -> StatusLine(text = "Connected", color = LoopColors.Success, modifier = modifier) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = LoopColors.Success,
                modifier = Modifier.size(18.dp),
            )
        }
        is ConnectionCheck.Failed -> StatusLine(
            text = check.message,
            color = MaterialTheme.colorScheme.error,
            modifier = modifier,
        ) {
            Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun StatusLine(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        icon()
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}
