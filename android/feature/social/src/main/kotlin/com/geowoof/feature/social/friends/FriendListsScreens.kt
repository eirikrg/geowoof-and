package com.geowoof.feature.social.friends

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewFontScale
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geowoof.core.designsystem.component.EmptyState
import com.geowoof.core.designsystem.icon.GeoWoofIcons
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.core.model.AppError
import com.geowoof.core.model.Friend
import com.geowoof.core.model.FriendRequest
import com.geowoof.core.model.RequestDirection
import com.geowoof.core.model.UserSummary
import com.geowoof.core.ui.UserRow
import com.geowoof.core.ui.userMessage
import com.geowoof.feature.social.R
import java.time.Instant

@Composable
internal fun FriendsScreen(
    onBack: () -> Unit,
    onOpenUser: (userId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FriendsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    FriendsContent(
        uiState = uiState,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onOpenUser = onOpenUser,
        onDismissError = viewModel::onErrorShown,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FriendsContent(
    uiState: FriendsUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpenUser: (userId: String) -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbars = errorSnackbar(uiState.error, onDismissError)
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbars) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feature_social_friends_title)) },
                navigationIcon = { BackButton(onBack) },
            )
        },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // A pull needs something that scrolls, also when the list is empty.
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (uiState.friends.isEmpty() && !uiState.isRefreshing) {
                    item {
                        EmptyState(
                            icon = GeoWoofIcons.Groups,
                            title = stringResource(R.string.feature_social_no_friends_title),
                            message = stringResource(R.string.feature_social_no_friends_message),
                            modifier = Modifier.fillParentMaxSize(),
                        )
                    }
                }
                items(uiState.friends, key = { it.user.id }) { friend ->
                    UserRow(friend.user, onClick = { onOpenUser(friend.user.id) })
                }
            }
        }
    }
}

@Composable
internal fun FriendRequestsScreen(
    onBack: () -> Unit,
    onOpenUser: (userId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FriendRequestsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    FriendRequestsContent(
        uiState = uiState,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onOpenUser = onOpenUser,
        onAccept = viewModel::onAccept,
        onReject = viewModel::onReject,
        onCancel = viewModel::onCancel,
        onDismissError = viewModel::onErrorShown,
        modifier = modifier,
    )
}

@Suppress("LongParameterList")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FriendRequestsContent(
    uiState: FriendRequestsUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpenUser: (userId: String) -> Unit,
    onAccept: (requestId: String) -> Unit,
    onReject: (requestId: String) -> Unit,
    onCancel: (requestId: String) -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbars = errorSnackbar(uiState.error, onDismissError)
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbars) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feature_social_requests_title)) },
                navigationIcon = { BackButton(onBack) },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(stringResource(R.string.feature_social_tab_received, uiState.received.size)) },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(stringResource(R.string.feature_social_tab_sent, uiState.sent.size)) },
                )
            }
            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                val requests = if (selectedTab == 0) uiState.received else uiState.sent
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    if (requests.isEmpty() && !uiState.isRefreshing) {
                        item {
                            EmptyState(
                                icon = GeoWoofIcons.GroupAdd,
                                title = stringResource(R.string.feature_social_no_requests_title),
                                message = stringResource(
                                    if (selectedTab == 0) {
                                        R.string.feature_social_no_received_message
                                    } else {
                                        R.string.feature_social_no_sent_message
                                    },
                                ),
                                modifier = Modifier.fillParentMaxSize(),
                            )
                        }
                    }
                    items(requests, key = FriendRequest::id) { request ->
                        UserRow(
                            user = request.user,
                            onClick = { onOpenUser(request.user.id) },
                            trailing = {
                                if (request.direction == RequestDirection.Received) {
                                    ReceivedActions(request, uiState.answeringId == null, onAccept, onReject)
                                } else {
                                    TextButton(
                                        onClick = { onCancel(request.id) },
                                        enabled = uiState.answeringId == null,
                                    ) {
                                        Text(stringResource(R.string.feature_social_cancel_request))
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReceivedActions(
    request: FriendRequest,
    isEnabled: Boolean,
    onAccept: (requestId: String) -> Unit,
    onReject: (requestId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(GeoWoofTheme.spacing.extraSmall)) {
        IconButton(onClick = { onReject(request.id) }, enabled = isEnabled) {
            Icon(
                painter = painterResource(GeoWoofIcons.Close),
                contentDescription = stringResource(R.string.feature_social_reject_request, request.user.displayName),
            )
        }
        IconButton(onClick = { onAccept(request.id) }, enabled = isEnabled) {
            Icon(
                painter = painterResource(GeoWoofIcons.Check),
                contentDescription = stringResource(R.string.feature_social_accept_request, request.user.displayName),
            )
        }
    }
}

@Composable
private fun BackButton(onBack: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onBack, modifier = modifier) {
        Icon(
            painter = painterResource(GeoWoofIcons.ArrowBack),
            contentDescription = stringResource(R.string.feature_social_back),
        )
    }
}

@Composable
private fun errorSnackbar(error: AppError?, onDismiss: () -> Unit): SnackbarHostState {
    val snackbars = remember { SnackbarHostState() }
    val message = error?.userMessage()
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(message) {
        if (message != null) {
            snackbars.showSnackbar(message)
            currentOnDismiss()
        }
    }
    return snackbars
}

private val PREVIEW_USER = UserSummary("1", "bea", "Bea", "Madrid", null)
private val PREVIEW_TIME = Instant.parse("2026-11-05T10:00:00Z")

@PreviewLightDark
@PreviewFontScale
@Composable
private fun FriendsPreview() {
    GeoWoofTheme(dynamicColor = false) {
        FriendsContent(
            uiState = FriendsUiState(friends = listOf(Friend(PREVIEW_USER, PREVIEW_TIME)), isRefreshing = false),
            onBack = {},
            onRefresh = {},
            onOpenUser = {},
            onDismissError = {},
        )
    }
}

@PreviewLightDark
@PreviewFontScale
@Composable
private fun FriendRequestsPreview() {
    GeoWoofTheme(dynamicColor = false) {
        FriendRequestsContent(
            uiState = FriendRequestsUiState(
                received = listOf(FriendRequest("1", PREVIEW_USER, RequestDirection.Received, PREVIEW_TIME)),
                isRefreshing = false,
            ),
            onBack = {},
            onRefresh = {},
            onOpenUser = {},
            onAccept = {},
            onReject = {},
            onCancel = {},
            onDismissError = {},
        )
    }
}
