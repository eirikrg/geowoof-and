package com.geowoof.feature.social.friends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geowoof.core.data.repository.SocialRepository
import com.geowoof.core.model.AppError
import com.geowoof.core.model.Friend
import com.geowoof.core.model.FriendRequest
import com.geowoof.core.model.Outcome
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FriendsUiState(
    val friends: List<Friend> = emptyList(),
    val isRefreshing: Boolean = true,
    val error: AppError? = null,
)

/** What Room already has stays on screen while the list refreshes (docs/decisions/0009). */
@HiltViewModel
class FriendsViewModel @Inject constructor(private val social: SocialRepository) : ViewModel() {
    private val status = MutableStateFlow(RefreshStatus())

    val uiState: StateFlow<FriendsUiState> = combine(social.friends, status) { friends, current ->
        FriendsUiState(friends, current.isRefreshing, current.error)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), FriendsUiState())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            status.value = RefreshStatus(isRefreshing = true)
            status.value = RefreshStatus(error = (social.refreshFriends() as? Outcome.Failure)?.error)
        }
    }

    fun onErrorShown() = status.update { it.copy(error = null) }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

data class FriendRequestsUiState(
    val received: List<FriendRequest> = emptyList(),
    val sent: List<FriendRequest> = emptyList(),
    val isRefreshing: Boolean = true,
    /** The request being answered, whose buttons wait for the server. */
    val answeringId: String? = null,
    val error: AppError? = null,
)

@HiltViewModel
class FriendRequestsViewModel @Inject constructor(private val social: SocialRepository) : ViewModel() {
    private val status = MutableStateFlow(RefreshStatus())
    private val answeringId = MutableStateFlow<String?>(null)

    val uiState: StateFlow<FriendRequestsUiState> =
        combine(social.receivedRequests, social.sentRequests, status, answeringId) {
                received,
                sent,
                current,
                answering,
            ->
            FriendRequestsUiState(received, sent, current.isRefreshing, answering, current.error)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), FriendRequestsUiState())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            status.value = RefreshStatus(isRefreshing = true)
            status.value = RefreshStatus(error = (social.refreshRequests() as? Outcome.Failure)?.error)
        }
    }

    fun onAccept(requestId: String) = answer(requestId) { social.acceptRequest(requestId) }

    fun onReject(requestId: String) = answer(requestId) { social.rejectRequest(requestId) }

    fun onCancel(requestId: String) = answer(requestId) { social.cancelRequest(requestId) }

    private fun answer(requestId: String, call: suspend () -> Outcome<Unit>) {
        if (answeringId.value != null) return
        answeringId.value = requestId
        viewModelScope.launch {
            val failure = call() as? Outcome.Failure
            answeringId.value = null
            if (failure != null) status.update { it.copy(error = failure.error) }
        }
    }

    fun onErrorShown() = status.update { it.copy(error = null) }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

private data class RefreshStatus(val isRefreshing: Boolean = false, val error: AppError? = null)
