package com.geowoof.core.ui

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect

/**
 * Whether the reminders can ring at their time, again each time the screen comes back: it changes in the settings.
 * Without it, from Android 12, the system may bring them up to an hour late; from Android 14 it starts off.
 */
@Composable
fun rememberExactAlarmsAllowed(): Boolean {
    val context = LocalContext.current
    var isAllowed by remember { mutableStateOf(context.mayScheduleExactAlarms()) }
    LifecycleResumeEffect(context) {
        isAllowed = context.mayScheduleExactAlarms()
        onPauseOrDispose {}
    }
    return isAllowed
}

/** The setting of the system where someone allows the alarms at their time. Before Android 12 there is none. */
fun Context.openExactAlarmSettings() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    startActivity(
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:$packageName".toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

private fun Context.mayScheduleExactAlarms(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
    getSystemService<AlarmManager>()?.canScheduleExactAlarms() != false
