package com.geowoof.feature.profile

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.geowoof.core.data.repository.PeopleRepository
import com.geowoof.core.data.repository.PostsRepository
import com.geowoof.core.data.repository.SocialRepository
import com.geowoof.core.model.AppError
import com.geowoof.core.model.Outcome
import com.geowoof.core.model.Relationship
import com.geowoof.core.model.ReportReason
import com.geowoof.core.model.TimelineItem
import com.geowoof.core.model.Walk
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The profile of someone else, which always comes from the server: a block or a change of visibility has
 * to count at once (docs/decisions/0009). Each public function is one thing the user can do with this person.
 */
@Suppress("TooManyFunctions")
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class UserProfileViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val people: PeopleRepository,
    private val social: SocialRepository,
    private val postsRepository: PostsRepository,
) : ViewModel() {
    private val userId: String = checkNotNull(savedStateHandle["userId"])
    private val state = MutableStateFlow(ProfileUiState())
    private val walkReloads = MutableStateFlow(0)
    private var isLoadingNow = false

    val uiState: StateFlow<ProfileUiState> = state.asStateFlow()

    val reportDetails = TextFieldState()

    /** Which walks can be seen depends on the relationship. A private profile is not even asked. */
    val walks: Flow<PagingData<Walk>> = walkReloads.flatMapLatest {
        if (state.value.canSeeContent) people.walks(userId) else flowOf(PagingData.empty())
    }.cachedIn(viewModelScope)

    /** The same for the posts. */
    val posts: Flow<PagingData<TimelineItem>> = walkReloads.flatMapLatest {
        if (state.value.canSeeContent) postsRepository.userPosts(userId) else flowOf(PagingData.empty())
    }.cachedIn(viewModelScope)

    init {
        refresh()
    }

    fun refresh() {
        state.update { it.copy(isLoading = true, loadError = null) }
        reload(alwaysReloadWalks = true)
    }

    /** Back from another screen, the numbers or the relationship may have changed. */
    fun onResume() = reload(alwaysReloadWalks = false)

    private fun reload(alwaysReloadWalks: Boolean) {
        if (isLoadingNow) return
        isLoadingNow = true
        viewModelScope.launch {
            loadProfile(alwaysReloadWalks)
            isLoadingNow = false
        }
    }

    /** The app makes one call to each module: the server does not join them (docs/decisions/0002). */
    private suspend fun loadProfile(alwaysReloadWalks: Boolean) {
        val before = state.value
        val (profile, relationship, counts) = coroutineScope {
            val profile = async { people.profile(userId) }
            val relationship = async { social.relationship(userId) }
            val counts = async { people.counts(userId) }
            Triple(profile.await(), relationship.await(), counts.await())
        }
        val loaded = before.copy(
            profile = (profile as? Outcome.Success)?.value ?: before.profile,
            relationship = (relationship as? Outcome.Success)?.value ?: before.relationship,
            counts = (counts as? Outcome.Success)?.value ?: before.counts,
        )
        val (pets, totals) = coroutineScope {
            val pets = async { if (loaded.canSeeContent) people.pets(userId) else Outcome.Success(emptyList()) }
            val totals = async { if (loaded.canSeeContent) people.walkTotals(userId) else null }
            pets.await() to totals.await()
        }
        val failure = listOfNotNull(profile, relationship, counts, pets, totals)
            .filterIsInstance<Outcome.Failure>()
            .firstOrNull { !it.error.isProfilePrivate() }
            ?.error
        val hasNewAccess = before.relationship?.status != loaded.relationship?.status ||
            before.canSeeContent != loaded.canSeeContent
        state.update {
            it.copy(
                isLoading = false,
                profile = loaded.profile,
                relationship = loaded.relationship,
                counts = loaded.counts,
                pets = if (loaded.canSeeContent) (pets as? Outcome.Success)?.value ?: it.pets else emptyList(),
                totals = if (loaded.canSeeContent) (totals as? Outcome.Success)?.value ?: it.totals else null,
                isUnavailable = failure.isNotFound(),
                loadError = failure.takeUnless { error -> error.isNotFound() },
            )
        }
        if (alwaysReloadWalks || hasNewAccess) walkReloads.update { it + 1 }
    }

    fun onFollow() = changeRelationship { social.follow(userId) }

    /** Also for a request that is still pending: the server treats both the same. */
    fun onUnfollow() = changeRelationship { social.unfollow(userId) }

    fun onAcceptRequest() {
        val requestId = state.value.relationship?.incomingRequestId ?: return
        changeRelationship { social.acceptRequest(requestId) }
    }

    fun onRejectRequest() {
        val requestId = state.value.relationship?.incomingRequestId ?: return
        act {
            val error = (social.rejectRequest(requestId) as? Outcome.Failure)?.error
            loadProfile(alwaysReloadWalks = false)
            error
        }
    }

    fun onShowDialog(dialog: ProfileDialog) = state.update { it.copy(dialog = dialog) }

    fun onDismissDialog() {
        reportDetails.clearText()
        state.update { it.copy(dialog = null, reportReason = null) }
    }

    fun onConfirmFollowBack() {
        state.update { it.copy(dialog = null) }
        onFollow()
    }

    fun onConfirmUnfollow() {
        state.update { it.copy(dialog = null) }
        onUnfollow()
    }

    fun onConfirmRemoveFollower() {
        state.update { it.copy(dialog = null) }
        changeRelationship { social.removeFollower(userId) }
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

    /** A failure to load with nothing on screen stays there, with its button to retry. */
    fun onMessageShown() = state.update {
        it.copy(actionError = null, wasReported = false, loadError = it.loadError.takeIf { _ -> it.profile == null })
    }

    /** Any change can give or take access, and it changes the numbers of the two people. */
    private fun changeRelationship(call: suspend () -> Outcome<Relationship>) = act {
        when (val result = call()) {
            is Outcome.Success -> {
                state.update { it.copy(relationship = result.value) }
                loadProfile(alwaysReloadWalks = true)
                null
            }

            is Outcome.Failure -> if (result.error.isEmailNotVerified()) {
                state.update { it.copy(dialog = ProfileDialog.VerifyEmail) }
                null
            } else {
                // If the server said no, the other person has done something meanwhile.
                loadProfile(alwaysReloadWalks = false)
                result.error
            }
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

    private fun AppError.isEmailNotVerified() = this is AppError.Api && code == CODE_FORBIDDEN

    /** The person has just made their profile private: the next load shows the restricted view. */
    private fun AppError.isProfilePrivate() = this is AppError.Api && code == CODE_PROFILE_PRIVATE

    private companion object {
        const val HTTP_NOT_FOUND = 404
        const val CODE_FORBIDDEN = "auth.forbidden"
        const val CODE_PROFILE_PRIVATE = "identity.profile_private"
    }
}
