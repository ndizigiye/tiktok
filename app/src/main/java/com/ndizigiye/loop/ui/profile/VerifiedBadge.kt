package com.ndizigiye.loop.ui.profile

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ndizigiye.loop.ui.theme.LoopColors

/** Badge shown after the display name of verified accounts; size it with [modifier]. */
@Composable
internal fun VerifiedBadge(modifier: Modifier = Modifier) {
    Icon(
        imageVector = Icons.Filled.Verified,
        contentDescription = "Verified",
        tint = LoopColors.Accent,
        modifier = modifier,
    )
}
