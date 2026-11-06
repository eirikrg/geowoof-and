package com.geowoof.feature.chat

import android.text.format.DateUtils
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Today, the time. Any other day, the date: that is what tells them apart. */
internal fun formatChatTime(
    moment: Instant,
    now: Instant = Instant.now(),
    zone: ZoneId = ZoneId.systemDefault(),
): String {
    val local = moment.atZone(zone)
    val style = if (local.toLocalDate() == now.atZone(zone).toLocalDate()) {
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    } else {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
    }
    return style.format(local)
}

/** Only the time: the day is said above the messages of that day. */
internal fun formatChatClock(moment: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(moment.atZone(zone))

internal fun Instant.isSameDayAs(other: Instant, zone: ZoneId = ZoneId.systemDefault()): Boolean =
    atZone(zone).toLocalDate() == other.atZone(zone).toLocalDate()

/** "Today", "Yesterday" or the date. The system translates the first two. */
internal fun formatChatDay(moment: Instant, now: Instant = Instant.now()): String = DateUtils.getRelativeTimeSpanString(
    moment.toEpochMilli(),
    now.toEpochMilli(),
    DateUtils.DAY_IN_MILLIS,
    DateUtils.FORMAT_ABBREV_MONTH,
).toString()
