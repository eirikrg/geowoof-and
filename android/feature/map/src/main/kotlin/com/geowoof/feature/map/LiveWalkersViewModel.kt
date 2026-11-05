package com.geowoof.feature.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geowoof.core.data.repository.LiveRepository
import com.geowoof.core.data.repository.SocialRepository
import com.geowoof.core.model.AppError
import com.geowoof.core.model.LiveNearby
import com.geowoof.core.model.LiveWalker
import com.geowoof.core.model.MapViewport
import com.geowoof.core.model.Outcome
import com.geowoof.core.model.Relationship
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LiveWalkersUiState(
    val walkers: List<LiveWalker> = emptyList(),
    /** The map is too far away to show anyone. */
    val isTooWide: Boolean = false,
    /** The accessible alternative to the markers. */
    val isListOpen: Boolean = false,
    /** Stays open with what was known if they stop walking meanwhile. */
    val selected: LiveWalker? = null,
    /** `null` until it is known: nothing about following is offered without it. */
    val relationship: Relationship? = null,
    val isChangingFollow: Boolean = false,
    /** Following someone who follows you makes you friends, so it asks first. */
    val isConfirmingFollowBack: Boolean = false,
    val error: AppError? = null,
)

private data class LivePanel(
    val isListOpen: Boolean = false,
    val selected: LiveWalker? = null,
    val relationship: Relationship? = null,
    val isChangingFollow: Boolean = false,
    val isConfirmingFollowBack: Boolean = false,
    val error: AppError? = null,
)

/**
 * Who is walking on the part of the map on the screen. While nobody looks at the screen it stops
 * collecting, which closes the connection unless a walk is using it.
 */
@HiltViewModel
class LiveWalkersViewModel @Inject constructor(live: LiveRepository, private val social: SocialRepository) :
    ViewModel() {
    private val viewports = MutableStateFlow<MapViewport?>(null)
    private val panel = MutableStateFlow(LivePanel())

    @OptIn(FlowPreview::class)
    private val nearby = live
        .nearby(viewports.filterNotNull().debounce(VIEWPORT_DEBOUNCE_MILLIS).distinctUntilChanged())
        .onStart { emit(LiveNearby()) }

    val uiState: StateFlow<LiveWalkersUiState> = combine(nearby, panel) { found, current ->
        LiveWalkersUiState(
            walkers = found.walkers,
            isTooWide = found.isTooWide,
            isListOpen = current.isListOpen,
            selected = current.selected?.let { open -> found.walkers.firstOrNull { it.userId == open.userId } ?: open },
            relationship = current.relationship,
            isChangingFollow = current.isChangingFollow,
            isConfirmingFollowBack = current.isConfirmingFollowBack,
            error = current.error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), LiveWalkersUiState())

    fun onViewportChange(viewport: MapViewport) {
        viewports.value = viewport
    }

    fun onOpenList() = panel.update { it.copy(isListOpen = true) }

    fun onDismissList() = panel.update { it.copy(isListOpen = false) }

    fun onWalkerClick(userId: String) {
        val walker = uiState.value.walkers.firstOrNull { it.userId == userId } ?: return
        panel.update { it.copy(isListOpen = false, selected = walker, relationship = null) }
        viewModelScope.launch {
            val answer = social.relationship(userId)
            panel.update { current ->
                if (current.selected?.userId == userId) {
                    current.copy(relationship = (answer as? Outcome.Success)?.value)
                } else {
                    current
                }
            }
        }
    }

    fun onDismissWalker() = panel.update {
        it.copy(selected = null, relationship = null, isConfirmingFollowBack = false)
    }

    fun onFollowClick() {
        val current = panel.value
        if (current.relationship?.followsYou == true) {
            panel.update { it.copy(isConfirmingFollowBack = true) }
        } else {
            follow()
        }
    }

    fun onConfirmFollowBack() {
        panel.update { it.copy(isConfirmingFollowBack = false) }
        follow()
    }

    fun onDismissFollowBack() = panel.update { it.copy(isConfirmingFollowBack = false) }

    fun onErrorShown() = panel.update { it.copy(error = null) }

    private fun follow() {
        val userId = panel.value.selected?.userId ?: return
        if (panel.value.isChangingFollow) return
        panel.update { it.copy(isChangingFollow = true) }
        viewModelScope.launch {
            when (val answer = social.follow(userId)) {
                is Outcome.Success -> panel.update { it.copy(isChangingFollow = false, relationship = answer.value) }
                is Outcome.Failure -> panel.update { it.copy(isChangingFollow = false, error = answer.error) }
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val VIEWPORT_DEBOUNCE_MILLIS = 500L
    }
}
