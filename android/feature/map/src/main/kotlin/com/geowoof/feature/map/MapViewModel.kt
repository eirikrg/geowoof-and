package com.geowoof.feature.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geowoof.core.data.repository.ActiveWalkRepository
import com.geowoof.core.data.repository.PetsRepository
import com.geowoof.core.data.repository.SettingsRepository
import com.geowoof.core.domain.walks.ComputeWalkStatsUseCase
import com.geowoof.core.location.LocationAccess
import com.geowoof.core.location.LocationDataSource
import com.geowoof.core.location.LocationPermissionLevel
import com.geowoof.core.model.ActiveWalk
import com.geowoof.core.model.AppError
import com.geowoof.core.model.GeoPoint
import com.geowoof.core.model.LiveVisibility
import com.geowoof.core.model.Outcome
import com.geowoof.core.model.Pet
import com.geowoof.core.model.UserSettings
import com.geowoof.core.model.WalkLimits
import com.geowoof.core.model.WalkStats
import com.geowoof.feature.map.tracking.WalkTrackingController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What has to be explained before a walk can start. */
enum class WalkPrompt {
    /** The first time the map opens, to open it where the person is. It is only asked once. */
    OpenAtLocation,

    /** The explanation before the system asks for the permission. */
    PermissionRationale,

    /** The user only allowed the approximate location, which cannot draw a route. */
    ApproximateLocation,
    PermissionDenied,
    LocationDisabled,
}

/** Where to take the camera, once. [isJump] is for the first time, when it should not fly across the country. */
data class CameraTarget(val position: GeoPoint, val isJump: Boolean)

data class MapUiState(
    val walk: ActiveWalk? = null,
    val stats: WalkStats? = null,
    val pets: List<Pet> = emptyList(),
    val isPickingPets: Boolean = false,
    val selectedPetIds: Set<String> = emptySet(),
    val prompt: WalkPrompt? = null,
    val hasLocationPermission: Boolean = false,
    /** Go to the summary of the walk that has just finished. */
    val openSummary: Boolean = false,
    /** `null` until the person chooses. */
    val liveVisibility: LiveVisibility? = null,
    val hideStartDistanceM: Int = UserSettings.DEFAULT_HIDE_START_M,
    val isChoosingVisibility: Boolean = false,
    val cameraTarget: CameraTarget? = null,
    /** It does not know yet where to open: the map would show another city for a moment. */
    val isLocating: Boolean = false,
    val error: AppError? = null,
)

private data class MapChoices(
    val isPickingPets: Boolean = false,
    val selectedPetIds: Set<String> = emptySet(),
    val prompt: WalkPrompt? = null,
    val hasLocationPermission: Boolean = false,
    val openSummary: Boolean = false,
    val isChoosingVisibility: Boolean = false,
    /** The first walk waits for the person to say who can see them. */
    val startsAfterChoice: Boolean = false,
    val cameraTarget: CameraTarget? = null,
    val isLocating: Boolean = true,
    val error: AppError? = null,
)

