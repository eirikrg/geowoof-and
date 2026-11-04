package com.geowoof.feature.settings.account

import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geowoof.core.data.credentials.PasswordSaveRequests
import com.geowoof.core.data.repository.AuthRepository
import com.geowoof.core.domain.ValidatePasswordUseCase
import com.geowoof.core.model.AppError
import com.geowoof.core.model.Outcome
import com.geowoof.core.model.PasswordIssue
import com.geowoof.core.model.SessionState
import com.geowoof.core.model.User
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

data class ChangePasswordUiState(
    /** Accounts created with Google have no password yet: they create one without the current. */
    val hasPassword: Boolean = true,
    val isCurrentPasswordMissing: Boolean = false,
    val newPasswordIssue: PasswordIssue? = null,
    val isSubmitting: Boolean = false,
    val error: AppError? = null,
    /** The password was changed; the screen then goes back. */
    val isDone: Boolean = false,
)

@HiltViewModel
class ChangePasswordViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val validatePassword: ValidatePasswordUseCase,
    private val passwordSaves: PasswordSaveRequests,
) : ViewModel() {
    private val user: User? get() = (auth.sessionState.value as? SessionState.SignedIn)?.user

    val currentPassword = TextFieldState()
    val newPassword = TextFieldState()

    private val _uiState = MutableStateFlow(ChangePasswordUiState(hasPassword = user?.hasPassword != false))
    val uiState: StateFlow<ChangePasswordUiState> = _uiState.asStateFlow()

    init {
        currentPassword.textChanges()
            .onEach { text ->
                _uiState.update {
                    it.copy(
                        isCurrentPasswordMissing =
                            it.isCurrentPasswordMissing && text.isEmpty(),
                    )
                }
            }
            .launchIn(viewModelScope)
        newPassword.textChanges()
            .onEach { text ->
                _uiState.update { it.copy(newPasswordIssue = it.newPasswordIssue?.let { validatePassword(text) }) }
            }
            .launchIn(viewModelScope)
    }

    /** The API starts a new session and closes the others (docs/api.md). */
    fun submit() {
        val state = _uiState.value
        if (state.isSubmitting) return
        val current = currentPassword.text.toString().takeIf { state.hasPassword }
        val new = newPassword.text.toString()
        val isCurrentMissing = state.hasPassword && current.isNullOrEmpty()
        val issue = validatePassword(new)
        val isValid = !isCurrentMissing && issue == null
        _uiState.update {
            it.copy(
                isCurrentPasswordMissing = isCurrentMissing,
                newPasswordIssue = issue,
                error = null,
                isSubmitting = isValid,
            )
        }
        if (!isValid) return

        viewModelScope.launch {
            when (val outcome = auth.changePassword(current, new)) {
                is Outcome.Success -> {
                    user?.let { passwordSaves.request(it.email, new) }
                    _uiState.update { it.copy(isSubmitting = false, isDone = true) }
                }

                is Outcome.Failure -> _uiState.update { it.copy(isSubmitting = false, error = outcome.error) }
            }
        }
    }
}
