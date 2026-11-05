package com.geowoof.feature.social

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geowoof.core.data.repository.SocialRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CommunityUiState(val friendCount: Int = 0, val requestCount: Int = 0)

@HiltViewModel
class CommunityViewModel @Inject constructor(private val social: SocialRepository) : ViewModel() {
    val uiState: StateFlow<CommunityUiState> =
        combine(social.friends, social.receivedRequests) { friends, requests ->
            CommunityUiState(friends.size, requests.size)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), CommunityUiState())

    /** Each list says so if it cannot refresh: here, the numbers just stay as they were. */
    fun refresh() {
        viewModelScope.launch {
            social.refreshFriends()
            social.refreshRequests()
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
