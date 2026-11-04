package com.geowoof.feature.settings.account

import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geowoof.core.common.AppConfig
import com.geowoof.core.data.repository.AuthRepository
import com.geowoof.core.model.AppError
import com.geowoof.core.model.SessionState
import com.geowoof.core.model.onFailure
import com.geowoof.core.ui.textChanges
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DeleteAccountUiState(
    /** With a password the user confirms with it; otherwise, with Google. */
    val hasPassword: Boolean = true,
    val googleClientId: String? = null,
    val isPasswordMissing: Boolean = false,
    val isConfirming: Boolean = false,
    val isDeleting: Boolean = false,
    val error: AppError? = null,
)

@HiltViewModel
class DeleteAccountViewModel @Inject constructor(private val auth: AuthRepository, config: AppConfig) : ViewModel() {
    val password = TextFieldState()

    private val _uiState = MutableStateFlow(
        DeleteAccountUiState(
            hasPassword = (auth.sessionState.value as? SessionState.SignedIn)?.user?.hasPassword != false,
            googleClientId = config.googleServerClientId?.takeIf { it.isNotBlank() },
        ),
    )
    val uiState: StateFlow<DeleteAccountUiState> = _uiState.asStateFlow()

    init {
        password.textChanges()
            .onEach { text -> _uiState.update { it.copy(isPasswordMissing = it.isPasswordMissing && text.isEmpty()) } }
            .launchIn(viewModelScope)
    }

    /** Asks for confirmation once the password (if the account has one) has been typed. */
    fun requestDeletion() {
        val state = _uiState.value
        if (state.isDeleting) return
        val isPasswordMissing = state.hasPassword && password.text.isEmpty()
        _uiState.update {
            it.copy(isPasswordMissing = isPasswordMissing, isConfirming = !isPasswordMissing, error = null)
        }
    }

    fun dismissConfirmation() = _uiState.update { it.copy(isConfirming = false) }

    fun deleteWithPassword() = delete(password = password.text.toString(), googleIdToken = null)

    fun deleteWithGoogle(idToken: String) = delete(password = null, googleIdToken = idToken)

    fun onGoogleUnavailable() = _uiState.update {
        it.copy(isConfirming = false, error = AppError.CredentialUnavailable)
    }

    /** On success the session ends and the app shows the welcome screen. */
    private fun delete(password: String?, googleIdToken: String?) {
        if (_uiState.value.isDeleting) return
        _uiState.update { it.copy(isConfirming = false, isDeleting = true, error = null) }
        viewModelScope.launch {
            auth.deleteAccount(password, googleIdToken).onFailure { error ->
                _uiState.update { it.copy(isDeleting = false, error = error) }
            }
        }
    }
}
