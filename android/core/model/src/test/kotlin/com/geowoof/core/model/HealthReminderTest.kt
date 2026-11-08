package com.geowoof.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** When the phone reminds of something of the health of a pet, in the time zone of the phone. */
class HealthReminderTest {
    private val madrid = ZoneId.of("Europe/Madrid")

    private fun at(text: String): Instant = LocalDateTime.parse(text).atZone(madrid).toInstant()

    private fun event(
        dueOn: LocalDate = LocalDate.of(2026, 11, 20),
        dueAt: LocalTime? = null,
        reminder: HealthReminder = HealthReminder.OnTheDay,
        repeat: HealthRepeat? = null,
        doneOn: LocalDate? = null,
    ) = HealthEvent(
        id = "e",
        petId = "luna",
        type = HealthEventType.Vaccine,
        title = "Rabia",
        dueOn = dueOn,
        dueAt = dueAt,
        doneOn = doneOn,
        repeat = repeat,
        reminder = reminder,
        notes = null,
        updatedAt = Instant.EPOCH,
    )

    @Test
    fun without_a_time_it_reminds_at_nine_in_the_morning() {
        val now = at("2026-11-08T10:00")

        assertEquals(at("2026-11-20T09:00"), event(reminder = HealthReminder.OnTheDay).nextReminder(now, madrid)?.at)
        assertEquals(at("2026-11-19T09:00"), event(reminder = HealthReminder.DayBefore).nextReminder(now, madrid)?.at)
        assertEquals(at("2026-11-13T09:00"), event(reminder = HealthReminder.WeekBefore).nextReminder(now, madrid)?.at)
    }

    @Test
    fun with_a_time_it_reminds_an_hour_before_or_the_day_before_at_that_time() {
        val now = at("2026-11-08T10:00")
        val appointment = LocalTime.of(17, 30)

        assertEquals(at("2026-11-20T16:30"), event(dueAt = appointment).nextReminder(now, madrid)?.at)
        assertEquals(
            at("2026-11-19T17:30"),
            event(dueAt = appointment, reminder = HealthReminder.DayBefore).nextReminder(now, madrid)?.at,
        )
    }

    @Test
    fun what_is_done_or_has_no_reminder_does_not_remind() {
        val now = at("2026-11-08T10:00")

        assertNull(event(doneOn = LocalDate.of(2026, 11, 7)).nextReminder(now, madrid))
        assertNull(event(reminder = HealthReminder.None).nextReminder(now, madrid))
    }

    @Test
    fun a_reminder_that_went_by_is_not_given_late() {
        assertNull(event().nextReminder(at("2026-11-20T09:30"), madrid))
    }

    /** A daily medicine that is not marked keeps reminding, each day, until its repetition ends. */
    @Test
    fun what_repeats_reminds_of_its_next_day_while_it_is_not_done() {
        val medicine = event(
            dueOn = LocalDate.of(2026, 11, 1),
            dueAt = LocalTime.of(8, 0),
            repeat = HealthRepeat(1, RepeatUnit.Days, until = LocalDate.of(2026, 11, 10)),
        )

        val next = medicine.nextReminder(at("2026-11-08T10:00"), madrid)

        assertEquals(LocalDate.of(2026, 11, 9), next?.dueOn)
        assertEquals(at("2026-11-09T07:00"), next?.at)
        assertNull(medicine.nextReminder(at("2026-11-10T08:00"), madrid))
    }

    @Test
    fun the_time_is_that_of_where_the_phone_is() {
        val tokyo = ZoneId.of("Asia/Tokyo")

        val next = event().nextReminder(at("2026-11-08T10:00"), tokyo)

        assertEquals(LocalDateTime.parse("2026-11-20T09:00").atZone(tokyo).toInstant(), next?.at)
    }

    @Test
    fun the_next_time_of_a_repetition_counts_as_the_calendar_does() {
        val date = LocalDate.of(2026, 1, 31)

        assertEquals(LocalDate.of(2026, 2, 28), HealthRepeat(1, RepeatUnit.Months).after(date))
        assertEquals(LocalDate.of(2026, 2, 14), HealthRepeat(2, RepeatUnit.Weeks).after(date))
        assertEquals(LocalDate.of(2027, 1, 31), HealthRepeat(1, RepeatUnit.Years).after(date))
    }
}
