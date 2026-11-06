package com.geowoof.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geowoof.core.data.repository.AccountRepository
import com.geowoof.core.data.repository.ActiveWalkRepository
import com.geowoof.core.data.repository.AppearanceRepository
import com.geowoof.core.data.repository.AuthRepository
import com.geowoof.core.data.repository.SettingsRepository
import com.geowoof.core.data.repository.WalksRepository
import com.geowoof.core.model.AppError
import com.geowoof.core.model.Appearance
import com.geowoof.core.model.LiveVisibility
import com.geowoof.core.model.Outcome
import com.geowoof.core.model.SessionState
import com.geowoof.core.model.ThemeMode
import com.geowoof.core.model.User
import com.geowoof.core.model.UserSettings
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
    /** Waiting for the server to change who can see the profile. */
    val isChangingPrivacy: Boolean = false,
    /** Room keeps the answer of the server, so a choice only shows once it has been made. */
    val settings: UserSettings = UserSettings(),
    val appearance: Appearance = Appearance(),
    val error: AppError? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val walks: WalksRepository,
    private val activeWalks: ActiveWalkRepository,
    private val accounts: AccountRepository,
    private val settings: SettingsRepository,
    private val appearance: AppearanceRepository,
) : ViewModel() {
    private val isSigningOut = MutableStateFlow(false)
    private val isConfirmingSignOut = MutableStateFlow(false)
    private val isChangingPrivacy = MutableStateFlow(false)
    private val error = MutableStateFlow<AppError?>(null)

    val uiState: StateFlow<SettingsUiState> =
        combine(
            auth.sessionState,
            isSigningOut,
            isConfirmingSignOut,
            isChangingPrivacy,
            error,
        ) { session, signingOut, confirming, changingPrivacy, failure ->
            SettingsUiState(
                user = session.userOrNull(),
                isSigningOut = signingOut,
                isConfirmingSignOut = confirming,
                isChangingPrivacy = changingPrivacy,
                error = failure,
            )
        }.combine(settings.settings) { state, current ->
            state.copy(settings = current)
        }.combine(appearance.appearance) { state, current ->
            state.copy(appearance = current)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = SettingsUiState(user = auth.sessionState.value.userOrNull()),
        )

    init {
        viewModelScope.launch { settings.refresh() }
    }

    /** The session keeps the answer of the server, so the switch only moves if the change was made. */
    fun onPrivateChange(isPrivate: Boolean) {
        if (isChangingPrivacy.value) return
        isChangingPrivacy.value = true
        viewModelScope.launch {
            error.value = (accounts.setPrivate(isPrivate) as? Outcome.Failure)?.error
            isChangingPrivacy.value = false
        }
    }

    fun onLiveVisibilityChange(visibility: LiveVisibility) {
        viewModelScope.launch { error.value = (settings.setLiveVisibility(visibility) as? Outcome.Failure)?.error }
    }

    fun onHideStartChange(meters: Int) {
        viewModelScope.launch { error.value = (settings.setHideStartDistance(meters) as? Outcome.Failure)?.error }
    }

    fun onThemeModeChange(mode: ThemeMode) {
        viewModelScope.launch { appearance.setThemeMode(mode) }
    }

    fun onWallpaperColorsChange(use: Boolean) {
        viewModelScope.launch { appearance.setUseWallpaperColors(use) }
    }

    fun onErrorShown() {
        error.value = null
    }

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
