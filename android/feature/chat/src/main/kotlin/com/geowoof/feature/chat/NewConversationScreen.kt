package com.geowoof.feature.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.geowoof.core.designsystem.component.EmptyState
import com.geowoof.core.designsystem.component.GeoWoofIllustrations
import com.geowoof.core.designsystem.component.SkeletonList
import com.geowoof.core.designsystem.component.SkeletonRow
import com.geowoof.core.designsystem.icon.GeoWoofIcons
import com.geowoof.core.model.Person
import com.geowoof.core.ui.RemoteAvatar

@Composable
internal fun NewConversationScreen(
    onBack: () -> Unit,
    onOpenChat: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NewConversationViewModel = hiltViewModel(),
) {
    NewConversationContent(
        friends = viewModel.friends.collectAsLazyPagingItems(),
        onBack = onBack,
        onOpenChat = onOpenChat,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NewConversationContent(
    friends: LazyPagingItems<Person>,
    onBack: () -> Unit,
    onOpenChat: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feature_chat_new_conversation)) },
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
    ) { innerPadding ->
        when {
            friends.itemCount > 0 -> LazyColumn(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                items(friends.itemCount, key = friends.itemKey { it.user.id }) { index ->
                    friends[index]?.let { friend ->
                        ListItem(
                            modifier = Modifier.clickable { onOpenChat(friend.user.id) },
                            leadingContent = {
                                RemoteAvatar(
                                    image = friend.user.avatar,
                                    fallbackIcon = GeoWoofIcons.Person,
                                    contentDescription = null,
                                    size = AvatarSize,
                                )
                            },
                            headlineContent = { Text(friend.user.displayName) },
                            supportingContent = {
                                Text(stringResource(R.string.feature_chat_username, friend.user.username))
                            },
                        )
                    }
                }
            }

            friends.loadState.refresh is LoadState.Loading ->
                SkeletonList(modifier = Modifier.padding(innerPadding)) { SkeletonRow() }

            else -> EmptyState(
                icon = GeoWoofIcons.Groups,
                title = stringResource(R.string.feature_chat_no_friends_title),
                message = stringResource(R.string.feature_chat_no_friends),
                modifier = Modifier.padding(innerPadding),
                illustration = GeoWoofIllustrations.EmptyFriends,
            )
        }
    }
}

private val AvatarSize = 48.dp
