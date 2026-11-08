package com.geowoof.feature.pets

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.SavedStateHandle
import com.geowoof.core.model.AppError
import com.geowoof.core.model.HealthEventType
import com.geowoof.core.model.HealthReminder
import com.geowoof.core.model.HealthRepeat
import com.geowoof.core.model.RepeatUnit
import com.geowoof.core.testing.FakeHealthRepository
import com.geowoof.core.testing.FakePetsRepository
import com.geowoof.core.testing.MainDispatcherRule
import com.geowoof.core.testing.TestHealth
import com.geowoof.core.testing.TestPets
import com.geowoof.feature.pets.health.HealthEditorViewModel
import com.geowoof.feature.pets.health.HealthViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class HealthViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val today = LocalDate.now()
    private val pets = FakePetsRepository(listOf(TestPets.pet()))

    private fun TestScope.health(health: FakeHealthRepository): HealthViewModel {
        val viewModel = HealthViewModel(SavedStateHandle(mapOf("petId" to "luna")), health, pets)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        return viewModel
    }

    @Test
    fun what_is_late_what_comes_and_what_was_done_go_apart_each_in_its_order() = runTest {
        val repository = FakeHealthRepository(
            listOf(
                TestHealth.event(id = "far", dueOn = today.plusDays(30)),
                TestHealth.event(id = "late", dueOn = today.minusDays(3)),
                TestHealth.event(id = "near", dueOn = today.plusDays(2)),
                TestHealth.event(id = "old", dueOn = today.minusDays(90), doneOn = today.minusDays(90)),
                TestHealth.event(id = "recent", dueOn = today.minusDays(10), doneOn = today.minusDays(9)),
                TestHealth.event(id = "of-toby", petId = "toby"),
            ),
        )

        val state = health(repository).uiState.value

        assertEquals("Luna", state.petName)
        assertEquals(listOf("late"), state.overdue.map { it.id })
        assertEquals(listOf("near", "far"), state.upcoming.map { it.id })
        assertEquals(listOf("recent", "old"), state.history.map { it.id })
        assertTrue(state.hasReminders)
        assertEquals(listOf("refresh:luna"), repository.calls)
    }

    @Test
    fun marking_as_done_moves_it_to_the_history_and_brings_the_next_one() = runTest {
        val vaccine = TestHealth.event(id = "rabies", dueOn = today, repeat = HealthRepeat(1, RepeatUnit.Years))
        val repository = FakeHealthRepository(listOf(vaccine))
        val viewModel = health(repository)

        viewModel.onComplete(vaccine)

        val state = viewModel.uiState.value
        assertEquals(listOf("rabies"), state.history.map { it.id })
        assertEquals(today.plusYears(1), state.upcoming.single().dueOn)
        assertTrue(state.completing.isEmpty())
    }

    @Test
    fun a_failure_is_said_and_nothing_moves() = runTest {
        val vaccine = TestHealth.event(id = "rabies")
        val repository = FakeHealthRepository(listOf(vaccine))
        val viewModel = health(repository)
        repository.failure = AppError.Network

        viewModel.onComplete(vaccine)

        assertEquals(AppError.Network, viewModel.uiState.value.error)
        assertEquals(listOf("rabies"), viewModel.uiState.value.upcoming.map { it.id })
        viewModel.onErrorShown()
        assertNull(viewModel.uiState.value.error)
    }
}

class HealthEditorViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val today = LocalDate.now()
    private val pets = FakePetsRepository(listOf(TestPets.pet()))

    private fun editor(health: FakeHealthRepository, eventId: String? = null) = HealthEditorViewModel(
        SavedStateHandle(listOfNotNull("petId" to "luna", eventId?.let { "eventId" to it }).toMap()),
        health,
        pets,
    )

    @Test
    fun each_type_brings_what_it_usually_is_until_it_is_chosen() {
        val viewModel = editor(FakeHealthRepository())
        assertEquals(HealthRepeat(1, RepeatUnit.Years), viewModel.uiState.value.repeat)
        assertEquals(HealthReminder.WeekBefore, viewModel.uiState.value.reminder)

        viewModel.onTypeChange(HealthEventType.Deworming)
        assertEquals(HealthRepeat(3, RepeatUnit.Months), viewModel.uiState.value.repeat)
        assertEquals(HealthReminder.OnTheDay, viewModel.uiState.value.reminder)

        viewModel.onRepeatChange(null)
        viewModel.onTypeChange(HealthEventType.Medication)
        assertNull(viewModel.uiState.value.repeat)
    }

    @Test
    fun without_saying_what_it_is_nothing_is_saved() {
        val repository = FakeHealthRepository()
        val viewModel = editor(repository)

        viewModel.save()

        assertTrue(viewModel.uiState.value.isTitleMissing)
        assertTrue(repository.calls.isEmpty())
    }

    @Test
    fun something_with_a_reminder_asks_for_the_notifications_before_leaving() = runTest {
        val repository = FakeHealthRepository()
        val viewModel = editor(repository)
        viewModel.onSuggestion("Rabia")
        viewModel.notes.setTextAndPlaceCursorAtEnd("  Lote 1234 ")

        viewModel.save()

        val saved = repository.events.single()
        assertEquals("Rabia", saved.title)
        assertEquals("Lote 1234", saved.notes)
        assertNull(saved.doneOn)
        assertTrue(viewModel.uiState.value.asksForNotifications)
        assertFalse(viewModel.uiState.value.isFinished)
        viewModel.onNotificationsAnswered()
        assertTrue(viewModel.uiState.value.isFinished)
    }

    /** What repeats and was done already makes its next one, as completing it from the list does. */
    @Test
    fun something_that_was_done_already_goes_to_the_history_and_brings_the_next_one() = runTest {
        val repository = FakeHealthRepository()
        val viewModel = editor(repository)
        viewModel.title.setTextAndPlaceCursorAtEnd("Rabia")
        viewModel.onDueOnChange(today.minusDays(5))
        viewModel.onDoneChange(true)

        viewModel.save()

        assertEquals(listOf("save:new-0", "complete:new-0:${today.minusDays(5)}"), repository.calls)
        assertEquals(today.minusDays(5).plusYears(1), repository.events.single { !it.isDone }.dueOn)
        assertTrue(viewModel.uiState.value.isFinished)
        assertFalse(viewModel.uiState.value.asksForNotifications)
    }

    @Test
    fun an_event_opens_as_it_is_and_can_be_deleted() = runTest {
        val visit = TestHealth.event(
            id = "visit",
            type = HealthEventType.VetVisit,
            title = "Revisión",
            notes = "En ayunas",
        )
        val repository = FakeHealthRepository(listOf(visit))
        val viewModel = editor(repository, eventId = "visit")

        assertFalse(viewModel.uiState.value.isNew)
        assertEquals(HealthEventType.VetVisit, viewModel.uiState.value.type)
        assertEquals("Revisión", viewModel.title.text.toString())
        assertEquals("En ayunas", viewModel.notes.text.toString())

        viewModel.onRequestDelete()
        viewModel.onConfirmDelete()

        assertTrue(repository.events("luna").first().isEmpty())
        assertTrue(viewModel.uiState.value.isFinished)
    }

    @Test
    fun the_end_of_a_repetition_cannot_be_before_the_first_time() {
        val viewModel = editor(FakeHealthRepository())
        viewModel.onDueOnChange(today)
        viewModel.onUntilChange(today.plusDays(10))

        viewModel.onDueOnChange(today.plusDays(20))

        assertNull(viewModel.uiState.value.repeat?.until)
    }
}
