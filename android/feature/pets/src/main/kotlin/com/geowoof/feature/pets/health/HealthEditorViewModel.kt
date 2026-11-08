package com.geowoof.feature.pets.health

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geowoof.core.data.repository.HealthRepository
import com.geowoof.core.data.repository.PetsRepository
import com.geowoof.core.model.AppError
import com.geowoof.core.model.HealthEvent
import com.geowoof.core.model.HealthEventType
import com.geowoof.core.model.HealthReminder
import com.geowoof.core.model.HealthRepeat
import com.geowoof.core.model.Outcome
import com.geowoof.core.model.RepeatUnit
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

/** Which of the dialogs of the form is open. */
enum class HealthPicker { DueOn, DueAt, DoneOn, Until, Repeat, Reminder }

data class HealthEditorUiState(
    val isNew: Boolean = true,
    val isLoading: Boolean = false,
    val petName: String? = null,
    val type: HealthEventType = HealthEventType.Vaccine,
    val dueOn: LocalDate = LocalDate.now(),
    val dueAt: LocalTime? = null,
    val repeat: HealthRepeat? = defaultRepeat(HealthEventType.Vaccine),
    val reminder: HealthReminder = defaultReminder(HealthEventType.Vaccine),
    val isDone: Boolean = false,
    val doneOn: LocalDate = LocalDate.now(),
    val picker: HealthPicker? = null,
    val isTitleMissing: Boolean = false,
    val isSaving: Boolean = false,
    val isConfirmingDelete: Boolean = false,
    val error: AppError? = null,
    /** Saved with a reminder: the notifications are asked for before leaving, if they have not been yet. */
    val asksForNotifications: Boolean = false,
    /** Saved or deleted: the screen goes back. */
    val isFinished: Boolean = false,
)

/**
 * What each type usually is: a vaccine comes back every year and is reminded a week before, to make the appointment;
 * a deworming every three months, on the day; a medicine every day, on the day; an appointment the day before.
 */
internal fun defaultRepeat(type: HealthEventType): HealthRepeat? = when (type) {
    HealthEventType.Vaccine -> HealthRepeat(1, RepeatUnit.Years)
    HealthEventType.Deworming -> HealthRepeat(EVERY_THREE, RepeatUnit.Months)
    HealthEventType.Medication -> HealthRepeat(1, RepeatUnit.Days)
    HealthEventType.VetVisit, HealthEventType.Other -> null
}

internal fun defaultReminder(type: HealthEventType): HealthReminder = when (type) {
    HealthEventType.Vaccine -> HealthReminder.WeekBefore
    HealthEventType.VetVisit -> HealthReminder.DayBefore
    HealthEventType.Deworming, HealthEventType.Medication, HealthEventType.Other -> HealthReminder.OnTheDay
}

/** The repetitions offered in the form; one from the server that is not here is offered too. */
internal val RepeatChoices: List<HealthRepeat?> = listOf(
    null,
    HealthRepeat(1, RepeatUnit.Days),
    HealthRepeat(1, RepeatUnit.Weeks),
    HealthRepeat(1, RepeatUnit.Months),
    HealthRepeat(EVERY_THREE, RepeatUnit.Months),
    HealthRepeat(EVERY_SIX, RepeatUnit.Months),
    HealthRepeat(1, RepeatUnit.Years),
)

private const val EVERY_THREE = 3
private const val EVERY_SIX = 6

