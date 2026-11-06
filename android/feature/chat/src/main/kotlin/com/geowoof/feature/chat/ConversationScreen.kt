package com.geowoof.feature.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geowoof.core.designsystem.component.EmptyState
import com.geowoof.core.designsystem.icon.GeoWoofIcons
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.core.model.ChatMessage
import com.geowoof.core.model.MessageStatus
import com.geowoof.core.model.UserSummary
import com.geowoof.core.ui.RemoteAvatar
import com.geowoof.core.ui.RemotePhoto
import com.geowoof.core.ui.rememberPhotoPicker
import com.geowoof.core.ui.userMessage
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.Instant

@Composable
internal fun ConversationScreen(
    onBack: () -> Unit,
    onOpenUser: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ConversationViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pickPhoto = rememberPhotoPicker(viewModel::onPickPhoto)
    LifecycleResumeEffect(viewModel) {
        viewModel.onResume()
        onPauseOrDispose { viewModel.onPause() }
    }
    ConversationContent(
        uiState = uiState,
        input = viewModel.input,
        actions = ConversationActions(
            onBack = onBack,
            onOpenUser = onOpenUser,
            onSend = viewModel::onSend,
            onPickPhoto = pickPhoto,
            onRetry = viewModel::onRetry,
            onLoadOlder = viewModel::onLoadOlder,
            onDismissError = viewModel::onErrorShown,
        ),
        modifier = modifier,
    )
}

