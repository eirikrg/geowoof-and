package com.geowoof.core.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.edit
import androidx.core.net.toUri
import com.geowoof.core.common.ApplicationScope
import com.geowoof.core.data.repository.HealthRepository
import com.geowoof.core.data.repository.PetsRepository
import com.geowoof.core.model.HealthReminderItem
import com.geowoof.core.model.PlannedReminder
import com.geowoof.core.model.nextReminder
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/*
 * The reminders of the health of the pets are alarms of the phone, made from what Room has: the server only keeps when
 * each one wants to be reminded. Room is the source of truth, and the alarms follow it while the process lives. When it
 * does not, the system wakes the app after a reboot, a change of the clock or of the time zone, and an update of the
 * app, which wipe or move the alarms, and they are made again. A reminder that goes off also plans the next ones.
 *
 * They are exact if the system lets the app (Android 11 or older, or if the person allowed it), and otherwise as close
 * as the system wants, which in Doze is a few minutes: enough for a vaccine or an appointment, and it asks for no
 * permission that the person would have to grant in the settings.
 */

/** Keeps the alarms of the phone as the health of the pets says. */
@Singleton
class HealthReminders @Inject internal constructor(
    @ApplicationContext private val context: Context,
    private val health: HealthRepository,
    private val pets: PetsRepository,
    private val clock: Clock,
) {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val scheduled = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val planning = Mutex()

    /**
     * For as long as the process lives: the alarms follow every change, and the health of your pets is asked for each
     * time the list of pets changes, so that a phone where you just signed in reminds of it too.
     */
    fun start(scope: CoroutineScope) {
        scope.launch { health.pendingReminders().collect { plan(it) } }
        scope.launch {
            pets.myPets.map { list -> list.map { it.id }.toSet() }.distinctUntilChanged().collect { ids ->
                if (ids.isNotEmpty()) health.refreshAll()
            }
        }
    }

    /** Once, from what Room has now. */
    suspend fun sync() = plan(health.pendingReminders().first())

    private suspend fun plan(items: List<HealthReminderItem>) = planning.withLock {
        val now = clock.instant()
        // The zone of the phone now: after travelling, the reminders are at the local time of where it is.
        val zone = ZoneId.systemDefault()
        val planned = items
            .mapNotNull { it.event.nextReminder(now, zone) }
            .sortedBy { it.at }
            .take(MAX_ALARMS)
        val kept = planned.map { it.eventId }.toSet()
        scheduled.getStringSet(KEY_SCHEDULED, emptySet()).orEmpty().filterNot(kept::contains).forEach(::cancel)
        planned.forEach(::schedule)
        scheduled.edit { putStringSet(KEY_SCHEDULED, kept) }
    }

    private fun schedule(reminder: PlannedReminder) {
        val intent = alarmIntent(reminder.eventId).putExtra(EXTRA_DUE_ON, reminder.dueOn.toEpochDay())
        val pending = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val at = reminder.at.toEpochMilli()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        }
    }

    private fun cancel(eventId: String) {
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        PendingIntent.getBroadcast(context, 0, alarmIntent(eventId), flags)?.let { pending ->
            alarms.cancel(pending)
            pending.cancel()
        }
    }

    /** One per event: the link makes them different for the system, and it does not open anything. */
    private fun alarmIntent(eventId: String) = Intent(context, HealthReminderReceiver::class.java)
        .setAction(ACTION_REMIND)
        .setData("geowoof-health://reminder/$eventId".toUri())

    internal companion object {
        const val ACTION_REMIND = "com.geowoof.health.REMIND"
        const val EXTRA_DUE_ON = "dueOn"
        private const val PREFERENCES = "health_reminders"
        private const val KEY_SCHEDULED = "scheduled"

        /** The system allows an app 500 alarms. The ones further away are planned when the nearer ones go off. */
        const val MAX_ALARMS = 100

        fun eventId(intent: Intent): String? = intent.data?.lastPathSegment
    }
}

/** What the receivers need, which the system creates without Hilt. */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface HealthRemindersEntryPoint {
    fun reminders(): HealthReminders

    fun health(): HealthRepository

    fun presenter(): NotificationPresenter

    @ApplicationScope
    fun scope(): CoroutineScope
}

private fun Context.healthReminders(): HealthRemindersEntryPoint =
    EntryPointAccessors.fromApplication(applicationContext, HealthRemindersEntryPoint::class.java)

/** A reminder goes off: it is shown, if it is still pending, and the next ones are planned. */
class HealthReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != HealthReminders.ACTION_REMIND) return
        val eventId = HealthReminders.eventId(intent) ?: return
        val dueOn = LocalDate.ofEpochDay(
            intent.getLongExtra(HealthReminders.EXTRA_DUE_ON, LocalDate.now().toEpochDay()),
        )
        val entryPoint = context.healthReminders()
        val result = goAsync()
        entryPoint.scope().launch {
            try {
                entryPoint.health().reminderItem(eventId)
                    ?.takeIf { !it.event.isDone }
                    ?.let { entryPoint.presenter().showHealthReminder(it, dueOn) }
                entryPoint.reminders().sync()
            } finally {
                result.finish()
            }
        }
    }
}

/** After a reboot, a change of the clock or the time zone, or an update of the app, the alarms are made again. */
class HealthRemindersRestorer : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in RESTORING_ACTIONS) return
        val entryPoint = context.healthReminders()
        val result = goAsync()
        entryPoint.scope().launch {
            try {
                entryPoint.reminders().sync()
            } finally {
                result.finish()
            }
        }
    }

    private companion object {
        val RESTORING_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            // When the alarms at their time are allowed, the ones that were set late are set again. Written out
            // because the constant is from Android 12; before it, nobody sends it.
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
        )
    }
}
