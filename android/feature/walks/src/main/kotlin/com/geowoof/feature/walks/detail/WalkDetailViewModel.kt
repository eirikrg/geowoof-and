package com.geowoof.feature.walks.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geowoof.core.common.AppConfig
import com.geowoof.core.data.repository.PetsRepository
import com.geowoof.core.data.repository.WalksRepository
import com.geowoof.core.model.AppError
import com.geowoof.core.model.Outcome
import com.geowoof.core.model.RouteSegment
import com.geowoof.core.model.Walk
import com.geowoof.core.model.WalkSyncStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WalkDetailUiState(
    val walk: Walk? = null,
    /** `null` until the full route is on the device. */
    val route: List<RouteSegment>? = null,
    val petNames: String = "",
    /** Without a Maps key a real map would be blank, so a drawing of the route replaces it. */
    val showsMap: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: AppError? = null,
    val isConfirmingDelete: Boolean = false,
    /** It disappears at once, and the server finds out when there is a connection. */
    val isDeleted: Boolean = false,
)

private data class DetailChoices(
    val isRefreshing: Boolean = false,
    val error: AppError? = null,
    val isConfirmingDelete: Boolean = false,
    val isDeleted: Boolean = false,
)

@HiltViewModel
class WalkDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val walks: WalksRepository,
    pets: PetsRepository,
    private val config: AppConfig,
) : ViewModel() {
    private val walkId: String = checkNotNull(savedStateHandle["walkId"])
    private val choices = MutableStateFlow(DetailChoices())

    val uiState: StateFlow<WalkDetailUiState> =
        combine(walks.observeWalk(walkId), walks.observeRoute(walkId), pets.myPets, choices) {
                walk,
                route,
                myPets,
                current,
            ->
            WalkDetailUiState(
                walk = walk,
                route = route,
                petNames = walk?.petIds.orEmpty().mapNotNull { id -> myPets.find { it.id == id }?.name }.joinToString(),
                showsMap = config.isMapsConfigured,
                isRefreshing = current.isRefreshing,
                error = current.error,
                isConfirmingDelete = current.isConfirmingDelete,
                isDeleted = current.isDeleted,
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            WalkDetailUiState(showsMap = config.isMapsConfigured, isRefreshing = true),
        )

    init {
        refresh()
    }

    /**
     * A walk still on the device has nothing new on the server. With the route already here, a failed
     * refresh changes nothing for the user, so it is only reported if the route is missing.
     */
    private fun refresh() {
        viewModelScope.launch {
            val syncStatus = walks.observeWalk(walkId).first()?.syncStatus
            if (syncStatus == null || syncStatus == WalkSyncStatus.Synced) {
                choices.update { it.copy(isRefreshing = true) }
                val result = walks.refreshWalk(walkId)
                val hasRoute = walks.observeRoute(walkId).first() != null
                val error = (result as? Outcome.Failure)?.error?.takeUnless { hasRoute }
                choices.update { it.copy(isRefreshing = false, error = error) }
            }
        }
    }

    fun onErrorShown() = choices.update { it.copy(error = null) }

    fun onRetryUpload() {
        viewModelScope.launch { walks.retrySync(walkId) }
    }

    fun onRequestDelete() = choices.update { it.copy(isConfirmingDelete = true) }

    fun onDismissDelete() = choices.update { it.copy(isConfirmingDelete = false) }

    fun onConfirmDelete() {
        viewModelScope.launch {
            walks.delete(walkId)
            choices.update { it.copy(isConfirmingDelete = false, isDeleted = true) }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
