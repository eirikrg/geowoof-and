package com.geowoof.feature.map.summary

import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geowoof.core.data.repository.ActiveWalkRepository
import com.geowoof.core.data.repository.PetsRepository
import com.geowoof.core.domain.walks.ComputeWalkStatsUseCase
import com.geowoof.core.domain.walks.SaveWalkUseCase
import com.geowoof.core.model.ActiveWalk
import com.geowoof.core.model.ActiveWalkStatus
import com.geowoof.core.model.AppError
import com.geowoof.core.model.Outcome
import com.geowoof.core.model.Pet
import com.geowoof.core.model.WalkLimits
import com.geowoof.core.model.WalkStats
import com.geowoof.core.model.WalkVisibility
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import javax.inject.Inject

data class WalkSummaryUiState(
    val walk: ActiveWalk? = null,
    val stats: WalkStats? = null,
    val petNames: List<String> = emptyList(),
    val visibility: WalkVisibility = WalkVisibility.Friends,
    val photoUris: List<String> = emptyList(),
    val isNoteTooLong: Boolean = false,
    val isConfirmingDiscard: Boolean = false,
    val isSaving: Boolean = false,
    val error: AppError? = null,
    /** Saved or discarded, or there was nothing to review: the screen goes back. */
    val isDone: Boolean = false,
) {
    /** The server needs a route, and it takes a moment of walking to have one. */
    val canSave: Boolean get() = walk?.hasRoute == true
}

private data class SummaryChoices(
    val visibility: WalkVisibility = WalkVisibility.Friends,
    val photoUris: List<String> = emptyList(),
    val isNoteTooLong: Boolean = false,
    val isConfirmingDiscard: Boolean = false,
    val isSaving: Boolean = false,
    val error: AppError? = null,
    val isDone: Boolean = false,
)

@HiltViewModel
class WalkSummaryViewModel @Inject constructor(
    private val activeWalks: ActiveWalkRepository,
    pets: PetsRepository,
    private val saveWalk: SaveWalkUseCase,
    private val computeStats: ComputeWalkStatsUseCase,
) : ViewModel() {
    private val choices = MutableStateFlow(SummaryChoices())

    val noteText = TextFieldState()

    val uiState: StateFlow<WalkSummaryUiState> =
        combine(activeWalks.activeWalk, pets.myPets, choices) { walk, myPets, current ->
            WalkSummaryUiState(
                walk = walk,
                stats = walk?.statsOf(),
                petNames = walk?.petNames(myPets).orEmpty(),
                visibility = current.visibility,
                photoUris = current.photoUris,
                isNoteTooLong = current.isNoteTooLong,
                isConfirmingDiscard = current.isConfirmingDiscard,
                isSaving = current.isSaving,
                error = current.error,
                isDone = current.isDone || walk?.status != ActiveWalkStatus.Finished,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), WalkSummaryUiState())

    private fun ActiveWalk.statsOf(): WalkStats {
        val end = endedAt ?: startedAt
        return computeStats(segments, Duration.between(startedAt, end).toMillis())
    }

    private fun ActiveWalk.petNames(myPets: List<Pet>): List<String> =
        petIds.mapNotNull { id -> myPets.find { it.id == id }?.name }

    fun onVisibilityChange(visibility: WalkVisibility) = choices.update { it.copy(visibility = visibility) }

    fun onAddPhoto(contentUri: String) = choices.update { current ->
        if (current.photoUris.size >= WalkLimits.MAX_PHOTOS || contentUri in current.photoUris) {
            current
        } else {
            current.copy(photoUris = current.photoUris + contentUri)
        }
    }

    fun onRemovePhoto(contentUri: String) = choices.update { it.copy(photoUris = it.photoUris - contentUri) }

    fun onErrorShown() = choices.update { it.copy(error = null) }

    fun onSaveClick() {
        val walk = uiState.value.walk ?: return
        val current = choices.value
        val note = noteText.text.toString()
        val isNoteTooLong = note.length > WalkLimits.NOTE_MAX_LENGTH
        choices.update { it.copy(isNoteTooLong = isNoteTooLong) }
        if (current.isSaving || isNoteTooLong || !walk.hasRoute) return

        choices.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            when (val result = saveWalk(walk, current.visibility, note, current.photoUris)) {
                is Outcome.Success -> choices.update { it.copy(isSaving = false, isDone = true) }
                is Outcome.Failure -> choices.update { it.copy(isSaving = false, error = result.error) }
            }
        }
    }

    fun onRequestDiscard() = choices.update { it.copy(isConfirmingDiscard = true) }

    fun onDismissDiscard() = choices.update { it.copy(isConfirmingDiscard = false) }

    fun onConfirmDiscard() {
        viewModelScope.launch {
            activeWalks.discard()
            choices.update { it.copy(isConfirmingDiscard = false, isDone = true) }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
