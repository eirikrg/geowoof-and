package com.geowoof.feature.walks

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.geowoof.core.data.repository.PetsRepository
import com.geowoof.core.data.repository.WalksRepository
import com.geowoof.core.model.Walk
import com.geowoof.core.model.WalkTotals
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WalksUiState(
    /** The pet of the list, or `null` if it is the list of the person. */
    val petName: String? = null,
    val totals: WalkTotals? = null,
    val petNames: Map<String, String> = emptyMap(),
)

@HiltViewModel
class WalksViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: WalksRepository,
    pets: PetsRepository,
) : ViewModel() {
    private val petId: String? = savedStateHandle["petId"]

    /** The screen reads the pages from Room; failing to update them is shown, and the list stays. */
    val walks: Flow<PagingData<Walk>> = repository.walks(petId).cachedIn(viewModelScope)

    val uiState: StateFlow<WalksUiState> = combine(repository.observeTotals(petId), pets.myPets) { totals, myPets ->
        WalksUiState(
            petName = myPets.find { it.id == petId }?.name,
            totals = totals,
            petNames = myPets.associate { it.id to it.name },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), WalksUiState())

    init {
        refreshTotals()
    }

    /** Without a connection, the totals stay as they were: the list says what happened. */
    fun refreshTotals() {
        viewModelScope.launch { repository.refreshTotals(petId) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
