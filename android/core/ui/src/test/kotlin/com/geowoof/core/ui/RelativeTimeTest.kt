package com.geowoof.core.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class RelativeTimeTest {
    private val now = Instant.parse("2026-11-06T10:30:00Z")

    @Test
    fun a_recent_moment_says_how_long_ago_it_was() {
        val text = now.minus(Duration.ofMinutes(5)).relativeToNow(now.toEpochMilli())

        assertTrue(text, text.startsWith("5 ") && text.contains("ago"))
    }

    @Test
    fun some_hours_are_said_in_hours() {
        val text = now.minus(Duration.ofHours(3)).relativeToNow(now.toEpochMilli())

        assertTrue(text, text.startsWith("3 ") && text.contains("ago"))
    }
}