@Suppress("LongParameterList")
internal class ConversationActions(
    val onBack: () -> Unit,
    val onOpenUser: (String) -> Unit,
    val onSend: () -> Unit,
    val onPickPhoto: () -> Unit,
    val onRetry: (String) -> Unit,
    val onLoadOlder: () -> Unit,
    val onDismissError: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConversationContent(
    uiState: ConversationUiState,
    input: TextFieldState,
    actions: ConversationActions,
    modifier: Modifier = Modifier,
) {
    val snackbars = remember { SnackbarHostState() }
    val errorMessage = uiState.error?.userMessage()
    val currentActions by rememberUpdatedState(actions)
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            snackbars.showSnackbar(errorMessage)
            currentActions.onDismissError()
        }
    }
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbars) },
        topBar = { ConversationTopBar(uiState.otherUser, actions) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding(),
        ) {
            when {
                uiState.isGone -> EmptyState(
                    icon = GeoWoofIcons.Forum,
                    title = stringResource(R.string.feature_chat_conversation_gone),
                    message = "",
                    modifier = Modifier.weight(1f),
                )

                uiState.isLoading -> Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }

                else -> {
                    Messages(uiState, actions, Modifier.weight(1f))
                    Footer(uiState, input, actions)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversationTopBar(otherUser: UserSummary?, actions: ConversationActions, modifier: Modifier = Modifier) {
    TopAppBar(
        modifier = modifier,
        navigationIcon = {
            IconButton(onClick = actions.onBack) {
                Icon(
                    painter = painterResource(GeoWoofIcons.ArrowBack),
                    contentDescription = stringResource(R.string.feature_chat_back),
                )
            }
        },
        title = {
            otherUser?.let { user ->
                Row(
                    modifier = Modifier.clickable { actions.onOpenUser(user.id) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(GeoWoofTheme.spacing.medium),
                ) {
                    RemoteAvatar(
                        image = user.avatar,
                        fallbackIcon = GeoWoofIcons.Person,
                        contentDescription = null,
                        size = TopBarAvatarSize,
                    )
                    Text(user.displayName)
                }
            }
        },
    )
}

@Composable
private fun Messages(uiState: ConversationUiState, actions: ConversationActions, modifier: Modifier = Modifier) {
    if (uiState.messages.isEmpty()) {
        EmptyState(
            icon = GeoWoofIcons.ChatBubble,
            title = stringResource(R.string.feature_chat_no_messages),
            message = stringResource(R.string.feature_chat_no_messages_hint),
            modifier = modifier,
        )
        return
    }

    val listState = rememberLazyListState()
    val currentOnLoadOlder by rememberUpdatedState(actions.onLoadOlder)
    val messageCount = uiState.messages.size
    LaunchedEffect(listState, messageCount) {
        snapshotFlow { (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= messageCount - OLDER_MARGIN }
            .distinctUntilChanged()
            .collect { isNearTheTop -> if (isNearTheTop) currentOnLoadOlder() }
    }

    // Upside down: what comes next is always at the bottom. It only follows it if it was already
    // there or if it is what this person has just sent: someone reading further up stays there.
    // It has to be read while composing: once it lays them out, the list keeps in place what was on
    // screen, as many positions up as messages have arrived.
    val isAtTheBottom by remember(listState) { derivedStateOf { listState.firstVisibleItemIndex == 0 } }
    val newest = uiState.messages.first()
    val shouldFollow = isAtTheBottom || newest.senderId == uiState.myUserId
    LaunchedEffect(newest.clientMessageId) {
        if (shouldFollow) listState.animateScrollToItem(0)
    }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        state = listState,
        reverseLayout = true,
        verticalArrangement = Arrangement.spacedBy(GeoWoofTheme.spacing.small),
    ) {
        itemsIndexed(uiState.messages, key = { _, message -> message.clientMessageId }) { index, message ->
            Column {
                // Upside down: the day goes before the oldest message of that day, which comes later in the list.
                val older = uiState.messages.getOrNull(index + 1)
                if (older == null || !older.sentAt.isSameDayAs(message.sentAt)) DayLabel(message.sentAt)
                Bubble(message, uiState, onRetry = actions.onRetry)
            }
        }
        if (uiState.isLoadingOlder) {
            item(key = "older") {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(GeoWoofTheme.spacing.medium),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(ProgressSize))
                }
            }
        }
    }
}

/** The day of the messages that follow, in a chip, so that each message only has to say its time. */
@Composable
private fun DayLabel(moment: Instant, modifier: Modifier = Modifier) {
    val spacing = GeoWoofTheme.spacing
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = spacing.small),
        contentAlignment = Alignment.Center,
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainer) {
            Text(
                text = formatChatDay(moment),
                modifier = Modifier.padding(horizontal = spacing.medium, vertical = spacing.extraSmall),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Bubble(
    message: ChatMessage,
    uiState: ConversationUiState,
    onRetry: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isMine = message.senderId == uiState.myUserId
    val spacing = GeoWoofTheme.spacing
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.medium),
        contentAlignment = if (isMine) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Surface(
            // The corner that points to who wrote it is less rounded.
            shape = if (isMine) MyBubbleShape else OtherBubbleShape,
            color = if (isMine) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
            modifier = Modifier
                .widthIn(max = BubbleMaxWidth)
                .then(
                    if (message.status == MessageStatus.Failed) {
                        Modifier.clickable { onRetry(message.clientMessageId) }
                    } else {
                        Modifier
                    },
                ),
        ) {
            Column(
                modifier = Modifier.padding(spacing.small),
                verticalArrangement = Arrangement.spacedBy(spacing.extraSmall),
            ) {
                if (message.image != null || message.localImagePath != null) {
                    RemotePhoto(
                        image = message.image,
                        localPath = message.localImagePath,
                        contentDescription = stringResource(R.string.feature_chat_photo),
                        modifier = Modifier.widthIn(max = BubbleMaxWidth),
                    )
                }
                message.body?.let {
                    Text(
                        text = it,
                        modifier = Modifier.padding(horizontal = spacing.extraSmall),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Row(
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(horizontal = spacing.extraSmall),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.extraSmall),
                ) {
                    Text(
                        text = formatChatClock(message.sentAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalContentColor.current.copy(alpha = TIME_ALPHA),
                    )
                    if (isMine) StatusIcon(message, uiState.otherLastReadMessageId)
                }
                if (message.status == MessageStatus.Failed) {
                    Text(
                        text = stringResource(R.string.feature_chat_failed),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

/** A clock while it waits, one check when it has been sent and two when the other person has read it. */
@Composable
private fun StatusIcon(message: ChatMessage, otherLastRead: String?, modifier: Modifier = Modifier) {
    // The ids grow with time, so everything up to the last one read has been read.
    val id = message.id
    val isRead = id != null && otherLastRead != null && id <= otherLastRead
    val (icon, description) = when {
        message.status == MessageStatus.Pending -> GeoWoofIcons.Schedule to R.string.feature_chat_status_pending
        message.status == MessageStatus.Failed -> GeoWoofIcons.Error to R.string.feature_chat_status_failed
        isRead -> GeoWoofIcons.DoneAll to R.string.feature_chat_status_read
        else -> GeoWoofIcons.Check to R.string.feature_chat_status_sent
    }
    Icon(
        painter = painterResource(icon),
        contentDescription = stringResource(description),
        modifier = modifier.size(StatusIconSize),
    )
}

@Composable
private fun Footer(
    uiState: ConversationUiState,
    input: TextFieldState,
    actions: ConversationActions,
    modifier: Modifier = Modifier,
) {
    val spacing = GeoWoofTheme.spacing
    Column(modifier = modifier.fillMaxWidth().navigationBarsPadding()) {
        AnimatedVisibility(visible = uiState.isOtherTyping) {
            Text(
                text = stringResource(R.string.feature_chat_typing, uiState.otherUser?.displayName.orEmpty()),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = spacing.large, vertical = spacing.extraSmall),
            )
        }
        if (!uiState.canSend) {
            Text(
                text = stringResource(R.string.feature_chat_cannot_send),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(spacing.large),
            )
            return
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(spacing.small),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.small),
        ) {
            IconButton(onClick = actions.onPickPhoto) {
                Icon(
                    painter = painterResource(GeoWoofIcons.Image),
                    contentDescription = stringResource(R.string.feature_chat_send_photo),
                )
            }
            OutlinedTextField(
                state = input,
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.feature_chat_message_hint)) },
                lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = MAX_INPUT_LINES),
                shape = MaterialTheme.shapes.large,
            )
            FilledIconButton(onClick = actions.onSend, modifier = Modifier.size(SendButtonSize)) {
                Icon(
                    painter = painterResource(GeoWoofIcons.Send),
                    contentDescription = stringResource(R.string.feature_chat_send),
                )
            }
        }
    }
}

private val TopBarAvatarSize = 36.dp
private val BubbleMaxWidth = 280.dp
private val ProgressSize = 24.dp
private val StatusIconSize = 14.dp
private val SendButtonSize = 52.dp
private val MyBubbleShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 6.dp)
private val OtherBubbleShape =
    RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 6.dp, bottomEnd = 20.dp)
private const val TIME_ALPHA = 0.7f
private const val OLDER_MARGIN = 5
private const val MAX_INPUT_LINES = 5

@PreviewLightDark
@Composable
private fun ConversationPreview() {
    val sentAt = Instant.parse("2026-11-05T10:00:00Z")
    GeoWoofTheme {
        ConversationContent(
            uiState = ConversationUiState(
                myUserId = "me",
                isLoading = false,
                canSend = true,
                otherUser = UserSummary("bea-id", "bea", "Bea", null, null),
                messages = listOf(
                    ChatMessage("2", "2", "1", "bea-id", "¡Perfecto!", null, null, MessageStatus.Sent, null, sentAt),
                    ChatMessage("1", "1", "1", "me", "¿Salimos a las 7?", null, null, MessageStatus.Sent, null, sentAt),
                ),
            ),
            input = TextFieldState(),
            actions = ConversationActions({}, {}, {}, {}, {}, {}, {}),
        )
    }
}
