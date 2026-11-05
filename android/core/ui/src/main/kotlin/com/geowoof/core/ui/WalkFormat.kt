package com.geowoof.core.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.geowoof.core.model.WalkVisibility
import java.time.DateTimeException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private const val METERS_PER_KM = 1000
private const val KM_DIVISOR = 1000.0
private const val SECONDS_PER_MINUTE = 60
private const val SECONDS_PER_HOUR = 3600

/** 1:05:09, or 42:10 under an hour. */
fun formatDuration(totalSeconds: Int): String {
    val hours = totalSeconds / SECONDS_PER_HOUR
    val minutes = totalSeconds % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
    val seconds = totalSeconds % SECONDS_PER_MINUTE
    return if (hours > 0) {
        "%d:%02d:%02d".format(Locale.ROOT, hours, minutes, seconds)
    } else {
        "%d:%02d".format(Locale.ROOT, minutes, seconds)
    }
}

@Composable
fun distanceText(meters: Int): String = if (meters < METERS_PER_KM) {
    stringResource(R.string.core_ui_distance_m, meters)
} else {
    stringResource(R.string.core_ui_distance_km, meters / KM_DIVISOR)
}

/** For what is not a composable, such as a notification. */
fun Context.distanceText(meters: Int): String = if (meters < METERS_PER_KM) {
    getString(R.string.core_ui_distance_m, meters)
} else {
    getString(R.string.core_ui_distance_km, meters / KM_DIVISOR)
}

@Composable
fun paceText(secondsPerKm: Int?): String =
    secondsPerKm?.let { stringResource(R.string.core_ui_pace, formatDuration(it)) }
        ?: stringResource(R.string.core_ui_no_value)

/** The date and time where the walk happened, which may not be where the user is now. */
fun Instant.formattedIn(timeZone: String): String {
    val zone = try {
        ZoneId.of(timeZone)
    } catch (@Suppress("SwallowedException") exception: DateTimeException) {
        ZoneId.systemDefault()
    }
    return atZone(zone).format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT))
}

@StringRes
fun WalkVisibility.labelRes(): Int = when (this) {
    WalkVisibility.Everyone -> R.string.core_ui_visibility_everyone
    WalkVisibility.Friends -> R.string.core_ui_visibility_friends
    WalkVisibility.OnlyMe -> R.string.core_ui_visibility_only_me
}
