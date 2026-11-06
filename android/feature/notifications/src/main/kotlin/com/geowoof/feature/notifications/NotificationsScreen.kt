package com.geowoof.feature.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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
import com.geowoof.core.data.repository.RemoteLoadException
import com.geowoof.core.designsystem.component.EmptyState
import com.geowoof.core.designsystem.component.ErrorState
import com.geowoof.core.designsystem.component.GeoWoofIllustrations
import com.geowoof.core.designsystem.component.IconBadge
import com.geowoof.core.designsystem.component.SkeletonList
import com.geowoof.core.designsystem.icon.GeoWoofIcons
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.core.model.AppNotification
import com.geowoof.core.model.NotificationType
import com.geowoof.core.ui.RemoteAvatar
import com.geowoof.core.ui.RequestNotificationPermission
import com.geowoof.core.ui.relativeToNow
import com.geowoof.core.ui.userMessage
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

            refresh is LoadState.Loading -> SkeletonList(Modifier.padding(innerPadding))

            refresh is LoadState.Error -> ErrorState(
                title = stringResource(R.string.feature_notifications_error_title),
                message = (refresh.error as? RemoteLoadException)?.error?.userMessage().orEmpty(),
                retryLabel = stringResource(R.string.feature_notifications_retry),
                onRetry = items::retry,
                modifier = Modifier.padding(innerPadding),
            )

            else -> EmptyState(
                icon = GeoWoofIcons.Notifications,
                title = stringResource(R.string.feature_notifications_empty_title),
                message = stringResource(R.string.feature_notifications_empty),
                illustration = GeoWoofIllustrations.EmptyNotifications,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

@Composable
private fun NotificationRow(notification: AppNotification, onClick: () -> Unit, modifier: Modifier = Modifier) {
    // What is new stands out, also for someone who cannot tell the dot apart.
    val colors = MaterialTheme.colorScheme
    ListItem(
        modifier = modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(
            containerColor = if (notification.isRead) {
                Color.Transparent
            } else {
                colors.secondaryContainer.copy(
                    alpha = NEW_ALPHA,
                )
            },
        ),
        leadingContent = {
            Box {
                RemoteAvatar(
                    image = notification.actor.avatar,
                    fallbackIcon = GeoWoofIcons.Person,
                    contentDescription = null,
                    size = AvatarSize,
                )
                IconBadge(
                    icon = notification.type.iconRes(),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = BadgeOffset, y = BadgeOffset),
                    size = BadgeSize,
                    iconSize = BadgeIconSize,
                    containerColor = colors.tertiary,
                    contentColor = colors.onTertiary,
                )
            }
        },
        headlineContent = {
            Text(
                text = stringResource(notification.type.textRes(), notification.actor.displayName),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (notification.isRead) FontWeight.Medium else FontWeight.Bold,
            )
        },
        supportingContent = {
            Text(notification.createdAt.relativeToNow(), style = MaterialTheme.typography.bodySmall)
        },
        trailingContent = {
            if (!notification.isRead) {
                val unread = stringResource(R.string.feature_notifications_unread)
                Box(
                    modifier = Modifier
                        .size(UnreadDotSize)
                        .background(colors.tertiary, CircleShape)
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

private fun NotificationType.iconRes(): Int = when (this) {
    NotificationType.UserFollowed, NotificationType.FollowRequested, NotificationType.FollowRequestAccepted ->
        GeoWoofIcons.PersonAdd

    NotificationType.FriendshipStarted -> GeoWoofIcons.Groups

    NotificationType.PostLiked -> GeoWoofIcons.FavoriteFilled

    NotificationType.CommentAdded -> GeoWoofIcons.ChatBubble

    NotificationType.PostReposted -> GeoWoofIcons.Repeat

    NotificationType.UserMentioned -> GeoWoofIcons.AlternateEmail
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

private val AvatarSize = 52.dp
private val BadgeSize = 22.dp
private val BadgeIconSize = 13.dp
private val BadgeOffset = 3.dp
private const val NEW_ALPHA = 0.45f
private val UnreadDotSize = 10.dp
