package com.geowoof.feature.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geowoof.core.designsystem.component.EmptyState
import com.geowoof.core.designsystem.component.GeoWoofIllustrations
import com.geowoof.core.designsystem.component.SkeletonList
import com.geowoof.core.designsystem.icon.GeoWoofIcons
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.core.model.Conversation
import com.geowoof.core.model.MessagePreview
import com.geowoof.core.model.UserSummary
import com.geowoof.core.ui.RemoteAvatar
import com.geowoof.core.ui.RequestNotificationPermission
import com.geowoof.core.ui.userMessage
import java.time.Instant

@Composable
internal fun ConversationsScreen(
    onBack: () -> Unit,
    onOpenConversation: (String) -> Unit,
    onNewConversation: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ConversationsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    RequestNotificationPermission()
    LifecycleResumeEffect(viewModel) {
        viewModel.onResume()
        onPauseOrDispose {}
    }
    ConversationsContent(
        uiState = uiState,
        onBack = onBack,
        onOpenConversation = onOpenConversation,
        onNewConversation = onNewConversation,
        onDismissError = viewModel::onErrorShown,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConversationsContent(
    uiState: ConversationsUiState,
    onBack: () -> Unit,
    onOpenConversation: (String) -> Unit,
    onNewConversation: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbars = remember { SnackbarHostState() }
    val errorMessage = uiState.error?.userMessage()
    val currentOnDismissError by rememberUpdatedState(onDismissError)
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            snackbars.showSnackbar(errorMessage)
            currentOnDismissError()
        }
    }
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbars) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feature_chat_conversations_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(GeoWoofIcons.ArrowBack),
                            contentDescription = stringResource(R.string.feature_chat_back),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNewConversation,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(
                    painterResource(GeoWoofIcons.Add),
                    contentDescription = stringResource(R.string.feature_chat_new_conversation),
                )
            }
        },
    ) { innerPadding ->
        when {
            uiState.conversations.isNotEmpty() -> LazyColumn(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                items(uiState.conversations, key = Conversation::id) { conversation ->
                    ConversationRow(conversation, uiState.myUserId, onClick = { onOpenConversation(conversation.id) })
                }
            }

            uiState.isLoading -> SkeletonList(Modifier.padding(innerPadding))

            else -> EmptyState(
                icon = GeoWoofIcons.Forum,
                title = stringResource(R.string.feature_chat_no_conversations_title),
                message = stringResource(R.string.feature_chat_no_conversations),
                illustration = GeoWoofIllustrations.EmptyMessages,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

@Composable
private fun ConversationRow(
    conversation: Conversation,
    myUserId: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasUnread = conversation.unreadCount > 0
    ListItem(
        modifier = modifier.clickable(onClick = onClick),
        leadingContent = {
            RemoteAvatar(
                image = conversation.otherUser.avatar,
                fallbackIcon = GeoWoofIcons.Person,
                contentDescription = null,
                size = AvatarSize,
            )
        },
        headlineContent = {
            Text(text = conversation.otherUser.displayName, style = MaterialTheme.typography.titleSmall)
        },
        supportingContent = {
            Text(
                text = previewText(conversation.lastMessage, myUserId),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                color = if (hasUnread) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                fontWeight = if (hasUnread) FontWeight.Bold else FontWeight.Medium,
            )
        },
        trailingContent = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(GeoWoofTheme.spacing.small),
            ) {
                Text(
                    text = formatChatTime(conversation.updatedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (hasUnread) {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary,
                    ) {
                        Text(conversation.unreadCount.toString())
                    }
                }
            }
        },
    )
}

@Composable
private fun previewText(preview: MessagePreview?, myUserId: String?): String {
    if (preview == null) return ""
    val text = preview.body ?: stringResource(R.string.feature_chat_photo)
    return if (preview.senderId == myUserId) stringResource(R.string.feature_chat_you_said, text) else text
}

private val AvatarSize = 52.dp

@PreviewLightDark
@Composable
private fun ConversationsPreview() {
    GeoWoofTheme {
        ConversationsContent(
            uiState = ConversationsUiState(
                isLoading = false,
                myUserId = "me",
                conversations = listOf(
                    Conversation(
                        id = "1",
                        otherUser = UserSummary("bea-id", "bea", "Bea", null, null),
                        canSend = true,
                        unreadCount = 2,
                        lastMessage = MessagePreview("bea-id", "¿Salimos a las 7?", false),
                        otherLastReadMessageId = null,
                        updatedAt = Instant.parse("2026-11-05T10:00:00Z"),
                    ),
                ),
            ),
            onBack = {},
            onOpenConversation = {},
            onNewConversation = {},
            onDismissError = {},
        )
    }
}
