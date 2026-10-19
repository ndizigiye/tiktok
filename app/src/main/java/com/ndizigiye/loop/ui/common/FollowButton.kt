package com.ndizigiye.loop.ui.common

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ndizigiye.loop.api.UserDto
import com.ndizigiye.loop.data.remote.userMessage
import com.ndizigiye.loop.ui.navigation.LocalAppContainer
import com.ndizigiye.loop.ui.navigation.LocalNavigator
import kotlinx.coroutines.launch

/**
 * "Follow" / "Following" toggle for [user], shared state via the InteractionStore.
 * Asks signed-out users to log in, reports failures in the app snackbar, and
 * renders nothing for the signed-in user's own account. [compact] is for rows and
 * video overlays.
 */
@Composable
fun FollowButton(
    user: UserDto,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val container = LocalAppContainer.current
    val navigator = LocalNavigator.current
    val snackbarHostState = LocalSnackbarHostState.current
    val session by container.sessionStore.session.collectAsStateWithLifecycle()
    if (user.isMe || session?.user?.id == user.id) return

    val following = rememberIsFollowing(user)
    val scope = rememberCoroutineScope()
    val toggle: () -> Unit = {
        if (navigator.requireAuth()) {
            scope.launch {
                container.userRepository.setFollowing(user, follow = !following)
                    .onFailure { snackbarHostState.showSnackbar(it.userMessage()) }
            }
        }
    }

    val height: Dp = if (compact) 30.dp else 38.dp
    val padding = PaddingValues(horizontal = if (compact) 14.dp else 24.dp)
    val textStyle: TextStyle = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge
    val shape = RoundedCornerShape(8.dp)
    val sized = modifier
        .height(height)
        .defaultMinSize(minWidth = if (compact) 72.dp else 112.dp)

    if (following) {
        FilledTonalButton(
            onClick = toggle,
            modifier = sized,
            shape = shape,
            contentPadding = padding,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        ) {
            Text("Following", style = textStyle, maxLines = 1)
        }
    } else {
        Button(
            onClick = toggle,
            modifier = sized,
            shape = shape,
            contentPadding = padding,
        ) {
            Text("Follow", style = textStyle, maxLines = 1)
        }
    }
}
