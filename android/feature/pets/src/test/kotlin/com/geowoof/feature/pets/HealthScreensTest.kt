package com.geowoof.feature.pets

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.core.model.HealthEventType
import com.geowoof.core.model.HealthRepeat
import com.geowoof.core.model.RepeatUnit
import com.geowoof.core.testing.TestHealth
import com.geowoof.core.testing.TestPets
import com.geowoof.core.ui.formatted
import com.geowoof.feature.pets.detail.PetDetailActions
import com.geowoof.feature.pets.detail.PetDetailContent
import com.geowoof.feature.pets.detail.PetDetailUiState
import com.geowoof.feature.pets.health.HealthActions
import com.geowoof.feature.pets.health.HealthContent
import com.geowoof.feature.pets.health.HealthEditorActions
import com.geowoof.feature.pets.health.HealthEditorContent
import com.geowoof.feature.pets.health.HealthEditorUiState
import com.geowoof.feature.pets.health.HealthPicker
import com.geowoof.feature.pets.health.HealthUiState
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w412dp-h915dp")
class HealthScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val calls = mutableListOf<String>()
    private val today = LocalDate.now()

    private fun show(uiState: HealthUiState) {
        composeRule.setContent {
            GeoWoofTheme(dynamicColor = false) {
                HealthContent(
                    uiState = uiState,
                    actions = HealthActions(
                        onAdd = { calls += "add" },
                        onOpenEvent = { calls += "open:$it" },
                        onComplete = { calls += "complete:${it.id}" },
                    ),
                )
            }
        }
    }

    @Test
    fun what_is_late_comes_first_then_what_is_next_and_then_what_was_done() {
        show(
            HealthUiState(
                petName = "Luna",
                isLoading = false,
                overdue = listOf(
                    TestHealth.event(
                        id = "pill",
                        type = HealthEventType.Medication,
                        title = "Pastilla",
                        dueOn = today.minusDays(2),
                    ),
                ),
                upcoming = listOf(
                    TestHealth.event(
                        id = "rabies",
                        dueOn = today.plusDays(1),
                        repeat = HealthRepeat(1, RepeatUnit.Years),
                    ),
                ),
                history = listOf(
                    TestHealth.event(
                        id = "old",
                        title = "Polivalente",
                        dueOn = today.minusDays(30),
                        doneOn = today.minusDays(30),
                    ),
                ),
            ),
        )

        composeRule.onNodeWithText("Luna's health").assertIsDisplayed()
        composeRule.onNodeWithText("Overdue").assertIsDisplayed()
        composeRule.onNodeWithText("2 days late").assertIsDisplayed()
        composeRule.onNodeWithText("Tomorrow · Every year").assertIsDisplayed()
        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Polivalente"))
        composeRule.onNodeWithText("Done on ${today.minusDays(30).formatted()}").assertIsDisplayed()
    }

    @Test
    fun what_is_pending_is_done_with_a_touch_and_opens_with_another() {
        show(HealthUiState(isLoading = false, upcoming = listOf(TestHealth.event(id = "rabies"))))

        composeRule.onNodeWithContentDescription("Mark Rabia as done").performClick()
        composeRule.onNodeWithText("Rabia").performClick()

        assertEquals(listOf("complete:rabies", "open:rabies"), calls)
    }

    @Test
    fun without_anything_it_says_what_it_is_for_and_how_to_start() {
        show(HealthUiState(isLoading = false))

        composeRule.onNodeWithText("Nothing yet").assertIsDisplayed()
        composeRule.onNodeWithText("Add").performClick()

        assertEquals(listOf("add"), calls)
    }

    /** The reminders cannot arrive: it says so, with the way to the settings, and nothing about when. */
    @Test
    fun with_the_notifications_off_it_says_that_the_reminders_cannot_arrive() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        shadowOf(context.getSystemService(NotificationManager::class.java)).setNotificationsEnabled(false)
        ShadowAlarmManager.setCanScheduleExactAlarms(false)

        show(HealthUiState(isLoading = false, upcoming = listOf(TestHealth.event(id = "rabies"))))

        composeRule.onNodeWithText(
            "The reminders cannot arrive: the notifications of GeoWoof are off.",
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Turn them on").assertIsDisplayed()
        composeRule.onNodeWithText(LATE, substring = true).assertDoesNotExist()
    }

    /** From Android 14 the alarms at their time start off: the reminders may be late, and it says where to fix it. */
    @Test
    fun without_the_alarms_at_their_time_it_says_that_the_reminders_may_be_late() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)

        show(HealthUiState(isLoading = false, upcoming = listOf(TestHealth.event(id = "rabies"))))

        composeRule.onNodeWithText(LATE, substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Allow").performClick()

        val application = ApplicationProvider.getApplicationContext<Application>()
        val started = shadowOf(application).nextStartedActivity
        assertEquals("android.settings.REQUEST_SCHEDULE_EXACT_ALARM", started.action)
        assertEquals("package:${application.packageName}", started.dataString)
    }

    @Test
    fun with_the_alarms_at_their_time_allowed_it_says_nothing() {
        show(HealthUiState(isLoading = false, upcoming = listOf(TestHealth.event(id = "rabies"))))

        composeRule.onNodeWithText("Rabia").assertIsDisplayed()
        composeRule.onNodeWithText(LATE, substring = true).assertDoesNotExist()
    }

    @Before
    fun allowTheAlarmsAtTheirTime() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    private companion object {
        const val LATE = "up to an hour late"
    }
}

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w412dp-h915dp")
class HealthEditorScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val calls = mutableListOf<String>()

    private fun show(uiState: HealthEditorUiState = HealthEditorUiState()) {
        composeRule.setContent {
            GeoWoofTheme(dynamicColor = false) {
                HealthEditorContent(
                    uiState = uiState,
                    title = TextFieldState(),
                    notes = TextFieldState(),
                    actions = HealthEditorActions(
                        onTypeChange = { calls += "type:$it" },
                        onSuggestion = { calls += "suggestion:$it" },
                        onPick = { calls += "pick:$it" },
                        onDoneChange = { calls += "done:$it" },
                        onSave = { calls += "save" },
                    ),
                )
            }
        }
    }

    @Test
    fun a_vaccine_offers_what_is_usually_written_and_repeats_every_year() {
        show()

        composeRule.onNodeWithText("Every year").assertIsDisplayed()
        composeRule.onNodeWithText("A week before").assertIsDisplayed()
        composeRule.onNodeWithText("Rabies").performClick()
        composeRule.onNodeWithText("Medication").performClick()
        composeRule.onNodeWithText("Repeats").performClick()

        assertEquals(listOf("suggestion:Rabies", "type:Medication", "pick:Repeat"), calls)
    }

    @Test
    fun without_saying_what_it_is_it_asks_for_it() {
        show(HealthEditorUiState(isTitleMissing = true))

        composeRule.onNodeWithText("Write what it is").assertIsDisplayed()
    }

    @Test
    fun something_that_was_done_says_when() {
        val day = LocalDate.of(2026, 3, 10)
        show(HealthEditorUiState(isDone = true, doneOn = day))

        composeRule.onNodeWithText("Done on").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Already done").performScrollTo().performClick()

        assertEquals(listOf("done:false"), calls)
    }

    @Test
    fun the_time_opens_its_dialog() {
        show(HealthEditorUiState(picker = HealthPicker.DueAt))

        composeRule.onNodeWithText("OK").assertIsDisplayed()
    }
}

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w412dp-h915dp")
class PetHealthSectionTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val calls = mutableListOf<String>()

    private fun show(uiState: PetDetailUiState) {
        composeRule.setContent {
            GeoWoofTheme(dynamicColor = false) {
                PetDetailContent(
                    uiState = uiState,
                    weightText = TextFieldState(),
                    actions = PetDetailActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}),
                    onBack = {},
                    onEdit = {},
                    onOpenWalks = {},
                    onOpenHealth = { calls += "health:$it" },
                    onAddHealth = { calls += "add:$it" },
                )
            }
        }
    }

    private fun scrollTo(text: String) = composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text))

    @Test
    fun the_page_of_a_pet_shows_what_is_due_and_opens_all_its_health() {
        show(PetDetailUiState(pet = TestPets.pet(), healthPending = listOf(TestHealth.event(id = "rabies"))))

        scrollTo("Rabia")
        composeRule.onNodeWithText("Rabia").assertIsDisplayed()
        composeRule.onNodeWithText("See all").performClick()

        assertEquals(listOf("health:luna"), calls)
    }

    @Test
    fun without_anything_due_it_says_what_it_is_for_and_how_to_add_the_first() {
        show(PetDetailUiState(pet = TestPets.pet()))

        composeRule.onNodeWithText("Vaccines, deworming, appointments and medicines, and a reminder when they are due.")
            .assertExists()
        scrollTo("Add")
        composeRule.onNodeWithText("Add").performClick()

        assertEquals(listOf("add:luna"), calls)
    }
}
