package com.geowoof.feature.chat

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
