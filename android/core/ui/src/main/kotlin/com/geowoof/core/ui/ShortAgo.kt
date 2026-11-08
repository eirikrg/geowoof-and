package com.geowoof.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import java.time.Duration
import java.time.Instant

/** The unit in which [Ago] counts, the largest that fits. */
enum class AgoUnit { Now, Minutes, Hours, Days, Weeks, Months, Years }

/** How long ago something happened, in a single unit: 2 weeks, not 2 weeks and 3 days. */
data class Ago(val unit: AgoUnit, val amount: Long)

/**
 * Minutes up to an hour, hours up to a day, days up to a week, weeks up to a month, months up to a year, and then
 * years. A month is 30 days. A time in the future, as a clock a little ahead can give, is now.
 */
fun Duration.toAgo(): Ago {
    val elapsed = if (isNegative) Duration.ZERO else this
    val days = elapsed.toDays()
    return when {
        elapsed.toMinutes() < 1 -> Ago(AgoUnit.Now, 0)
        elapsed.toHours() < 1 -> Ago(AgoUnit.Minutes, elapsed.toMinutes())
        days < 1 -> Ago(AgoUnit.Hours, elapsed.toHours())
        days < DAYS_PER_WEEK -> Ago(AgoUnit.Days, days)
        days < DAYS_PER_MONTH -> Ago(AgoUnit.Weeks, days / DAYS_PER_WEEK)
        days < DAYS_PER_YEAR -> Ago(AgoUnit.Months, (days / DAYS_PER_MONTH).coerceAtMost(MONTHS_PER_YEAR - 1))
        else -> Ago(AgoUnit.Years, days / DAYS_PER_YEAR)
    }
}

/**
 * How long ago, as short as comments say it: "now", "5 min", "3 h", "2 d", "3 sem", "6 meses", "1 año". For the
 * whole of it, as TalkBack has to say it, [relativeToNow].
 */
@Composable
fun Instant.shortAgo(now: Instant = Instant.now()): String {
    val ago = Duration.between(this, now).toAgo()
    val amount = ago.amount.toInt()
    return when (ago.unit) {
        AgoUnit.Now -> stringResource(R.string.core_ui_ago_now)
        AgoUnit.Minutes -> pluralStringResource(R.plurals.core_ui_ago_minutes, amount, amount)
        AgoUnit.Hours -> pluralStringResource(R.plurals.core_ui_ago_hours, amount, amount)
        AgoUnit.Days -> pluralStringResource(R.plurals.core_ui_ago_days, amount, amount)
        AgoUnit.Weeks -> pluralStringResource(R.plurals.core_ui_ago_weeks, amount, amount)
        AgoUnit.Months -> pluralStringResource(R.plurals.core_ui_ago_months, amount, amount)
        AgoUnit.Years -> pluralStringResource(R.plurals.core_ui_ago_years, amount, amount)
    }
}

private const val DAYS_PER_WEEK = 7L
private const val DAYS_PER_MONTH = 30L
private const val DAYS_PER_YEAR = 365L
private const val MONTHS_PER_YEAR = 12L
