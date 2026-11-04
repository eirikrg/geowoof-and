package com.geowoof.feature.auth.signup

import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geowoof.core.data.credentials.PasswordSaveRequests
import com.geowoof.core.data.repository.AuthRepository
import com.geowoof.core.domain.ValidateDisplayNameUseCase
import com.geowoof.core.domain.ValidateEmailUseCase
import com.geowoof.core.domain.ValidatePasswordUseCase
import com.geowoof.core.model.AppError
import com.geowoof.core.model.DisplayNameIssue
import com.geowoof.core.model.EmailIssue
import com.geowoof.core.model.Outcome
import com.geowoof.core.model.PasswordIssue
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

data class SignUpUiState(
    val displayNameIssue: DisplayNameIssue? = null,
    val emailIssue: EmailIssue? = null,
    val passwordIssue: PasswordIssue? = null,
    val isSubmitting: Boolean = false,
    val error: AppError? = null,
)

@HiltViewModel
class SignUpViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val validateDisplayName: ValidateDisplayNameUseCase,
    private val validateEmail: ValidateEmailUseCase,
    private val validatePassword: ValidatePasswordUseCase,
    private val passwordSaves: PasswordSaveRequests,
) : ViewModel() {
    val displayName = TextFieldState()
    val email = TextFieldState()
    val password = TextFieldState()

    private val _uiState = MutableStateFlow(SignUpUiState())
    val uiState: StateFlow<SignUpUiState> = _uiState.asStateFlow()

    init {
        // Once a field shows a problem, it is re-checked as the user types.
        displayName.textChanges()
            .onEach { text ->
                _uiState.update { it.copy(displayNameIssue = it.displayNameIssue?.let { validateDisplayName(text) }) }
            }
            .launchIn(viewModelScope)
        email.textChanges()
            .onEach { text -> _uiState.update { it.copy(emailIssue = it.emailIssue?.let { validateEmail(text) }) } }
            .launchIn(viewModelScope)
        password.textChanges()
            .onEach { text ->
                _uiState.update { it.copy(passwordIssue = it.passwordIssue?.let { validatePassword(text) }) }
            }
            .launchIn(viewModelScope)
    }

    /** On success the session starts and the app shows the onboarding. */
    fun submit() {
        if (_uiState.value.isSubmitting) return
        val nameText = displayName.text.toString()
        val emailText = email.text.toString()
        val passwordText = password.text.toString()
        val issues = SignUpUiState(
            displayNameIssue = validateDisplayName(nameText),
            emailIssue = validateEmail(emailText),
            passwordIssue = validatePassword(passwordText),
        )
        val isValid = issues.displayNameIssue == null && issues.emailIssue == null && issues.passwordIssue == null
        _uiState.value = issues.copy(isSubmitting = isValid)
        if (!isValid) return

        viewModelScope.launch {
            when (val outcome = auth.register(emailText, passwordText, nameText)) {
                is Outcome.Success -> passwordSaves.request(emailText, passwordText)
                is Outcome.Failure -> _uiState.update { it.copy(isSubmitting = false, error = outcome.error) }
            }
        }
    }
}
