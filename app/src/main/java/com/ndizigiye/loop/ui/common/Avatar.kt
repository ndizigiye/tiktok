package com.ndizigiye.loop.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import coil3.compose.AsyncImage
import com.ndizigiye.loop.api.UserDto
import com.ndizigiye.loop.ui.theme.LoopColors

/**
 * Round profile picture: the user's avatar image over their initials on their
 * `avatarColor`. A [borderColor] draws a ring inside [size].
 */
@Composable
fun Avatar(
    user: UserDto?,
    size: Dp,
    modifier: Modifier = Modifier,
    borderColor: Color? = null,
) {
    val imageUrl = rememberResolvedUrl(user?.avatarUrl)
    val background = remember(user?.avatarColor) { parseHexColor(user?.avatarColor) ?: LoopColors.Placeholder }
    val initials = remember(user?.displayName, user?.username) { initialsOf(user) }
    val fontSize = with(LocalDensity.current) { (size * 0.38f).toSp() }
    val ring = if (borderColor != null) {
        val width = max(1.5.dp, size / 28)
        Modifier
            .border(width, borderColor, CircleShape)
            .padding(width)
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .size(size)
            .then(ring)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        if (initials.isNotEmpty()) {
            Text(text = initials, color = Color.White, fontSize = fontSize, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = user?.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

private fun initialsOf(user: UserDto?): String {
    if (user == null) return ""
    val words = user.displayName.split(' ').filter { it.isNotBlank() }
    val letters = when {
        words.size >= 2 -> "${words[0].first()}${words[1].first()}"
        words.size == 1 -> words[0].take(1)
        else -> user.username.take(1)
    }
    return letters.uppercase()
}

/** Parses "#RRGGBB" or "#AARRGGBB"; null when malformed. */
private fun parseHexColor(hex: String?): Color? {
    val digits = hex?.trim()?.removePrefix("#") ?: return null
    val value = digits.toLongOrNull(16) ?: return null
    return when (digits.length) {
        6 -> Color(0xFF000000L or value)
        8 -> Color(value)
        else -> null
    }
}
