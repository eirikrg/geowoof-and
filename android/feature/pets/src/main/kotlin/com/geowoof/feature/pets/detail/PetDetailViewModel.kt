package com.geowoof.feature.pets.detail

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geowoof.core.data.repository.PetsRepository
import com.geowoof.core.data.repository.WalksRepository
import com.geowoof.core.domain.ParseWeightUseCase
import com.geowoof.core.domain.validatePetDate
import com.geowoof.core.model.AppError
import com.geowoof.core.model.Outcome
import com.geowoof.core.model.Pet
import com.geowoof.core.model.PetDateIssue
import com.geowoof.core.model.WalkTotals
import com.geowoof.core.model.WeightEntry
import com.geowoof.core.model.WeightIssue
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

/** The dialog to add or correct a measurement. The weight text lives in [PetDetailViewModel.weightText]. */
data class WeightEditorState(
    /** `null` when adding a new one. */
    val entryId: String? = null,
    val measuredOn: LocalDate,
    val weightIssue: WeightIssue? = null,
    val dateIssue: PetDateIssue? = null,
    val isSaving: Boolean = false,
    val error: AppError? = null,
)

data class PetDetailUiState(
    val pet: Pet? = null,
    val weights: List<WeightEntry> = emptyList(),
    val walkTotals: WalkTotals? = null,
    val isRefreshing: Boolean = false,
    val error: AppError? = null,
    val weightEditor: WeightEditorState? = null,
    val isConfirmingDelete: Boolean = false,
    val isDeleting: Boolean = false,
    /** The pet was deleted; the screen then goes back. */
    val isDeleted: Boolean = false,
)

private data class DetailActions(
    val isRefreshing: Boolean = false,
    val error: AppError? = null,
    val weightEditor: WeightEditorState? = null,
    val isConfirmingDelete: Boolean = false,
    val isDeleting: Boolean = false,
    val isDeleted: Boolean = false,
)

@HiltViewModel
class PetDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val pets: PetsRepository,
    private val walks: WalksRepository,
    private val parseWeight: ParseWeightUseCase,
) : ViewModel() {
    private val petId: String = checkNotNull(savedStateHandle["petId"])
    private val actions = MutableStateFlow(DetailActions())

    val weightText = TextFieldState()

    val uiState: StateFlow<PetDetailUiState> =
        combine(
            pets.observePet(petId),
            pets.observeWeights(petId),
            walks.observeTotals(petId),
            actions,
        ) { pet, weights, totals, current ->
            PetDetailUiState(
                pet = pet,
                weights = weights,
                walkTotals = totals,
                isRefreshing = current.isRefreshing,
                error = current.error,
                weightEditor = current.weightEditor,
                isConfirmingDelete = current.isConfirmingDelete,
                isDeleting = current.isDeleting,
                isDeleted = current.isDeleted,
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            PetDetailUiState(isRefreshing = true),
        )

    init {
        refresh()
    }

    /** What Room already has stays on screen while this runs. */
    fun refresh() {
        if (actions.value.isRefreshing) return
        actions.update { it.copy(isRefreshing = true, error = null) }
        viewModelScope.launch {
            val results = listOf(pets.refreshPet(petId), pets.refreshWeights(petId), walks.refreshTotals(petId))
            val failure = results.filterIsInstance<Outcome.Failure>().firstOrNull()?.error
            actions.update { it.copy(isRefreshing = false, error = failure) }
        }
    }

    fun onErrorShown() = actions.update { it.copy(error = null) }

    fun openAddWeight(today: LocalDate = LocalDate.now()) {
        weightText.setTextAndPlaceCursorAtEnd("")
        actions.update { it.copy(weightEditor = WeightEditorState(measuredOn = today)) }
    }

    fun openEditWeight(entry: WeightEntry) {
        weightText.setTextAndPlaceCursorAtEnd(entry.weightKg.toString())
        actions.update { it.copy(weightEditor = WeightEditorState(entryId = entry.id, measuredOn = entry.measuredOn)) }
    }

    fun onWeightDateChange(date: LocalDate) = updateEditor { it.copy(measuredOn = date, dateIssue = null) }

    fun dismissWeightEditor() = actions.update { it.copy(weightEditor = null) }

    fun saveWeight() {
        val editor = actions.value.weightEditor?.takeUnless { it.isSaving } ?: return
        val parsed = parseWeight(weightText.text.toString())
        val dateIssue = validatePetDate(editor.measuredOn)
        if (parsed.weightKg != null && dateIssue == null) {
            persistWeight(editor, parsed.weightKg!!)
        } else {
            updateEditor { it.copy(weightIssue = parsed.issue, dateIssue = dateIssue, error = null) }
        }
    }

    private fun persistWeight(editor: WeightEditorState, weightKg: Double) {
        updateEditor { it.copy(weightIssue = null, dateIssue = null, error = null, isSaving = true) }
        viewModelScope.launch {
            val result = if (editor.entryId == null) {
                pets.addWeight(petId, editor.measuredOn, weightKg)
            } else {
                pets.updateWeight(petId, editor.entryId, editor.measuredOn, weightKg)
            }
            when (result) {
                is Outcome.Success -> dismissWeightEditor()
                is Outcome.Failure -> updateEditor { it.copy(isSaving = false, error = result.error) }
            }
        }
    }

    /** Deletes the measurement being edited. */
    fun deleteWeight() {
        val entryId = actions.value.weightEditor?.entryId ?: return
        updateEditor { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            when (val result = pets.deleteWeight(petId, entryId)) {
                is Outcome.Success -> dismissWeightEditor()
                is Outcome.Failure -> updateEditor { it.copy(isSaving = false, error = result.error) }
            }
        }
    }

    fun requestDelete() = actions.update { it.copy(isConfirmingDelete = true) }

    fun dismissDelete() = actions.update { it.copy(isConfirmingDelete = false) }

    fun confirmDelete() {
        if (actions.value.isDeleting) return
        actions.update { it.copy(isConfirmingDelete = false, isDeleting = true, error = null) }
        viewModelScope.launch {
            when (val result = pets.delete(petId)) {
                is Outcome.Success -> actions.update { it.copy(isDeleting = false, isDeleted = true) }
                is Outcome.Failure -> actions.update { it.copy(isDeleting = false, error = result.error) }
            }
        }
    }

    private fun updateEditor(change: (WeightEditorState) -> WeightEditorState) =
        actions.update { current -> current.copy(weightEditor = current.weightEditor?.let(change)) }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
