package com.geowoof.feature.auth.welcome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geowoof.core.data.repository.AuthRepository
import com.geowoof.core.domain.GetGoogleClientIdUseCase
import com.geowoof.core.model.AppError
import com.geowoof.core.model.onFailure
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WelcomeUiState(
    /** `null` hides "Continue with Google". */
    val googleClientId: String? = null,
    val isSigningInWithGoogle: Boolean = false,
    val error: AppError? = null,
)

@HiltViewModel
class WelcomeViewModel @Inject constructor(
    private val auth: AuthRepository,
    getGoogleClientId: GetGoogleClientIdUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(WelcomeUiState())
    val uiState: StateFlow<WelcomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val clientId = getGoogleClientId()
            _uiState.update { it.copy(googleClientId = clientId) }
        }
    }

    /** On success the session changes and the app leaves the signed-out flow. */
    fun signInWithGoogle(idToken: String) {
        if (_uiState.value.isSigningInWithGoogle) return
        _uiState.update { it.copy(isSigningInWithGoogle = true, error = null) }
        viewModelScope.launch {
            auth.signInWithGoogle(idToken).onFailure { error ->
                _uiState.update { it.copy(isSigningInWithGoogle = false, error = error) }
            }
        }
    }

    fun onGoogleUnavailable() = _uiState.update { it.copy(error = AppError.CredentialUnavailable) }
}
