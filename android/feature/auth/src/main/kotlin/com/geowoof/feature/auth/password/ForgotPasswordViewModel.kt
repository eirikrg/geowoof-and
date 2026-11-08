package com.geowoof.feature.auth.password

import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geowoof.core.data.repository.AuthRepository
import com.geowoof.core.domain.ValidateEmailUseCase
import com.geowoof.core.model.AppError
import com.geowoof.core.model.EmailIssue
import com.geowoof.core.model.Outcome
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

data class ForgotPasswordUiState(
    val emailIssue: EmailIssue? = null,
    val isSubmitting: Boolean = false,
    val error: AppError? = null,
    /** Set once the code has been requested; the screen then moves on to the reset form. */
    val codeSentTo: String? = null,
)

@HiltViewModel
class ForgotPasswordViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val auth: AuthRepository,
    private val validateEmail: ValidateEmailUseCase,
) : ViewModel() {
    val email = TextFieldState(savedStateHandle.get<String>("email").orEmpty())

    private val _uiState = MutableStateFlow(ForgotPasswordUiState())
    val uiState: StateFlow<ForgotPasswordUiState> = _uiState.asStateFlow()

    init {
        email.textChanges()
            .onEach { text -> _uiState.update { it.copy(emailIssue = it.emailIssue?.let { validateEmail(text) }) } }
            .launchIn(viewModelScope)
    }

    /** The API answers the same whether or not the account exists. */
    fun submit() {
        if (_uiState.value.isSubmitting) return
        val emailText = email.text.toString().trim()
        val issue = validateEmail(emailText)
        _uiState.update { it.copy(emailIssue = issue, error = null, isSubmitting = issue == null) }
        if (issue != null) return

        viewModelScope.launch {
            when (val outcome = auth.requestPasswordReset(emailText)) {
                is Outcome.Success -> _uiState.update { it.copy(isSubmitting = false, codeSentTo = emailText) }
                is Outcome.Failure -> _uiState.update { it.copy(isSubmitting = false, error = outcome.error) }
            }
        }
    }

    fun onCodeSentHandled() = _uiState.update { it.copy(codeSentTo = null) }
}
