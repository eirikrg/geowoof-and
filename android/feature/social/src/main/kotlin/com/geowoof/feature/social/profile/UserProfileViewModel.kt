package com.geowoof.feature.social.profile

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.geowoof.core.data.repository.PeopleRepository
import com.geowoof.core.data.repository.SocialRepository
import com.geowoof.core.model.AppError
import com.geowoof.core.model.Outcome
import com.geowoof.core.model.Pet
import com.geowoof.core.model.Profile
import com.geowoof.core.model.Relationship
import com.geowoof.core.model.RelationshipStatus
import com.geowoof.core.model.ReportReason
import com.geowoof.core.model.Walk
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ProfileDialog { ConfirmRemoveFriend, ConfirmBlock, Report, VerifyEmail }

data class UserProfileUiState(
    val isLoading: Boolean = true,
    val profile: Profile? = null,
    val relationship: Relationship? = null,
    val pets: List<Pet> = emptyList(),
    /** It does not exist, or there is a block in some direction: the server does not say which. */
    val isUnavailable: Boolean = false,
    /** It could not be loaded, and retrying may help. */
    val loadError: AppError? = null,
    /** An action is waiting for the server. */
    val isActing: Boolean = false,
    val actionError: AppError? = null,
    val dialog: ProfileDialog? = null,
    val reportReason: ReportReason? = null,
    val wasReported: Boolean = false,
    /** Blocked: there is nothing left to show here. */
    val isFinished: Boolean = false,
)

/** Each public function is one thing the user can do with this person. */
@Suppress("TooManyFunctions")
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class UserProfileViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val people: PeopleRepository,
    private val social: SocialRepository,
) : ViewModel() {
    private val userId: String = checkNotNull(savedStateHandle["userId"])
    private val state = MutableStateFlow(UserProfileUiState())
    private val walkReloads = MutableStateFlow(0)

    val uiState: StateFlow<UserProfileUiState> = state.asStateFlow()

    val reportDetails = TextFieldState()

    /** Becoming friends, or no longer being friends, changes which walks can be seen. */
    val walks: Flow<PagingData<Walk>> = walkReloads.flatMapLatest { people.walks(userId) }.cachedIn(viewModelScope)

    init {
        refresh()
    }

    fun refresh() {
        state.update { it.copy(isLoading = true, loadError = null) }
        viewModelScope.launch {
            val profile = people.profile(userId)
            val relationship = social.relationship(userId)
            val pets = people.pets(userId)
            val failure = listOf(profile, relationship, pets).filterIsInstance<Outcome.Failure>().firstOrNull()?.error
            state.update {
                it.copy(
                    isLoading = false,
                    profile = (profile as? Outcome.Success)?.value ?: it.profile,
                    relationship = (relationship as? Outcome.Success)?.value ?: it.relationship,
                    pets = (pets as? Outcome.Success)?.value ?: it.pets,
                    isUnavailable = failure.isNotFound(),
                    loadError = failure.takeUnless { error -> error.isNotFound() },
                )
            }
        }
    }

    fun onSendRequest() = act {
        when (val result = social.sendRequest(userId)) {
            is Outcome.Success -> {
                onRelationshipChanged(result.value)
                null
            }

            is Outcome.Failure -> if (result.error.isEmailNotVerified()) {
                state.update { it.copy(dialog = ProfileDialog.VerifyEmail) }
                null
            } else {
                result.error
            }
        }
    }

    fun onAcceptRequest() = answerRequest(social::acceptRequest)

    fun onRejectRequest() = answerRequest(social::rejectRequest)

    fun onCancelRequest() = answerRequest(social::cancelRequest)

    private fun answerRequest(answer: suspend (String) -> Outcome<Unit>) {
        val requestId = state.value.relationship?.requestId ?: return
        act { reloadRelationshipAfter(answer(requestId)) }
    }

    fun onShowDialog(dialog: ProfileDialog) = state.update { it.copy(dialog = dialog) }

    fun onDismissDialog() {
        reportDetails.clearText()
        state.update { it.copy(dialog = null, reportReason = null) }
    }

    fun onConfirmRemoveFriend() {
        state.update { it.copy(dialog = null) }
        act { reloadRelationshipAfter(social.removeFriend(userId)) }
    }

    fun onConfirmBlock() {
        state.update { it.copy(dialog = null) }
        act {
            when (val result = social.block(userId)) {
                is Outcome.Success -> {
                    state.update { it.copy(isFinished = true) }
                    null
                }

                is Outcome.Failure -> result.error
            }
        }
    }

    fun onSelectReportReason(reason: ReportReason) = state.update { it.copy(reportReason = reason) }

    fun onConfirmReport() {
        val reason = state.value.reportReason ?: return
        val details = reportDetails.text.toString()
        onDismissDialog()
        act {
            when (val result = social.report(userId, reason, details)) {
                is Outcome.Success -> {
                    state.update { it.copy(wasReported = true) }
                    null
                }

                is Outcome.Failure -> result.error
            }
        }
    }

    fun onMessageShown() = state.update { it.copy(actionError = null, wasReported = false) }

    private suspend fun reloadRelationshipAfter(result: Outcome<Unit>): AppError? {
        val error = (result as? Outcome.Failure)?.error
        // Also after a failure: if the server said no, the other person has done something meanwhile.
        (social.relationship(userId) as? Outcome.Success)?.let { onRelationshipChanged(it.value) }
        return error
    }

    private fun onRelationshipChanged(relationship: Relationship) {
        val hasChanged = state.value.relationship?.status != relationship.status
        state.update { it.copy(relationship = relationship) }
        if (hasChanged &&
            (relationship.status == RelationshipStatus.Friends || relationship.status == RelationshipStatus.None)
        ) {
            walkReloads.update { it + 1 }
        }
    }

    /** One action at a time. [action] answers with the error to show, if there is one. */
    private fun act(action: suspend () -> AppError?) {
        if (state.value.isActing) return
        state.update { it.copy(isActing = true) }
        viewModelScope.launch {
            val error = action()
            state.update { it.copy(isActing = false, actionError = error) }
        }
    }

    private fun AppError?.isNotFound() = this is AppError.Api && status == HTTP_NOT_FOUND

    private fun AppError.isEmailNotVerified() = this is AppError.Api && status == HTTP_FORBIDDEN

    private companion object {
        const val HTTP_FORBIDDEN = 403
        const val HTTP_NOT_FOUND = 404
    }
}
