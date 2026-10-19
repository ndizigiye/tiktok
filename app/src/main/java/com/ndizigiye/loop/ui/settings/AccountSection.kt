package com.ndizigiye.loop.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ndizigiye.loop.api.UserDto
import com.ndizigiye.loop.ui.common.Avatar
import com.ndizigiye.loop.ui.theme.LoopColors

/** The signed-in account with a confirmed "Log out", or a "Log in" button when signed out. */
@Composable
internal fun AccountSection(
    user: UserDto?,
    onOpenProfile: (username: String) -> Unit,
    onLogIn: () -> Unit,
    onLogOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (user == null) {
            ListItem(
                headlineContent = { Text("Not signed in") },
                supportingContent = { Text("Log in to like, comment, follow and post videos.") },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Outlined.AccountCircle,
                        contentDescription = null,
                        tint = LoopColors.TextMuted,
                        modifier = Modifier.size(40.dp),
                    )
                },
                colors = settingsListItemColors(),
            )
            Button(
                onClick = onLogIn,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                Text("Log in")
            }
        } else {
            var confirmLogout by rememberSaveable { mutableStateOf(false) }
            ListItem(
                headlineContent = {
                    Text(text = user.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                supportingContent = {
                    Text(text = "@${user.username}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                leadingContent = { Avatar(user = user, size = 40.dp) },
                modifier = Modifier.clickable(onClickLabel = "Open profile") { onOpenProfile(user.username) },
                colors = settingsListItemColors(),
            )
            OutlinedButton(
                onClick = { confirmLogout = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text("Log out")
            }
            if (confirmLogout) {
                LogoutDialog(
                    username = user.username,
                    onConfirm = {
                        confirmLogout = false
                        onLogOut()
                    },
                    onDismiss = { confirmLogout = false },
                )
            }
        }
    }
}

@Composable
private fun LogoutDialog(username: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log out?") },
        text = { Text("You'll need the password of @$username to log back in.") },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text("Log out")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
