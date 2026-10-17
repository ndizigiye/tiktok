package com.ndizigiye.loop.common

import kotlin.test.Test
import kotlin.test.assertEquals

class FormattingTest {
    @Test
    fun counts() {
        assertEquals("0", formatCount(0))
        assertEquals("999", formatCount(999))
        assertEquals("1K", formatCount(1_000))
        assertEquals("1.2K", formatCount(1_250))
        assertEquals("99.9K", formatCount(99_999))
        assertEquals("100K", formatCount(100_000))
        assertEquals("999K", formatCount(999_999))
        assertEquals("1M", formatCount(1_000_000))
        assertEquals("12.3M", formatCount(12_345_678))
        assertEquals("2.1B", formatCount(2_100_000_000))
        assertEquals("-1.5K", formatCount(-1_500))
    }

    @Test
    fun relativeTime() {
        val now = 1_000_000_000_000L
        assertEquals("now", formatRelativeTime(now - 30_000, now))
        assertEquals("now", formatRelativeTime(now + 5_000, now))
        assertEquals("5m", formatRelativeTime(now - 5 * 60_000, now))
        assertEquals("3h", formatRelativeTime(now - 3 * 3_600_000, now))
        assertEquals("2d", formatRelativeTime(now - 2 * 86_400_000L, now))
        assertEquals("3w", formatRelativeTime(now - 21 * 86_400_000L, now))
        assertEquals("1y", formatRelativeTime(now - 400 * 86_400_000L, now))
    }

    @Test
    fun duration() {
        assertEquals("0:00", formatDuration(0))
        assertEquals("0:07", formatDuration(7_900))
        assertEquals("1:23", formatDuration(83_000))
    }
}
