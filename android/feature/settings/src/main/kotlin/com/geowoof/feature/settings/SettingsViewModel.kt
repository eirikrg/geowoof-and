package com.geowoof.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geowoof.core.data.repository.ActiveWalkRepository
import com.geowoof.core.data.repository.AuthRepository
import com.geowoof.core.data.repository.WalksRepository
import com.geowoof.core.model.SessionState
import com.geowoof.core.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val user: User? = null,
    val isSigningOut: Boolean = false,
    /** Signing out would lose walks that are only on this device. */
    val isConfirmingSignOut: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val walks: WalksRepository,
    private val activeWalks: ActiveWalkRepository,
) : ViewModel() {
    private val isSigningOut = MutableStateFlow(false)
    private val isConfirmingSignOut = MutableStateFlow(false)

    val uiState: StateFlow<SettingsUiState> =
        combine(auth.sessionState, isSigningOut, isConfirmingSignOut) { session, signingOut, confirming ->
            SettingsUiState(
                user = session.userOrNull(),
                isSigningOut = signingOut,
                isConfirmingSignOut = confirming,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = SettingsUiState(user = auth.sessionState.value.userOrNull()),
        )

    /** Always signs out locally, and the app then shows the welcome screen. First it warns if that would lose walks. */
    fun signOut() {
        if (isSigningOut.value) return
        viewModelScope.launch {
            if (walks.hasUnsyncedWalks() || activeWalks.current() != null) {
                isConfirmingSignOut.value = true
            } else {
                confirmSignOut()
            }
        }
    }

    fun dismissSignOut() {
        isConfirmingSignOut.value = false
    }

    fun confirmSignOut() {
        if (isSigningOut.value) return
        isConfirmingSignOut.value = false
        isSigningOut.value = true
        viewModelScope.launch { auth.signOut() }
    }

    private fun SessionState.userOrNull(): User? = (this as? SessionState.SignedIn)?.user

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
