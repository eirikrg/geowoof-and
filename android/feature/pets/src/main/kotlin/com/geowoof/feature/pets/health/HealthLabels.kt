package com.geowoof.feature.pets.health

import androidx.annotation.ArrayRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.geowoof.core.designsystem.icon.GeoWoofIcons
import com.geowoof.core.model.HealthEvent
import com.geowoof.core.model.HealthEventType
import com.geowoof.core.model.HealthReminder
import com.geowoof.core.model.HealthRepeat
import com.geowoof.core.model.RepeatUnit
import com.geowoof.core.ui.formatted
import com.geowoof.feature.pets.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit

// How the health of a pet is named on screen.

@StringRes
internal fun HealthEventType.labelRes(): Int = when (this) {
    HealthEventType.Vaccine -> R.string.feature_pets_health_vaccine
    HealthEventType.Deworming -> R.string.feature_pets_health_deworming
    HealthEventType.VetVisit -> R.string.feature_pets_health_vet_visit
    HealthEventType.Medication -> R.string.feature_pets_health_medication
    HealthEventType.Other -> R.string.feature_pets_health_other
}

@DrawableRes
internal fun HealthEventType.icon(): Int = when (this) {
    HealthEventType.Vaccine -> GeoWoofIcons.Vaccines
    HealthEventType.Deworming -> GeoWoofIcons.PestControl
    HealthEventType.VetVisit -> GeoWoofIcons.Stethoscope
    HealthEventType.Medication -> GeoWoofIcons.Medication
    HealthEventType.Other -> GeoWoofIcons.CalendarMonth
}

/** What is usually written for each type, to pick instead of writing it. */
@ArrayRes
internal fun HealthEventType.suggestionsRes(): Int? = when (this) {
    HealthEventType.Vaccine -> R.array.feature_pets_health_vaccine_suggestions
    HealthEventType.Deworming -> R.array.feature_pets_health_deworming_suggestions
    HealthEventType.VetVisit -> R.array.feature_pets_health_vet_visit_suggestions
    HealthEventType.Medication, HealthEventType.Other -> null
}

@StringRes
internal fun HealthReminder.labelRes(): Int = when (this) {
    HealthReminder.None -> R.string.feature_pets_health_reminder_none
    HealthReminder.OnTheDay -> R.string.feature_pets_health_reminder_on_the_day
    HealthReminder.DayBefore -> R.string.feature_pets_health_reminder_day_before
    HealthReminder.WeekBefore -> R.string.feature_pets_health_reminder_week_before
}

/** "Every year", "Every 3 months until 1 Mar 2027", or that it does not repeat. */
@Composable
internal fun HealthRepeat?.label(): String {
    if (this == null) return stringResource(R.string.feature_pets_health_no_repeat)
    val every = when (unit) {
        RepeatUnit.Days -> pluralStringResource(R.plurals.feature_pets_health_every_days, every, every)
        RepeatUnit.Weeks -> pluralStringResource(R.plurals.feature_pets_health_every_weeks, every, every)
        RepeatUnit.Months -> pluralStringResource(R.plurals.feature_pets_health_every_months, every, every)
        RepeatUnit.Years -> pluralStringResource(R.plurals.feature_pets_health_every_years, every, every)
    }
    return until?.let { stringResource(R.string.feature_pets_health_every_until, every, it.formatted()) } ?: every
}

/**
 * When it is due, as people say it: today, tomorrow, in 5 days, or the date further away; and how late, if it is.
 * With its time if it has one.
 */
@Composable
internal fun HealthEvent.dueText(today: LocalDate = LocalDate.now()): String {
    val days = ChronoUnit.DAYS.between(today, dueOn).toInt()
    val day = when {
        days == 0 -> stringResource(R.string.feature_pets_health_today)
        days == 1 -> stringResource(R.string.feature_pets_health_tomorrow)
        days in 2..WEEK -> pluralStringResource(R.plurals.feature_pets_health_in_days, days, days)
        days < 0 && !isDone -> pluralStringResource(R.plurals.feature_pets_health_days_late, -days, -days)
        else -> dueOn.formatted()
    }
    val time = dueAt?.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
    return time?.let { stringResource(R.string.feature_pets_health_day_at, day, it) } ?: day
}

private const val WEEK = 7
