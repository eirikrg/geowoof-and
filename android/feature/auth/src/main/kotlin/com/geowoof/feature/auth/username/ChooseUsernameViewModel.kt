package com.geowoof.feature.auth.username

import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geowoof.core.data.repository.AccountRepository
import com.geowoof.core.data.repository.AuthRepository
import com.geowoof.core.domain.CheckUsernameUseCase
import com.geowoof.core.domain.UsernameStatus
import com.geowoof.core.domain.ValidateDisplayNameUseCase
import com.geowoof.core.domain.ValidateUsernameUseCase
import com.geowoof.core.model.AppError
import com.geowoof.core.model.DisplayNameIssue
import com.geowoof.core.model.Outcome
import com.geowoof.core.model.SessionState
import com.geowoof.core.model.UsernameUnavailableReason
import com.geowoof.core.model.code
import com.geowoof.core.ui.textChanges
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

data class ChooseUsernameUiState(
    /** Result of the last check; `null` while there is nothing to report. */
    val usernameStatus: UsernameStatus? = null,
    val isCheckingUsername: Boolean = false,
    val displayNameIssue: DisplayNameIssue? = null,
    val isSubmitting: Boolean = false,
    val error: AppError? = null,
)

@HiltViewModel
class ChooseUsernameViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val accounts: AccountRepository,
    private val validateUsername: ValidateUsernameUseCase,
    private val checkUsername: CheckUsernameUseCase,
    private val validateDisplayName: ValidateDisplayNameUseCase,
) : ViewModel() {
    private val currentDisplayName: String?
        get() = (auth.sessionState.value as? SessionState.SignedIn)?.user?.displayName

    val username = TextFieldState()
    val displayName = TextFieldState(currentDisplayName.orEmpty())

    private val _uiState = MutableStateFlow(ChooseUsernameUiState())
    val uiState: StateFlow<ChooseUsernameUiState> = _uiState.asStateFlow()

    init {
        // Availability is checked once the user pauses typing; each keystroke cancels the pending check.
        viewModelScope.launch {
            username.textChanges().collectLatest { raw ->
                _uiState.update { it.copy(usernameStatus = null, isCheckingUsername = false) }
                if (raw.isBlank()) return@collectLatest
                delay(CHECK_DELAY)
                _uiState.update { it.copy(isCheckingUsername = true) }
                val status = checkUsername(raw)
                _uiState.update { it.copy(usernameStatus = status, isCheckingUsername = false) }
            }
        }
        displayName.textChanges()
            .onEach { text ->
                _uiState.update { it.copy(displayNameIssue = it.displayNameIssue?.let { validateDisplayName(text) }) }
            }
            .launchIn(viewModelScope)
    }

    /** Choosing the username ends the onboarding: the session changes and the app shows the main shell. */
    fun submit() {
        val state = _uiState.value
        if (state.isSubmitting) return
        val rawUsername = username.text.toString()
        val name = displayName.text.toString().trim()
        val usernameIssue = validateUsername(rawUsername)
        val nameIssue = validateDisplayName(name)
        val status = usernameIssue?.let(UsernameStatus::Invalid) ?: state.usernameStatus
        val canSubmit = usernameIssue == null && nameIssue == null && status !is UsernameStatus.Unavailable
        _uiState.update {
            it.copy(
                usernameStatus = status,
                isCheckingUsername = false,
                displayNameIssue = nameIssue,
                error = null,
                isSubmitting = canSubmit,
            )
        }
        if (!canSubmit) return

        viewModelScope.launch {
            if (name != currentDisplayName) {
                val updated = accounts.updateDisplayName(name)
                if (updated is Outcome.Failure) {
                    _uiState.update { it.copy(isSubmitting = false, error = updated.error) }
                    return@launch
                }
            }
            val outcome = accounts.chooseUsername(validateUsername.normalize(rawUsername))
            if (outcome is Outcome.Failure) {
                val unavailable = outcome.error.unavailableReason()
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        usernameStatus = unavailable?.let(UsernameStatus::Unavailable) ?: it.usernameStatus,
                        error = if (unavailable == null) outcome.error else null,
                    )
                }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch { auth.signOut() }
    }

    private fun AppError.unavailableReason(): UsernameUnavailableReason? = when (code) {
        "identity.username_taken" -> UsernameUnavailableReason.Taken
        "identity.username_reserved" -> UsernameUnavailableReason.Reserved
        else -> null
    }

    private companion object {
        val CHECK_DELAY = 400.milliseconds
    }
}
