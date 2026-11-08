package com.geowoof.feature.pets.health

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geowoof.core.data.repository.HealthRepository
import com.geowoof.core.data.repository.PetsRepository
import com.geowoof.core.model.AppError
import com.geowoof.core.model.HealthEvent
import com.geowoof.core.model.HealthReminder
import com.geowoof.core.model.Outcome
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** The health of a pet in three parts: what is late, what comes next, and what was done. */
data class HealthUiState(
    val petName: String? = null,
    val overdue: List<HealthEvent> = emptyList(),
    val upcoming: List<HealthEvent> = emptyList(),
    val history: List<HealthEvent> = emptyList(),
    /** Until Room answers the first time. */
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: AppError? = null,
    /** Those being marked as done, which do not take a second touch. */
    val completing: Set<String> = emptySet(),
) {
    val isEmpty: Boolean get() = overdue.isEmpty() && upcoming.isEmpty() && history.isEmpty()

    /** Something pending reminds: then it matters whether the notifications are on. */
    val hasReminders: Boolean get() = (overdue + upcoming).any { it.reminder != HealthReminder.None }
}

private data class HealthWork(
    val isRefreshing: Boolean = false,
    val error: AppError? = null,
    val completing: Set<String> = emptySet(),
)

@HiltViewModel
class HealthViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val health: HealthRepository,
    pets: PetsRepository,
) : ViewModel() {
    private val petId: String = checkNotNull(savedStateHandle["petId"])
    private val actions = MutableStateFlow(HealthWork())

    val uiState: StateFlow<HealthUiState> =
        combine(pets.observePet(petId), health.events(petId), actions) { pet, events, current ->
            val today = LocalDate.now()
            val pending = events.filterNot { it.isDone }.sortedWith(compareBy({ it.dueOn }, { it.dueAt }))
            HealthUiState(
                petName = pet?.name,
                overdue = pending.filter { it.isOverdue(today) },
                upcoming = pending.filterNot { it.isOverdue(today) },
                history = events.filter { it.isDone }.sortedByDescending { it.doneOn },
                isLoading = false,
                isRefreshing = current.isRefreshing,
                error = current.error,
                completing = current.completing,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), HealthUiState())

    init {
        refresh()
    }

    /** What Room already has stays on screen while this runs. */
    fun refresh() {
        if (actions.value.isRefreshing) return
        actions.update { it.copy(isRefreshing = true, error = null) }
        viewModelScope.launch {
            val failure = (health.refresh(petId) as? Outcome.Failure)?.error
            actions.update { it.copy(isRefreshing = false, error = failure) }
        }
    }

    /** Done today. If it repeats, the next one appears in what comes next. */
    fun onComplete(event: HealthEvent) {
        if (event.isDone || event.id in actions.value.completing) return
        actions.update { it.copy(completing = it.completing + event.id) }
        viewModelScope.launch {
            val failure = (health.complete(event, LocalDate.now()) as? Outcome.Failure)?.error
            actions.update { it.copy(completing = it.completing - event.id, error = failure ?: it.error) }
        }
    }

    fun onErrorShown() = actions.update { it.copy(error = null) }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
