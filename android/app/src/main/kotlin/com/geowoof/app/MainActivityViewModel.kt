package com.geowoof.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geowoof.core.data.repository.AuthRepository
import com.geowoof.core.data.repository.ChatRepository
import com.geowoof.core.data.repository.NotificationsRepository
import com.geowoof.core.data.repository.SocialRepository
import com.geowoof.core.model.SessionState
import com.geowoof.core.notifications.PushRegistrar
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** While [sessionState] is [SessionState.Loading], the splash screen stays visible. */
data class AppUiState(
    val sessionState: SessionState = SessionState.Loading,
    /** The user has just finished choosing a username: the last, optional step comes next. */
    val offerFirstPet: Boolean = false,
    /** The people waiting for an answer to follow this profile: the navigation bar shows it. */
    val pendingRequests: Int = 0,
    val unreadMessages: Int = 0,
    val unreadNotifications: Int = 0,
    /** A tapped notification, until there is a navigation to open it in. */
    val pendingLink: String? = null,
)

@HiltViewModel
class MainActivityViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val social: SocialRepository,
    private val chat: ChatRepository,
    private val notifications: NotificationsRepository,
    private val push: PushRegistrar,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AppUiState(sessionState = auth.sessionState.value))
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            auth.sessionState.collect { session -> _uiState.update { it.next(session) } }
        }

        // Picks up changes made elsewhere (email verified on another device, account deleted…).
        // A revoked session ends here: the authenticator clears it and the app shows the welcome screen.
        viewModelScope.launch {
            if (auth.sessionState.first { it != SessionState.Loading } is SessionState.SignedIn) {
                auth.refreshUser()
            }
        }

        viewModelScope.launch {
            social.receivedRequests.collect { requests -> _uiState.update { it.copy(pendingRequests = requests.size) } }
        }

        viewModelScope.launch {
            chat.unreadMessages.collect { count -> _uiState.update { it.copy(unreadMessages = count) } }
        }

        viewModelScope.launch {
            notifications.unreadCount.collect { count -> _uiState.update { it.copy(unreadNotifications = count) } }
        }

        viewModelScope.launch {
            auth.sessionState
                .map { it.onboardedUserId() }
                .distinctUntilChanged()
                .filterNotNull()
                .collect {
                    social.refreshRequests()
                    refreshInbox()
                    push.register()
                }
        }
    }

    /** Keeps the chat hub open while the app is visible and there is someone signed in. */
    suspend fun listenToChat() {
        auth.sessionState
            .map { it.onboardedUserId() }
            .distinctUntilChanged()
            .collectLatest { userId -> if (userId != null) chat.listen() }
    }

    /** Back in the app, someone may have asked to follow. If there is no connection, the number stays as it was. */
    fun onAppVisible() {
        if (auth.sessionState.value.onboardedUserId() == null) return
        viewModelScope.launch { social.refreshRequests() }
        refreshInbox()
    }

    private fun refreshInbox() {
        viewModelScope.launch { chat.refreshConversations() }
        viewModelScope.launch { notifications.refreshUnreadCount() }
    }

    fun onFirstPetStepDone() = _uiState.update { it.copy(offerFirstPet = false) }

    fun onLink(link: String) = _uiState.update { it.copy(pendingLink = link) }

    fun onLinkOpened() = _uiState.update { it.copy(pendingLink = null) }

    private fun SessionState.onboardedUserId(): String? =
        (this as? SessionState.SignedIn)?.user?.takeUnless { it.needsOnboarding }?.id

    private fun AppUiState.next(session: SessionState): AppUiState {
        val wasOnboarding = (sessionState as? SessionState.SignedIn)?.user?.needsOnboarding == true
        val isOnboarded = (session as? SessionState.SignedIn)?.user?.needsOnboarding == false
        return copy(
            sessionState = session,
            offerFirstPet = session is SessionState.SignedIn && (offerFirstPet || (wasOnboarding && isOnboarded)),
        )
    }
}