/** Adds something to the health of a pet, or changes or deletes it. Without an eventId, it is new. */
@Suppress("TooManyFunctions")
@HiltViewModel
class HealthEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val health: HealthRepository,
    pets: PetsRepository,
) : ViewModel() {
    private val petId: String = checkNotNull(savedStateHandle["petId"])
    private val eventId: String? = savedStateHandle["eventId"]
    private var original: HealthEvent? = null
    private var hasChosenRepeat = false
    private var hasChosenReminder = false
    private val state = MutableStateFlow(HealthEditorUiState(isNew = eventId == null, isLoading = eventId != null))

    val uiState: StateFlow<HealthEditorUiState> = state.asStateFlow()

    val title = TextFieldState()

    val notes = TextFieldState()

    init {
        viewModelScope.launch {
            state.update { it.copy(petName = pets.observePet(petId).first()?.name) }
        }
        eventId?.let(::load)
    }

    private fun load(id: String) {
        viewModelScope.launch {
            val event = health.event(id)
            original = event
            if (event == null) {
                state.update { it.copy(isLoading = false, isFinished = true) }
                return@launch
            }
            title.setTextAndPlaceCursorAtEnd(event.title)
            notes.setTextAndPlaceCursorAtEnd(event.notes.orEmpty())
            hasChosenRepeat = true
            hasChosenReminder = true
            state.update {
                it.copy(
                    isLoading = false,
                    type = event.type,
                    dueOn = event.dueOn,
                    dueAt = event.dueAt,
                    repeat = event.repeat,
                    reminder = event.reminder,
                    isDone = event.isDone,
                    doneOn = event.doneOn ?: minOf(event.dueOn, LocalDate.now()),
                )
            }
        }
    }

    /** In something new, the type brings what it usually is, unless that was chosen already. */
    fun onTypeChange(type: HealthEventType) = state.update {
        it.copy(
            type = type,
            repeat = if (it.isNew && !hasChosenRepeat) defaultRepeat(type) else it.repeat,
            reminder = if (it.isNew && !hasChosenReminder) defaultReminder(type) else it.reminder,
        )
    }

    fun onSuggestion(text: String) {
        title.setTextAndPlaceCursorAtEnd(text)
        state.update { it.copy(isTitleMissing = false) }
    }

    fun onPick(picker: HealthPicker?) = state.update { it.copy(picker = picker) }

    fun onDueOnChange(date: LocalDate) = state.update {
        val until = it.repeat?.until?.takeUnless(date::isAfter)
        it.copy(dueOn = date, repeat = it.repeat?.copy(until = until), picker = null)
    }

    fun onDueAtChange(time: LocalTime?) = state.update { it.copy(dueAt = time, picker = null) }

    fun onRepeatChange(repeat: HealthRepeat?) {
        hasChosenRepeat = true
        state.update { it.copy(repeat = repeat?.copy(until = it.repeat?.until), picker = null) }
    }

    fun onUntilChange(until: LocalDate?) = state.update {
        it.copy(repeat = it.repeat?.copy(until = until), picker = null)
    }

    fun onReminderChange(reminder: HealthReminder) {
        hasChosenReminder = true
        state.update { it.copy(reminder = reminder, picker = null) }
    }

    /** Something that was done goes to the history: on the day it was due, if that has gone by, and else today. */
    fun onDoneChange(isDone: Boolean) = state.update {
        it.copy(isDone = isDone, doneOn = if (isDone) minOf(it.dueOn, LocalDate.now()) else it.doneOn)
    }

    fun onDoneOnChange(date: LocalDate) = state.update { it.copy(doneOn = date, picker = null) }

    fun onErrorShown() = state.update { it.copy(error = null) }

    fun save() {
        val current = state.value.takeUnless { it.isSaving || it.isLoading } ?: return
        val text = title.text.toString().trim()
        if (text.isEmpty()) {
            state.update { it.copy(isTitleMissing = true) }
            return
        }
        val event = HealthEvent(
            id = original?.id ?: health.newId(),
            petId = petId,
            type = current.type,
            title = text,
            dueOn = current.dueOn,
            dueAt = current.dueAt,
            doneOn = current.doneOn.takeIf { current.isDone && original?.isDone == true },
            repeat = current.repeat,
            reminder = current.reminder,
            notes = notes.text.toString().trim().ifEmpty { null },
            updatedAt = Instant.now(),
        )
        state.update { it.copy(isSaving = true, isTitleMissing = false, error = null) }
        viewModelScope.launch {
            // Marking it as done now goes through completing it, so that what repeats makes the next one.
            val marksDone = current.isDone && original?.isDone != true
            val result = when (val saved = health.save(event)) {
                is Outcome.Success -> if (marksDone) health.complete(saved.value, current.doneOn) else saved
                is Outcome.Failure -> saved
            }
            state.update {
                when (result) {
                    is Outcome.Success -> it.copy(
                        isSaving = false,
                        asksForNotifications = !current.isDone && current.reminder != HealthReminder.None,
                        isFinished = current.isDone || current.reminder == HealthReminder.None,
                    )

                    is Outcome.Failure -> it.copy(isSaving = false, error = result.error)
                }
            }
        }
    }

    /** Once the notifications were asked for, or there was nothing to ask. */
    fun onNotificationsAnswered() = state.update { it.copy(asksForNotifications = false, isFinished = true) }

    fun onRequestDelete() = state.update { it.copy(isConfirmingDelete = true) }

    fun onDismissDelete() = state.update { it.copy(isConfirmingDelete = false) }

    fun onConfirmDelete() {
        val event = original ?: return
        state.update { it.copy(isConfirmingDelete = false, isSaving = true) }
        viewModelScope.launch {
            when (val result = health.delete(event)) {
                is Outcome.Success -> state.update { it.copy(isSaving = false, isFinished = true) }
                is Outcome.Failure -> state.update { it.copy(isSaving = false, error = result.error) }
            }
        }
    }
}
