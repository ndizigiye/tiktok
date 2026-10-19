package com.ndizigiye.loop.ui.theme

import androidx.compose.ui.graphics.Color

/** Loop's signature accent. */
val LoopViolet = Color(0xFF7B5CFF)

/** Named colours for feature UI that are not part of the Material colour scheme. */
object LoopColors {
    val Accent = LoopViolet
    val AccentLight = Color(0xFFB9A9FF)

    /** Filled heart for liked videos and comments. */
    val Like = Color(0xFFFF3D57)

    /** Filled bookmark for saved videos. */
    val Bookmark = Color(0xFFFFC93C)

    /** Text and icons drawn on top of video (always white, with shades for hierarchy). */
    val OverlayText = Color.White
    val OverlayTextSecondary = Color(0xE6FFFFFF)
    val OverlayTextMuted = Color(0xB3FFFFFF)

    /** Dark translucent backing for overlay controls and bottom gradients. */
    val OverlayScrim = Color(0x80000000)

    /** Grey text on dark surfaces. */
    val TextSecondary = Color(0xFFB3B3B3)
    val TextMuted = Color(0xFF8A8A8A)

    /** Background shown while thumbnails and avatars load. */
    val Placeholder = Color(0xFF1E1E1E)

    val Divider = Color(0xFF2A2A2A)
    val Success = Color(0xFF3DD68C)
}
