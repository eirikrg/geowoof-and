package com.geowoof.core.notifications

import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.geowoof.core.model.HealthReminder
import com.geowoof.core.model.HealthReminderItem
import com.geowoof.core.model.HealthRepeat
import com.geowoof.core.model.RepeatUnit
import com.geowoof.core.testing.FakeHealthRepository
import com.geowoof.core.testing.FakePetsRepository
import com.geowoof.core.testing.TestHealth
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.TimeZone

/** The alarms of the phone follow what Room has of the health of the pets. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class HealthRemindersTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val alarms get() = shadowOf(context.getSystemService(AlarmManager::class.java))
    private val zone = ZoneId.of("Europe/Madrid")
    private val today = LocalDate.of(2026, 11, 8)
    private val clock = Clock.fixed(today.atTime(10, 0).atZone(zone).toInstant(), ZoneOffset.UTC)
    private val previousZone = TimeZone.getDefault()

    @Before
    fun setUp() = TimeZone.setDefault(TimeZone.getTimeZone(zone))

    @After
    fun tearDown() = TimeZone.setDefault(previousZone)

    private fun reminders(health: FakeHealthRepository) = HealthReminders(context, health, FakePetsRepository(), clock)

    private fun at(date: LocalDate, time: LocalTime) =
        LocalDateTime.of(date, time).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun each_pending_reminder_is_an_alarm_at_its_time() = runTest {
        val health = FakeHealthRepository(
            listOf(
                TestHealth.event(id = "rabies", dueOn = today.plusDays(12), reminder = HealthReminder.DayBefore),
                TestHealth.event(
                    id = "visit",
                    dueOn = today.plusDays(2),
                    dueAt = LocalTime.of(17, 30),
                    reminder = HealthReminder.OnTheDay,
                ),
                TestHealth.event(id = "silent", reminder = HealthReminder.None),
                TestHealth.event(id = "done", doneOn = today),
            ),
        )

        reminders(health).sync()

        val times = alarms.scheduledAlarms.map { it.triggerAtMs }.sorted()
        assertEquals(
            listOf(at(today.plusDays(2), LocalTime.of(16, 30)), at(today.plusDays(11), LocalTime.of(9, 0))),
            times,
        )
    }

    @Test
    fun what_is_done_or_deleted_takes_its_alarm_with_it() = runTest {
        val rabies = TestHealth.event(id = "rabies", dueOn = today.plusDays(12))
        val visit = TestHealth.event(id = "visit", dueOn = today.plusDays(3))
        val health = FakeHealthRepository(listOf(rabies, visit))
        val reminders = reminders(health)
        reminders.sync()
        assertEquals(2, alarms.scheduledAlarms.size)

        health.complete(rabies, today)
        health.delete(visit)
        reminders.sync()

        assertTrue(alarms.scheduledAlarms.isEmpty())
    }

    @Test
    fun what_repeats_gets_the_alarm_of_its_next_one_once_it_is_done() = runTest {
        val pill = TestHealth.event(
            id = "pill",
            dueOn = today,
            dueAt = LocalTime.of(20, 0),
            repeat = HealthRepeat(1, RepeatUnit.Days),
            reminder = HealthReminder.OnTheDay,
        )
        val health = FakeHealthRepository(listOf(pill))
        val reminders = reminders(health)

        health.complete(pill, today)
        reminders.sync()

        assertEquals(
            listOf(at(today.plusDays(1), LocalTime.of(19, 0))),
            alarms.scheduledAlarms.map {
                it.triggerAtMs
            },
        )
    }

    @Test
    fun the_alarm_goes_to_the_receiver_of_the_reminders_with_the_day_it_is_for() = runTest {
        val health = FakeHealthRepository(listOf(TestHealth.event(id = "rabies", dueOn = today.plusDays(12))))

        reminders(health).sync()

        // Robolectric has no other way to read it.
        @Suppress("DEPRECATION")
        val operation = alarms.scheduledAlarms.single().operation
        val intent = shadowOf(operation).savedIntent
        assertEquals(HealthReminderReceiver::class.java.name, intent.component?.className)
        assertEquals("rabies", HealthReminders.eventId(intent))
        assertEquals(today.plusDays(12).toEpochDay(), intent.getLongExtra(HealthReminders.EXTRA_DUE_ON, 0))
    }

    /** Allowed, they ring at their time; if not, as from Android 14 by default, they are still set, idle or not. */
    @Test
    fun the_alarms_are_at_their_time_only_where_it_is_allowed() = runTest {
        val health = FakeHealthRepository(listOf(TestHealth.event(id = "rabies", dueOn = today.plusDays(12))))
        val reminders = reminders(health)

        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        reminders.sync()
        val onTime = alarms.scheduledAlarms.single()
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        reminders.sync()
        val late = alarms.scheduledAlarms.single()

        assertEquals(ShadowAlarmManager.WINDOW_EXACT, onTime.windowLengthMs)
        assertNotEquals(ShadowAlarmManager.WINDOW_EXACT, late.windowLengthMs)
        assertEquals(onTime.triggerAtMs, late.triggerAtMs)
        assertTrue(onTime.isAllowWhileIdle && late.isAllowWhileIdle)
    }
}

/** The notification says whose it is, what, and when, and opens the health of the pet. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class HealthReminderNotificationTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val presenter = AndroidNotificationPresenter(context)
    private val system get() = shadowOf(context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)

    @Before
    fun setUp() {
        val packages = shadowOf(context.packageManager)
        val launcher = ComponentName(context, "com.geowoof.Launcher")
        packages.addActivityIfNotPresent(launcher)
        packages.addIntentFilterForActivity(
            launcher,
            IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) },
        )
    }

    private fun show(dueOn: LocalDate, dueAt: LocalTime? = null) = presenter.showHealthReminder(
        HealthReminderItem(TestHealth.event(id = "rabies", petId = "luna-id", dueAt = dueAt), petName = "Luna"),
        dueOn,
    )

    @Test
    fun it_says_the_pet_what_and_tomorrow_with_its_time() {
        show(LocalDate.now().plusDays(1), LocalTime.of(17, 30))

        val notification = system.allNotifications.single()
        assertEquals("Luna: Rabia", shadowOf(notification).contentTitle)
        assertEquals("Tomorrow at 5:30 PM", shadowOf(notification).contentText)
        assertEquals("geowoof://pet-health/luna-id", shadowOf(notification.contentIntent).savedIntent.dataString)
    }

    @Test
    fun further_away_it_says_the_day() {
        val day = LocalDate.now().plusDays(7)

        show(day)

        val expected = "On ${day.month.getDisplayName(
            java.time.format.TextStyle.FULL,
            java.util.Locale.US,
        )} ${day.dayOfMonth}"
        assertEquals(expected, shadowOf(system.allNotifications.single()).contentText)
    }

    @Test
    fun today_without_a_time_says_today() {
        show(LocalDate.now())

        assertEquals("Today", shadowOf(system.allNotifications.single()).contentText)
    }
}