/** Each function is one thing the user can do with a walk. */
@Suppress("TooManyFunctions", "LongParameterList")
@HiltViewModel
class MapViewModel @Inject constructor(
    private val activeWalks: ActiveWalkRepository,
    private val pets: PetsRepository,
    private val locationAccess: LocationAccess,
    private val tracking: WalkTrackingController,
    private val computeStats: ComputeWalkStatsUseCase,
    private val settings: SettingsRepository,
    private val location: LocationDataSource,
) : ViewModel() {
    private val choices = MutableStateFlow(MapChoices())
    private var hasShownThePerson = false
    private var findingThePerson: Job? = null

    /** What to do once the permission is granted. */
    private var pendingStart: PendingStart? = null

    /** The service is about to start: a walk without a service is not an interrupted one yet. */
    private var isStarting = false

    val uiState: StateFlow<MapUiState> =
        combine(activeWalks.activeWalk, pets.myPets, choices, settings.settings) { walk, myPets, current, chosen ->
            MapUiState(
                walk = walk,
                stats = walk?.let { computeStats(it.segments, it.lastPoint?.offsetMs ?: 0) },
                pets = myPets,
                isPickingPets = current.isPickingPets,
                selectedPetIds = current.selectedPetIds,
                prompt = current.prompt,
                hasLocationPermission = current.hasLocationPermission,
                openSummary = current.openSummary,
                liveVisibility = chosen.liveVisibility,
                hideStartDistanceM = chosen.hideStartDistanceM,
                isChoosingVisibility = current.isChoosingVisibility,
                cameraTarget = current.cameraTarget,
                isLocating = current.isLocating,
                error = current.error,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), MapUiState(isLocating = true))

    /**
     * Each time the screen comes back. If the walk was recording and the service is not alive, the
     * process died and the system did not bring it back: the user decides what to do with it.
     */
    fun onScreenResumed() {
        refreshPermission()
        showThePersonOnce()
        viewModelScope.launch {
            val walk = activeWalks.current()
            if (walk?.isTracking == true && !tracking.isRunning && !isStarting) activeWalks.interrupt()
        }
    }

    fun onStartClick() {
        viewModelScope.launch { pets.refreshMyPets() }
        viewModelScope.launch { settings.refresh() }
        choices.update { it.copy(isPickingPets = true) }
    }

    fun onDismissPetPicker() = choices.update { it.copy(isPickingPets = false) }

    fun onTogglePet(petId: String) = choices.update { current ->
        val selected = current.selectedPetIds
        when {
            petId in selected -> current.copy(selectedPetIds = selected - petId)
            selected.size < WalkLimits.MAX_PETS -> current.copy(selectedPetIds = selected + petId)
            else -> current
        }
    }

    /** With a single pet there is nothing to choose. */
    fun onConfirmPets(onlyPetId: String? = null) {
        val selected = onlyPetId?.let(::setOf) ?: choices.value.selectedPetIds
        if (selected.isEmpty()) return
        choices.update { it.copy(isPickingPets = false, selectedPetIds = selected) }
        viewModelScope.launch {
            if (settings.current().liveVisibility == null) {
                choices.update { it.copy(isChoosingVisibility = true, startsAfterChoice = true) }
            } else {
                proceed(PendingStart.NewWalk)
            }
        }
    }

    fun onChangeVisibilityClick() = choices.update { it.copy(isChoosingVisibility = true, startsAfterChoice = false) }

    fun onDismissVisibility() = choices.update { it.copy(isChoosingVisibility = false, startsAfterChoice = false) }

    /**
     * The walk does not wait for the server, because it has to work without a connection. Nothing
     * is shared until the choice is saved: if that fails, this walk stays private and the next
     * one asks again.
     */
    fun onChooseVisibility(visibility: LiveVisibility) {
        val startsWalk = choices.value.startsAfterChoice
        choices.update { it.copy(isChoosingVisibility = false, startsAfterChoice = false) }
        viewModelScope.launch {
            val saved = settings.setLiveVisibility(visibility)
            if (saved is Outcome.Failure) choices.update { it.copy(error = saved.error) }
        }
        if (startsWalk) proceed(PendingStart.NewWalk)
    }

    fun onErrorShown() = choices.update { it.copy(error = null) }

    fun onMyLocationClick() = goToThePerson(isJump = false)

    fun onCameraTargetReached() = choices.update { it.copy(cameraTarget = null) }

    /**
     * The map opens where the person is, in place of a default city. If it could not know, it tries again the next
     * time the screen comes back.
     */
    private fun showThePersonOnce() {
        if (hasShownThePerson || findingThePerson?.isActive == true) return
        if (locationAccess.permissionLevel() == LocationPermissionLevel.None) {
            askForLocationOnce()
            return
        }
        goToThePerson(isJump = true)
        // Better the default city than an empty screen: the map still goes to the person when the position comes.
        viewModelScope.launch {
            delay(MAX_LOCATING_MILLIS)
            choices.update { it.copy(isLocating = false) }
        }
    }

    /** Whoever says no opens the map on the default city, and is not asked again each time. */
    private fun askForLocationOnce() {
        viewModelScope.launch {
            val asks = !settings.wasAskedForLocation() && activeWalks.current() == null
            choices.update { current ->
                when {
                    current.prompt == WalkPrompt.OpenAtLocation -> current
                    asks && current.prompt == null -> current.copy(prompt = WalkPrompt.OpenAtLocation)
                    else -> current.copy(isLocating = false)
                }
            }
        }
    }

    /** Before the system asks: the answer comes in [onPermissionsResult]. */
    fun onAskForLocationClick() {
        choices.update { it.copy(prompt = null) }
        viewModelScope.launch { settings.markAskedForLocation() }
    }

    private fun goToThePerson(isJump: Boolean) {
        findingThePerson?.cancel()
        findingThePerson = viewModelScope.launch {
            location.whereThePersonIs().collect { position ->
                hasShownThePerson = true
                choices.update { it.copy(isLocating = false, cameraTarget = CameraTarget(position, isJump)) }
            }
            choices.update { it.copy(isLocating = false) }
        }
    }

    /** A position that comes late must not take the map away from where the person has just put it. */
    fun onMapMovedByHand() {
        hasShownThePerson = true
        findingThePerson?.cancel()
    }

    fun onResumeClick() = proceed(PendingStart.Resume)

    fun onPauseClick() {
        viewModelScope.launch { activeWalks.pause() }
    }

    fun onFinishClick() {
        viewModelScope.launch {
            activeWalks.finish()
            choices.update { it.copy(openSummary = true) }
        }
    }

    fun onCancelClick() {
        viewModelScope.launch { activeWalks.discard() }
    }

    fun onReviewClick() = choices.update { it.copy(openSummary = true) }

    fun onSummaryOpened() = choices.update { it.copy(openSummary = false) }

    fun onDismissPrompt() {
        pendingStart = null
        if (choices.value.prompt == WalkPrompt.OpenAtLocation) {
            viewModelScope.launch { settings.markAskedForLocation() }
            choices.update { it.copy(prompt = null, isLocating = false) }
        } else {
            choices.update { it.copy(prompt = null) }
        }
    }

    /** The answer of the system. It is read from the system, because the user may also change it in Settings. */
    fun onPermissionsResult() {
        refreshPermission()
        // When it was the map asking, to open where the person is, any answer is fine: no walk is waiting for it.
        val isForAWalk = pendingStart != null
        when (locationAccess.permissionLevel()) {
            LocationPermissionLevel.Precise -> {
                choices.update { it.copy(prompt = null) }
                runPending()
            }

            LocationPermissionLevel.Approximate -> if (isForAWalk) {
                choices.update { it.copy(prompt = WalkPrompt.ApproximateLocation) }
            }

            LocationPermissionLevel.None -> if (isForAWalk) {
                choices.update { it.copy(prompt = WalkPrompt.PermissionDenied) }
            }
        }
        showThePersonOnce()
    }

    private fun proceed(start: PendingStart) {
        pendingStart = start
        if (locationAccess.permissionLevel() == LocationPermissionLevel.Precise) {
            runPending()
        } else {
            choices.update { it.copy(prompt = WalkPrompt.PermissionRationale) }
        }
    }

    private fun runPending() {
        val start = pendingStart ?: return
        if (!locationAccess.isLocationEnabled()) {
            choices.update { it.copy(prompt = WalkPrompt.LocationDisabled) }
            return
        }

        pendingStart = null
        isStarting = true
        viewModelScope.launch {
            when (start) {
                PendingStart.NewWalk -> activeWalks.start(choices.value.selectedPetIds.toList())
                PendingStart.Resume -> activeWalks.resume()
            }
            if (!tracking.start()) activeWalks.interrupt()
            isStarting = false
        }
    }

    private fun refreshPermission() {
        val hasPermission = locationAccess.permissionLevel() != LocationPermissionLevel.None
        choices.update { it.copy(hasLocationPermission = hasPermission) }
    }

    private enum class PendingStart {
        NewWalk,
        Resume,
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val MAX_LOCATING_MILLIS = 3_000L
    }
}
