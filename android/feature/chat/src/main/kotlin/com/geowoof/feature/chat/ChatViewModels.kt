package com.geowoof.feature.chat

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.geowoof.core.data.repository.AuthRepository
import com.geowoof.core.data.repository.ChatRepository
import com.geowoof.core.data.repository.OpenConversations
import com.geowoof.core.data.repository.PeopleRepository
import com.geowoof.core.model.AppError
import com.geowoof.core.model.ChatMessage
import com.geowoof.core.model.Conversation
import com.geowoof.core.model.Outcome
import com.geowoof.core.model.PeopleListKind
import com.geowoof.core.model.Person
import com.geowoof.core.model.SessionState
import com.geowoof.core.model.UserSummary
import com.geowoof.core.ui.textChanges
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

private const val STOP_TIMEOUT_MILLIS = 5_000L

private fun AuthRepository.myUserId(): String? = (sessionState.value as? SessionState.SignedIn)?.user?.id

data class ConversationsUiState(
    val conversations: List<Conversation> = emptyList(),
    val myUserId: String? = null,
    /** Only while there is nothing to show yet. */
    val isLoading: Boolean = true,
    val error: AppError? = null,
)

@HiltViewModel
class ConversationsViewModel @Inject constructor(private val chat: ChatRepository, auth: AuthRepository) : ViewModel() {
    private val isLoading = MutableStateFlow(true)
    private val error = MutableStateFlow<AppError?>(null)
    private val myUserId = auth.myUserId()

    val uiState: StateFlow<ConversationsUiState> = combine(chat.conversations, isLoading, error) {
            conversations,
            loading,
            failure,
        ->
        ConversationsUiState(conversations, myUserId, loading && conversations.isEmpty(), failure)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        ConversationsUiState(myUserId = myUserId),
    )

    fun onResume() {
        viewModelScope.launch {
            val answer = chat.refreshConversations()
            isLoading.value = false
            error.value = (answer as? Outcome.Failure)?.error
        }
    }

    fun onErrorShown() {
        error.value = null
    }
}

/** The friends of this person: they are the ones who can be written to. */
@HiltViewModel
class NewConversationViewModel @Inject constructor(people: PeopleRepository, auth: AuthRepository) : ViewModel() {
    val friends: Flow<PagingData<Person>> = auth.myUserId()
        ?.let { people.people(it, PeopleListKind.Friends).cachedIn(viewModelScope) }
        ?: emptyFlow()
}

data class ConversationUiState(
    val myUserId: String? = null,
    /** Until the conversation is known. */
    val isLoading: Boolean = true,
    /** It does not exist, or it is no longer available. */
    val isGone: Boolean = false,
    val otherUser: UserSummary? = null,
    val canSend: Boolean = false,
    /** From the newest to the oldest. */
    val messages: List<ChatMessage> = emptyList(),
    val otherLastReadMessageId: String? = null,
    val isOtherTyping: Boolean = false,
    val isLoadingOlder: Boolean = false,
    val error: AppError? = null,
)

private data class ConversationExtras(
    val isGone: Boolean = false,
    val hasOlder: Boolean = true,
    val isLoadingOlder: Boolean = false,
    val error: AppError? = null,
)

