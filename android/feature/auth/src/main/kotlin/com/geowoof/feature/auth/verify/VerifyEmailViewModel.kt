package com.geowoof.feature.auth.verify

import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geowoof.core.data.repository.AuthRepository
import com.geowoof.core.domain.ValidateVerificationCodeUseCase
import com.geowoof.core.model.AppError
import com.geowoof.core.model.CodeIssue
import com.geowoof.core.model.Outcome
import com.geowoof.core.model.SessionState
import com.geowoof.core.model.code
import com.geowoof.core.ui.textChanges
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

data class VerifyEmailUiState(
    val email: String = "",
    val codeIssue: CodeIssue? = null,
    val isVerifying: Boolean = false,
    val isResending: Boolean = false,
    /** Seconds until another code can be requested; 0 when it can be requested now. */
    val resendAvailableInSeconds: Int = 0,
    val codeResent: Boolean = false,
    val error: AppError? = null,
    /** The email is verified; the screen then moves on. */
    val isVerified: Boolean = false,
) {
    val canResend: Boolean get() = !isResending && resendAvailableInSeconds == 0
}

@HiltViewModel
class VerifyEmailViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val validateCode: ValidateVerificationCodeUseCase,
) : ViewModel() {
    val code = TextFieldState()

    private val _uiState = MutableStateFlow(
        VerifyEmailUiState(email = (auth.sessionState.value as? SessionState.SignedIn)?.user?.email.orEmpty()),
    )
    val uiState: StateFlow<VerifyEmailUiState> = _uiState.asStateFlow()

    private var cooldown: Job? = null

    init {
        code.textChanges()
            .onEach { text -> _uiState.update { it.copy(codeIssue = it.codeIssue?.let { validateCode(text) }) } }
            .launchIn(viewModelScope)
    }

    fun verify() {
        if (_uiState.value.isVerifying) return
        val codeText = code.text.toString()
        val issue = validateCode(codeText)
        _uiState.update { it.copy(codeIssue = issue, error = null, codeResent = false, isVerifying = issue == null) }
        if (issue != null) return

        viewModelScope.launch {
            when (val outcome = auth.verifyEmail(codeText)) {
                is Outcome.Success -> _uiState.update { it.copy(isVerifying = false, isVerified = true) }
                is Outcome.Failure -> onFailure(outcome.error)
            }
        }
    }

    fun resendCode() {
        if (!_uiState.value.canResend) return
        _uiState.update { it.copy(isResending = true, codeResent = false, error = null) }
        viewModelScope.launch {
            when (val outcome = auth.resendVerificationEmail()) {
                is Outcome.Success -> {
                    _uiState.update { it.copy(isResending = false, codeResent = true) }
                    startCooldown(RESEND_COOLDOWN_SECONDS)
                }

                is Outcome.Failure -> {
                    onFailure(outcome.error)
                    (outcome.error as? AppError.Api)?.retryAfterSeconds?.let { startCooldown(it.toInt()) }
                }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch { auth.signOut() }
    }

    private suspend fun onFailure(error: AppError) {
        if (error.code == EMAIL_ALREADY_VERIFIED) {
            // Verified meanwhile (for example, on another device): refresh the cached user.
            auth.refreshUser()
            _uiState.update { it.copy(isVerifying = false, isResending = false, isVerified = true) }
        } else {
            _uiState.update { it.copy(isVerifying = false, isResending = false, error = error) }
        }
    }

    private fun startCooldown(seconds: Int) {
        cooldown?.cancel()
        cooldown = viewModelScope.launch {
            for (remaining in seconds downTo 1) {
                _uiState.update { it.copy(resendAvailableInSeconds = remaining) }
                delay(1.seconds)
            }
            _uiState.update { it.copy(resendAvailableInSeconds = 0) }
        }
    }

    private companion object {
        /** The API accepts a new code every 60 s (docs/api.md). */
        const val RESEND_COOLDOWN_SECONDS = 60
        const val EMAIL_ALREADY_VERIFIED = "auth.email_already_verified"
    }
}
