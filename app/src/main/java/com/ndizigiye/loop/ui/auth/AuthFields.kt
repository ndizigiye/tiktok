package com.ndizigiye.loop.ui.auth

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation

@Composable
internal fun UsernameField(
    value: String,
    onValueChange: (String) -> Unit,
    error: String?,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        label = { Text("Username") },
        leadingIcon = { Icon(imageVector = Icons.Outlined.AlternateEmail, contentDescription = null) },
        supportingText = errorText(error),
        isError = error != null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Ascii,
            imeAction = ImeAction.Next,
        ),
    )
}

@Composable
internal fun DisplayNameField(
    value: String,
    onValueChange: (String) -> Unit,
    error: String?,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        label = { Text("Name") },
        leadingIcon = { Icon(imageVector = Icons.Outlined.Badge, contentDescription = null) },
        supportingText = errorText(error),
        isError = error != null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Words,
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Next,
        ),
    )
}

/** Password with a show/hide toggle; the keyboard's Done action calls [onDone]. */
@Composable
internal fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    error: String?,
    enabled: Boolean,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        label = { Text("Password") },
        leadingIcon = { Icon(imageVector = Icons.Outlined.Lock, contentDescription = null) },
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    imageVector = if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = if (visible) "Hide password" else "Show password",
                )
            }
        },
        supportingText = errorText(error),
        isError = error != null,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
    )
}

private fun errorText(error: String?): (@Composable () -> Unit)? =
    error?.let { message -> @Composable { Text(message) } }
