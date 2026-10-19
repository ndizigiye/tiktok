package com.ndizigiye.loop.common

import kotlin.math.abs

/** Compact engagement counts: 999, 1.2K, 12.3K, 123K, 1.2M, 1.2B. */
fun formatCount(count: Long): String {
    val n = abs(count)
    val sign = if (count < 0) "-" else ""
    return when {
        n < 1_000 -> "$sign$n"
        n < 1_000_000 -> sign + scaled(n, 1_000, "K")
        n < 1_000_000_000 -> sign + scaled(n, 1_000_000, "M")
        else -> sign + scaled(n, 1_000_000_000, "B")
    }
}

private fun scaled(n: Long, unit: Long, suffix: String): String {
    // One decimal below 100 units (1.2K, 45.6K); whole numbers above (123K).
    // Truncate rather than round so 999_999 never shows as "1000K".
    return if (n < 100 * unit) {
        val tenths = n * 10 / unit
        val whole = tenths / 10
        val frac = tenths % 10
        if (frac == 0L) "$whole$suffix" else "$whole.$frac$suffix"
    } else {
        "${n / unit}$suffix"
    }
}

/**
 * Short relative age used in feeds and comments: "now", "5m", "3h", "2d", "4w",
 * then a date-like fallback of whole years ("1y").
 */
fun formatRelativeTime(epochMillis: Long, nowMillis: Long): String {
    val seconds = ((nowMillis - epochMillis) / 1000).coerceAtLeast(0)
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24
    return when {
        seconds < 60 -> "now"
        minutes < 60 -> "${minutes}m"
        hours < 24 -> "${hours}h"
        days < 7 -> "${days}d"
        days < 365 -> "${days / 7}w"
        else -> "${days / 365}y"
    }
}

/** Playback position as m:ss, e.g. 0:07, 1:23. */
fun formatDuration(millis: Long): String {
    val totalSeconds = (millis.coerceAtLeast(0) / 1000)
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "$m:${s.toString().padStart(2, '0')}"
}
