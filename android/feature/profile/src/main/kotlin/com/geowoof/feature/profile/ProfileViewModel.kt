package com.geowoof.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geowoof.core.data.repository.PetsRepository
import com.geowoof.core.data.repository.ProfileRepository
import com.geowoof.core.model.AppError
import com.geowoof.core.model.Outcome
import com.geowoof.core.model.Pet
import com.geowoof.core.model.Profile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val profile: Profile? = null,
    val pets: List<Pet> = emptyList(),
    val isRefreshing: Boolean = false,
    val error: AppError? = null,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(private val profiles: ProfileRepository, private val pets: PetsRepository) :
    ViewModel() {
    private val isRefreshing = MutableStateFlow(false)
    private val error = MutableStateFlow<AppError?>(null)

    val uiState: StateFlow<ProfileUiState> =
        combine(profiles.myProfile, pets.myPets, isRefreshing, error) { profile, myPets, refreshing, failure ->
            ProfileUiState(profile, myPets, refreshing, failure)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            ProfileUiState(isRefreshing = true),
        )

    init {
        refresh()
    }

    /** What Room already has stays on screen while this runs (docs/decisions/0009). */
    fun refresh() {
        if (isRefreshing.value) return
        isRefreshing.value = true
        error.value = null
        viewModelScope.launch {
            val results = listOf(profiles.refresh(), pets.refreshMyPets())
            error.value = results.filterIsInstance<Outcome.Failure>().firstOrNull()?.error
            isRefreshing.value = false
        }
    }

    fun onErrorShown() {
        error.value = null
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
