package com.geowoof.core.ui

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.Instant

/** How long ago, as short as comments say it: always in the largest unit that fits. */
@RunWith(AndroidJUnit4::class)
class ShortAgoTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val now = Instant.parse("2026-11-08T10:30:00Z")

    private fun ago(duration: Duration) = duration.toAgo()

    @Test
    fun under_a_minute_is_now_and_also_a_moment_ahead() {
        assertEquals(Ago(AgoUnit.Now, 0), ago(Duration.ofSeconds(59)))
        assertEquals(Ago(AgoUnit.Now, 0), ago(Duration.ofSeconds(-30)))
    }

    @Test
    fun each_unit_until_the_next_one() {
        assertEquals(Ago(AgoUnit.Minutes, 59), ago(Duration.ofMinutes(59)))
        assertEquals(Ago(AgoUnit.Hours, 1), ago(Duration.ofMinutes(60)))
        assertEquals(Ago(AgoUnit.Hours, 23), ago(Duration.ofHours(23)))
        assertEquals(Ago(AgoUnit.Days, 1), ago(Duration.ofHours(24)))
        assertEquals(Ago(AgoUnit.Days, 6), ago(Duration.ofDays(6)))
        assertEquals(Ago(AgoUnit.Weeks, 1), ago(Duration.ofDays(7)))
        assertEquals(Ago(AgoUnit.Weeks, 4), ago(Duration.ofDays(29)))
        assertEquals(Ago(AgoUnit.Months, 1), ago(Duration.ofDays(30)))
        assertEquals(Ago(AgoUnit.Years, 1), ago(Duration.ofDays(365)))
        assertEquals(Ago(AgoUnit.Years, 2), ago(Duration.ofDays(800)))
    }

    /** 360 days are 12 months of 30 days, but it is not a year yet: it says 11 months, not 12. */
    @Test
    fun the_last_days_of_a_year_are_still_months() {
        assertEquals(Ago(AgoUnit.Months, 6), ago(Duration.ofDays(185)))
        assertEquals(Ago(AgoUnit.Months, 11), ago(Duration.ofDays(362)))
    }

    private fun show(duration: Duration) {
        composeRule.setContent { Text(now.minus(duration).shortAgo(now)) }
    }

    @Test
    fun in_english_it_is_a_number_and_a_letter() {
        show(Duration.ofDays(14))

        composeRule.onNodeWithText("2w").assertExists()
    }

    @Test
    @Config(qualifiers = "es")
    fun in_spanish_months_and_years_are_written_whole() {
        show(Duration.ofDays(185))

        composeRule.onNodeWithText("6 meses").assertExists()
    }

    @Test
    @Config(qualifiers = "es")
    fun in_spanish_one_year_is_singular() {
        show(Duration.ofDays(400))

        composeRule.onNodeWithText("1 año").assertExists()
    }

    @Test
    @Config(qualifiers = "es")
    fun in_spanish_a_moment_is_now() {
        show(Duration.ofSeconds(10))

        composeRule.onNodeWithText("ahora").assertExists()
    }
}
