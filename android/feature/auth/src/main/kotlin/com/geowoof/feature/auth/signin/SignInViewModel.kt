package com.geowoof.feature.auth.signin

import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geowoof.core.data.credentials.PasswordSaveRequests
import com.geowoof.core.data.repository.AuthRepository
import com.geowoof.core.domain.GetGoogleClientIdUseCase
import com.geowoof.core.domain.ValidateEmailUseCase
import com.geowoof.core.model.AppError
import com.geowoof.core.model.EmailIssue
import com.geowoof.core.model.Outcome
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

data class SignInUiState(
    val emailIssue: EmailIssue? = null,
    val isPasswordMissing: Boolean = false,
    val isSubmitting: Boolean = false,
    val isSigningInWithGoogle: Boolean = false,
    val error: AppError? = null,
    /** `null` hides "Continue with Google". */
    val googleClientId: String? = null,
    val passwordWasReset: Boolean = false,
) {
    val isBusy: Boolean get() = isSubmitting || isSigningInWithGoogle
}

@HiltViewModel
class SignInViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val auth: AuthRepository,
    private val validateEmail: ValidateEmailUseCase,
    private val passwordSaves: PasswordSaveRequests,
    getGoogleClientId: GetGoogleClientIdUseCase,
) : ViewModel() {
    val email = TextFieldState(savedStateHandle.get<String>("email").orEmpty())
    val password = TextFieldState()

    private val _uiState = MutableStateFlow(
        SignInUiState(passwordWasReset = savedStateHandle.get<Boolean>("passwordWasReset") == true),
    )
    val uiState: StateFlow<SignInUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val clientId = getGoogleClientId()
            _uiState.update { it.copy(googleClientId = clientId) }
        }
        // Once a field shows a problem, it is re-checked as the user types.
        email.textChanges()
            .onEach { text ->
                _uiState.update {
                    if (it.emailIssue !=
                        null
                    ) {
                        it.copy(emailIssue = validateEmail(text))
                    } else {
                        it
                    }
                }
            }
            .launchIn(viewModelScope)
        password.textChanges()
            .onEach { text -> _uiState.update { it.copy(isPasswordMissing = it.isPasswordMissing && text.isEmpty()) } }
            .launchIn(viewModelScope)
    }

    /** On success the session changes and the app leaves the signed-out flow. */
    fun submit() {
        if (_uiState.value.isBusy) return
        val emailText = email.text.toString()
        val passwordText = password.text.toString()
        val emailIssue = validateEmail(emailText)
        val isPasswordMissing = passwordText.isEmpty()
        _uiState.update { it.copy(emailIssue = emailIssue, isPasswordMissing = isPasswordMissing, error = null) }
        if (emailIssue != null || isPasswordMissing) return

        _uiState.update { it.copy(isSubmitting = true) }
        viewModelScope.launch {
            when (val outcome = auth.signIn(emailText, passwordText)) {
                is Outcome.Success -> passwordSaves.request(emailText, passwordText)
                is Outcome.Failure -> _uiState.update { it.copy(isSubmitting = false, error = outcome.error) }
            }
        }
    }

    fun signInWithGoogle(idToken: String) {
        if (_uiState.value.isBusy) return
        _uiState.update { it.copy(isSigningInWithGoogle = true, error = null) }
        viewModelScope.launch {
            auth.signInWithGoogle(idToken).onFailure { error ->
                _uiState.update { it.copy(isSigningInWithGoogle = false, error = error) }
            }
        }
    }

    fun onGoogleUnavailable() = _uiState.update { it.copy(error = AppError.CredentialUnavailable) }
}
