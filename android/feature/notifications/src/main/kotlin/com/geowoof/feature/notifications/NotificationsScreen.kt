package com.geowoof.feature.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.geowoof.core.data.repository.NotificationsRepository
import com.geowoof.core.designsystem.icon.GeoWoofIcons
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.core.model.AppNotification
import com.geowoof.core.model.NotificationType
import com.geowoof.core.ui.RemoteAvatar
import com.geowoof.core.ui.RequestNotificationPermission
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Opening the list means having seen what is in it. Each row still shows whether it was new when it opened. */
@HiltViewModel
class NotificationsViewModel @Inject constructor(private val notifications: NotificationsRepository) : ViewModel() {
    val items: Flow<PagingData<AppNotification>> = notifications.notifications().cachedIn(viewModelScope)

    fun onOpened() {
        viewModelScope.launch { notifications.markAllRead() }
    }
}

/** Where a notification leads. The screens are other features: the app provides how to reach them. */
class NotificationsActions(
    val onBack: () -> Unit,
    val onOpenPost: (String) -> Unit,
    val onOpenUser: (String) -> Unit,
    val onOpenRequests: () -> Unit,
)

@Composable
internal fun NotificationsScreen(
    actions: NotificationsActions,
    modifier: Modifier = Modifier,
    viewModel: NotificationsViewModel = hiltViewModel(),
) {
    RequestNotificationPermission()
    val items = viewModel.items.collectAsLazyPagingItems()
    LaunchedEffect(viewModel) { viewModel.onOpened() }
    NotificationsContent(items = items, actions = actions, modifier = modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NotificationsContent(
    items: LazyPagingItems<AppNotification>,
    actions: NotificationsActions,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feature_notifications_title)) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(
                            painter = painterResource(GeoWoofIcons.ArrowBack),
                            contentDescription = stringResource(R.string.feature_notifications_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        val refresh = items.loadState.refresh
        when {
            items.itemCount > 0 -> LazyColumn(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                items(items.itemCount, key = items.itemKey { it.id }) { index ->
                    items[index]?.let { notification ->
                        NotificationRow(notification, onClick = { notification.open(actions) })
                    }
                }
            }

            refresh is LoadState.Loading -> Centered(Modifier.padding(innerPadding)) { CircularProgressIndicator() }

            refresh is LoadState.Error -> Centered(Modifier.padding(innerPadding)) {
                Button(onClick = items::retry) { Text(stringResource(R.string.feature_notifications_retry)) }
            }

            else -> Centered(Modifier.padding(innerPadding)) {
                Text(stringResource(R.string.feature_notifications_empty), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun Centered(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier.fillMaxSize().padding(GeoWoofTheme.spacing.large),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun NotificationRow(notification: AppNotification, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ListItem(
        modifier = modifier.clickable(onClick = onClick),
        leadingContent = {
            RemoteAvatar(
                image = notification.actor.avatar,
                fallbackIcon = GeoWoofIcons.Person,
                contentDescription = null,
                size = AvatarSize,
            )
        },
        headlineContent = { Text(stringResource(notification.type.textRes(), notification.actor.displayName)) },
        trailingContent = {
            if (!notification.isRead) {
                val unread = stringResource(R.string.feature_notifications_unread)
                Box(
                    modifier = Modifier
                        .size(UnreadDotSize)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .semantics { contentDescription = unread },
                )
            }
        },
    )
}

private fun AppNotification.open(actions: NotificationsActions) {
    when (type) {
        NotificationType.PostLiked,
        NotificationType.CommentAdded,
        NotificationType.PostReposted,
        NotificationType.UserMentioned,
        ->
            postId?.let(actions.onOpenPost) ?: actions.onOpenUser(actor.id)

        NotificationType.FollowRequested -> actions.onOpenRequests()

        NotificationType.UserFollowed, NotificationType.FollowRequestAccepted, NotificationType.FriendshipStarted ->
            actions.onOpenUser(actor.id)
    }
}

private fun NotificationType.textRes(): Int = when (this) {
    NotificationType.UserFollowed -> R.string.feature_notifications_user_followed
    NotificationType.FollowRequested -> R.string.feature_notifications_follow_requested
    NotificationType.FollowRequestAccepted -> R.string.feature_notifications_follow_request_accepted
    NotificationType.FriendshipStarted -> R.string.feature_notifications_friendship_started
    NotificationType.PostLiked -> R.string.feature_notifications_post_liked
    NotificationType.CommentAdded -> R.string.feature_notifications_comment_added
    NotificationType.PostReposted -> R.string.feature_notifications_post_reposted
    NotificationType.UserMentioned -> R.string.feature_notifications_user_mentioned
}

private val AvatarSize = 48.dp
private val UnreadDotSize = 10.dp
