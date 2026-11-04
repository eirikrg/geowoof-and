package com.geowoof.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geowoof.core.data.repository.AuthRepository
import com.geowoof.core.model.SessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainActivityViewModel @Inject constructor(private val auth: AuthRepository) : ViewModel() {
    /** While [SessionState.Loading], the splash screen stays visible. */
    val sessionState: StateFlow<SessionState> = auth.sessionState

    init {
        // Picks up changes made elsewhere (email verified on another device, account deleted…).
        // A revoked session ends here: the authenticator clears it and the app shows the welcome screen.
        viewModelScope.launch {
            if (auth.sessionState.first { it != SessionState.Loading } is SessionState.SignedIn) {
                auth.refreshUser()
            }
        }
    }
}
