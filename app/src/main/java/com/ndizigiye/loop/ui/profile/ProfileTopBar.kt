package com.ndizigiye.loop.ui.profile

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.ndizigiye.loop.ui.common.LoopTopBar
import com.ndizigiye.loop.ui.common.rememberBackAction
import com.ndizigiye.loop.ui.navigation.LocalNavigator

/**
 * "@username" title bar. Your own profile gets a menu button that opens Settings;
 * other profiles get a menu to share the profile or copy the username. [showBack]
 * adds the back arrow on pushed profiles.
 */
@Composable
internal fun ProfileTopBar(
    username: String,
    isOwn: Boolean,
    showBack: Boolean,
    onShare: () -> Unit,
    onCopyUsername: () -> Unit,
) {
    val navigator = LocalNavigator.current
    LoopTopBar(
        title = "@$username",
        onBack = if (showBack) rememberBackAction() else null,
        actions = {
            if (isOwn) {
                IconButton(onClick = navigator::openSettings) {
                    Icon(imageVector = Icons.Filled.Menu, contentDescription = "Settings")
                }
            } else {
                ProfileMoreMenu(onShare = onShare, onCopyUsername = onCopyUsername)
            }
        },
    )
}

@Composable
private fun ProfileMoreMenu(onShare: () -> Unit, onCopyUsername: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(imageVector = Icons.Filled.MoreVert, contentDescription = "More options")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Share profile") },
                onClick = {
                    expanded = false
                    onShare()
                },
                leadingIcon = { Icon(imageVector = Icons.Outlined.Share, contentDescription = null) },
            )
            DropdownMenuItem(
                text = { Text("Copy username") },
                onClick = {
                    expanded = false
                    onCopyUsername()
                },
                leadingIcon = { Icon(imageVector = Icons.Outlined.ContentCopy, contentDescription = null) },
            )
        }
    }
}
