package com.geowoof.core.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

enum class HealthEventType { Vaccine, Deworming, VetVisit, Medication, Other }

enum class RepeatUnit { Days, Weeks, Months, Years }

/** When the phone reminds of it. */
enum class HealthReminder { None, OnTheDay, DayBefore, WeekBefore }

/** How often something comes back, and until when, if it ends. */
data class HealthRepeat(val every: Int, val unit: RepeatUnit, val until: LocalDate? = null) {
    /** The next time after [date], as the server counts it: a month after the 31st is the end of the next month. */
    fun after(date: LocalDate): LocalDate = when (unit) {
        RepeatUnit.Days -> date.plusDays(every.toLong())
        RepeatUnit.Weeks -> date.plusWeeks(every.toLong())
        RepeatUnit.Months -> date.plusMonths(every.toLong())
        RepeatUnit.Years -> date.plusYears(every.toLong())
    }
}

/**
 * Something of the health of a pet that is due on a day, such as a vaccine or an appointment, done on [doneOn] or
 * still pending. The day and the time are those of the owner, wherever the phone is.
 */
data class HealthEvent(
    val id: String,
    val petId: String,
    val type: HealthEventType,
    val title: String,
    val dueOn: LocalDate,
    val dueAt: LocalTime?,
    val doneOn: LocalDate?,
    val repeat: HealthRepeat?,
    val reminder: HealthReminder,
    val notes: String?,
    val updatedAt: Instant,
) {
    val isDone: Boolean get() = doneOn != null

    fun isOverdue(today: LocalDate): Boolean = !isDone && dueOn.isBefore(today)
}

/** Something pending with a reminder, with the name of its pet, which the notification says. */
data class HealthReminderItem(val event: HealthEvent, val petName: String)

/** What was done and, if it repeats, the next one. */
data class HealthCompletion(val done: HealthEvent, val next: HealthEvent?)

/** A reminder to show: of which event, for which of its days, and when. */
data class PlannedReminder(val eventId: String, val dueOn: LocalDate, val at: Instant)

/**
 * The next reminder of something pending, after [now]. Something that repeats keeps reminding of each of its days
 * while it is not marked as done, as a daily medicine has to: the reminder of a day that went by gives way to the next
 * one, until the repetition ends. Something done, or without a reminder, has none.
 */
fun HealthEvent.nextReminder(now: Instant, zone: ZoneId): PlannedReminder? {
    if (isDone || reminder == HealthReminder.None) return null
    return occurrences()
        .take(MAX_OCCURRENCES)
        .mapNotNull { due -> reminderTime(due)?.let { PlannedReminder(id, due, it.atZone(zone).toInstant()) } }
        .firstOrNull { it.at.isAfter(now) }
}

/** The days it is due: the first, and then each repetition until it ends. */
private fun HealthEvent.occurrences(): Sequence<LocalDate> = generateSequence(dueOn) { previous ->
    repeat?.let { series -> series.after(previous).takeUnless { next -> series.until?.let(next::isAfter) == true } }
}

/**
 * When it reminds of the day [due]. With a time, on the day itself it is an hour before, and the day before at the
 * same time. Without one, at nine in the morning. A week before is always at nine.
 */
fun HealthEvent.reminderTime(due: LocalDate): LocalDateTime? = when (reminder) {
    HealthReminder.None -> null
    HealthReminder.OnTheDay -> dueAt?.let { due.atTime(it).minusHours(1) } ?: due.atTime(MORNING)
    HealthReminder.DayBefore -> due.minusDays(1).atTime(dueAt ?: MORNING)
    HealthReminder.WeekBefore -> due.minusWeeks(1).atTime(MORNING)
}

private val MORNING: LocalTime = LocalTime.of(9, 0)

/** A daily medicine of a year and a half, and enough to skip what is in the past without looping forever. */
private const val MAX_OCCURRENCES = 600
