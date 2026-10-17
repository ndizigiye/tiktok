package com.ndizigiye.loop.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ndizigiye.loop.ui.navigation.LocalNavigator

/** Full-screen invitation to sign in, for tabs that need an account. */
@Composable
fun SignedOutPrompt(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    val navigator = LocalNavigator.current
    CenteredMessage(
        icon = Icons.Outlined.AccountCircle,
        title = title,
        subtitle = subtitle,
        modifier = modifier,
    ) {
        Button(
            onClick = navigator::openAuth,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            Text("Log in or sign up")
        }
    }
}
