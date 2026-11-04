package com.geowoof.feature.auth.password

import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geowoof.core.data.repository.AuthRepository
import com.geowoof.core.domain.ValidatePasswordUseCase
import com.geowoof.core.domain.ValidateVerificationCodeUseCase
import com.geowoof.core.model.AppError
import com.geowoof.core.model.CodeIssue
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

data class ResetPasswordUiState(
    val email: String,
    val codeIssue: CodeIssue? = null,
    val passwordIssue: PasswordIssue? = null,
    val isSubmitting: Boolean = false,
    val isResending: Boolean = false,
    val codeResent: Boolean = false,
    val error: AppError? = null,
    /** The password was changed; the screen then goes back to sign in. */
    val isDone: Boolean = false,
)

@HiltViewModel
class ResetPasswordViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val auth: AuthRepository,
    private val validateCode: ValidateVerificationCodeUseCase,
    private val validatePassword: ValidatePasswordUseCase,
) : ViewModel() {
    val code = TextFieldState()
    val newPassword = TextFieldState()

    private val _uiState =
        MutableStateFlow(ResetPasswordUiState(email = savedStateHandle.get<String>("email").orEmpty()))
    val uiState: StateFlow<ResetPasswordUiState> = _uiState.asStateFlow()

    init {
        code.textChanges()
            .onEach { text -> _uiState.update { it.copy(codeIssue = it.codeIssue?.let { validateCode(text) }) } }
            .launchIn(viewModelScope)
        newPassword.textChanges()
            .onEach { text ->
                _uiState.update { it.copy(passwordIssue = it.passwordIssue?.let { validatePassword(text) }) }
            }
            .launchIn(viewModelScope)
    }

    fun submit() {
        val state = _uiState.value
        if (state.isSubmitting) return
        val codeText = code.text.toString()
        val passwordText = newPassword.text.toString()
        val codeIssue = validateCode(codeText)
        val passwordIssue = validatePassword(passwordText)
        val isValid = codeIssue == null && passwordIssue == null
        _uiState.update {
            it.copy(
                codeIssue = codeIssue,
                passwordIssue = passwordIssue,
                error = null,
                codeResent = false,
                isSubmitting = isValid,
            )
        }
        if (!isValid) return

        viewModelScope.launch {
            when (val outcome = auth.resetPassword(state.email, codeText, passwordText)) {
                is Outcome.Success -> _uiState.update { it.copy(isSubmitting = false, isDone = true) }
                is Outcome.Failure -> _uiState.update { it.copy(isSubmitting = false, error = outcome.error) }
            }
        }
    }

    fun resendCode() {
        val state = _uiState.value
        if (state.isResending) return
        _uiState.update { it.copy(isResending = true, codeResent = false, error = null) }
        viewModelScope.launch {
            when (val outcome = auth.requestPasswordReset(state.email)) {
                is Outcome.Success -> _uiState.update { it.copy(isResending = false, codeResent = true) }
                is Outcome.Failure -> _uiState.update { it.copy(isResending = false, error = outcome.error) }
            }
        }
    }
}