/**
 * One conversation, by its id or by the person it is with. While it is on the screen what arrives
 * counts as read, and no notification interrupts it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ConversationViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val chat: ChatRepository,
    auth: AuthRepository,
    private val openConversations: OpenConversations,
    private val clock: Clock,
) : ViewModel() {
    private val myUserId = auth.myUserId()
    private val conversationId = MutableStateFlow(savedStateHandle.get<String>("conversationId"))
    private val extras = MutableStateFlow(ConversationExtras())
    private var isVisible = false
    private var lastTypingAtMs = 0L

    val input = TextFieldState()

    val uiState: StateFlow<ConversationUiState> = conversationId.flatMapLatest { id ->
        if (id == null) {
            extras.map { it.toUiState(conversation = null, messages = emptyList(), isTyping = false) }
        } else {
            combine(chat.conversation(id), chat.messages(id), chat.isTyping(id), extras) {
                    conversation,
                    messages,
                    typing,
                    current,
                ->
                current.toUiState(conversation, messages, typing)
            }
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        ConversationUiState(myUserId = myUserId),
    )

    init {
        val userId = savedStateHandle.get<String>("userId")
        if (conversationId.value == null && userId != null) {
            viewModelScope.launch {
                when (val opened = chat.openWith(userId)) {
                    is Outcome.Failure -> extras.update { it.copy(isGone = true, error = opened.error) }

                    is Outcome.Success -> {
                        conversationId.value = opened.value
                        if (isVisible) onOpen(opened.value)
                    }
                }
            }
        }
        viewModelScope.launch { markReadAsMessagesArrive() }
        viewModelScope.launch { tellWhileTyping() }
    }

    private fun ConversationExtras.toUiState(
        conversation: Conversation?,
        messages: List<ChatMessage>,
        isTyping: Boolean,
    ) = ConversationUiState(
        myUserId = myUserId,
        isLoading = conversation == null && !isGone,
        isGone = isGone,
        otherUser = conversation?.otherUser,
        canSend = conversation?.canSend == true,
        messages = messages,
        otherLastReadMessageId = conversation?.otherLastReadMessageId,
        isOtherTyping = isTyping,
        isLoadingOlder = isLoadingOlder,
        error = error,
    )

    private suspend fun markReadAsMessagesArrive() {
        val id = conversationId.filterNotNull().first()
        chat.messages(id)
            .map { messages -> messages.firstOrNull { it.id != null && it.senderId != myUserId }?.id }
            .distinctUntilChanged()
            .collect { if (it != null && isVisible) chat.markRead(id) }
    }

    /** The server takes it every 3 s, so more would be wasted. */
    private suspend fun tellWhileTyping() {
        input.textChanges().collect { text ->
            val id = conversationId.value
            val now = clock.millis()
            val hasSomethingToTell = text.isNotBlank() && uiState.value.canSend
            if (id != null && hasSomethingToTell && now - lastTypingAtMs >= TYPING_INTERVAL_MS) {
                lastTypingAtMs = now
                chat.setTyping(id)
            }
        }
    }

    fun onResume() {
        isVisible = true
        conversationId.value?.let(::onOpen)
    }

    fun onPause() {
        isVisible = false
        conversationId.value?.let(openConversations::close)
    }

    private fun onOpen(id: String) {
        openConversations.open(id)
        viewModelScope.launch {
            val caughtUp = chat.catchUp(id)
            if (caughtUp is Outcome.Failure && chat.conversation(id).first() == null) {
                // Without a connection nothing can be said about it, and the reason has to be told.
                extras.update {
                    it.copy(
                        isGone = true,
                        error = caughtUp.error.takeIf { error ->
                            error ==
                                AppError.Network
                        },
                    )
                }
            }
            chat.markRead(id)
        }
    }

    fun onLoadOlder() {
        val id = conversationId.value ?: return
        val current = extras.value
        if (current.isLoadingOlder || !current.hasOlder) return
        extras.update { it.copy(isLoadingOlder = true) }
        viewModelScope.launch {
            when (val older = chat.loadOlder(id)) {
                is Outcome.Failure -> extras.update { it.copy(isLoadingOlder = false) }
                is Outcome.Success -> extras.update { it.copy(isLoadingOlder = false, hasOlder = older.value) }
            }
        }
    }

    fun onSend() {
        val id = conversationId.value ?: return
        val body = input.text.toString().trim()
        if (body.isEmpty()) return
        input.clearText()
        send(id, body, imageUri = null)
    }

    fun onPickPhoto(contentUri: String) {
        conversationId.value?.let { send(it, body = null, imageUri = contentUri) }
    }

    private fun send(id: String, body: String?, imageUri: String?) {
        viewModelScope.launch {
            val sent = chat.send(id, body, imageUri)
            if (sent is Outcome.Failure) extras.update { it.copy(error = sent.error) }
        }
    }

    fun onRetry(clientMessageId: String) {
        viewModelScope.launch { chat.retry(clientMessageId) }
    }

    fun onErrorShown() = extras.update { it.copy(error = null) }

    override fun onCleared() {
        conversationId.value?.let(openConversations::close)
    }

    private companion object {
        const val TYPING_INTERVAL_MS = 3_000L
    }
}
