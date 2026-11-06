package com.geowoof.feature.chat

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class ChatDayTest {
    private val madrid = ZoneId.of("Europe/Madrid")
    private val now = Instant.parse("2026-11-06T10:30:00Z")

    @Test
    fun two_messages_are_on_the_same_day_where_the_person_is_not_in_utc() {
        val before = Instant.parse("2026-11-05T22:30:00Z")
        val after = Instant.parse("2026-11-05T23:30:00Z")

        assertTrue(before.isSameDayAs(after, ZoneId.of("UTC")))
        assertFalse(before.isSameDayAs(after, madrid))
    }

    @Test
    fun the_clock_of_a_message_is_the_local_one() {
        assertTrue(formatChatClock(now, madrid).contains("11:30"))
    }

    @Test
    fun the_day_of_a_message_says_today_and_yesterday_in_words() {
        assertEquals("Today", formatChatDay(now, now))
        assertEquals("Yesterday", formatChatDay(now.minus(Duration.ofDays(1)), now))
    }

    @Test
    fun an_older_day_is_a_date() {
        val text = formatChatDay(now.minus(Duration.ofDays(30)), now)

        assertTrue(text, text.any(Char::isDigit))
    }
}
