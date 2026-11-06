package com.geowoof.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewFontScale
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.geowoof.core.designsystem.component.EmptyState
import com.geowoof.core.designsystem.component.ErrorState
import com.geowoof.core.designsystem.icon.GeoWoofIcons
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.core.model.PeopleListKind
import com.geowoof.core.model.Profile
import com.geowoof.core.model.ProfileCounts
import com.geowoof.core.model.Relationship
import com.geowoof.core.model.RelationshipStatus
import com.geowoof.core.model.TimelineItem
import com.geowoof.core.model.Walk
import com.geowoof.core.ui.userMessage
import kotlinx.coroutines.flow.flowOf
import java.time.Instant

/** Your own profile, one of the destinations of the app. */
@Composable
fun ProfileScreen(
    onOpenSettings: () -> Unit,
    onEditProfile: () -> Unit,
    onOpenWalk: (walkId: String) -> Unit,
    onOpenPet: (petId: String) -> Unit,
    onAddPet: () -> Unit,
    onOpenPeople: (userId: String, kind: PeopleListKind) -> Unit,
    onOpenRequests: () -> Unit,
    onOpenPost: (postId: String) -> Unit,
    onCreatePost: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val posts = viewModel.posts.collectAsLazyPagingItems()
    val saved = viewModel.saved.collectAsLazyPagingItems()
    // Back from creating a post, or from saving or deleting one.
    LifecycleResumeEffect(viewModel, posts, saved) {
        viewModel.onResume()
        posts.refresh()
        saved.refresh()
        onPauseOrDispose {}
    }
    ProfileContent(
        uiState = uiState,
        lists = ProfileLists(posts, viewModel.walks.collectAsLazyPagingItems(), saved),
        reportDetails = remember { TextFieldState() },
        actions = ProfileActions(
            onRefresh = viewModel::refresh,
            onOpenSettings = onOpenSettings,
            onEditProfile = onEditProfile,
            onOpenPeople = { kind -> uiState.profile?.let { onOpenPeople(it.id, kind) } },
            onOpenRequests = onOpenRequests,
            onOpenWalk = onOpenWalk,
            onOpenPet = onOpenPet,
            onAddPet = onAddPet,
            onOpenPost = onOpenPost,
            onCreatePost = onCreatePost,
            onMessageShown = viewModel::onErrorShown,
        ),
        modifier = modifier,
    )
}

/** The profile of someone else. It can also be yours, if you reach it from the list of a friend. */
@Composable
internal fun UserProfileScreen(
    onBack: () -> Unit,
    onEditProfile: () -> Unit,
    onOpenWalk: (walkId: String) -> Unit,
    onOpenPeople: (userId: String, kind: PeopleListKind) -> Unit,
    onOpenPost: (postId: String) -> Unit,
    onVerifyEmail: () -> Unit,
    onSendMessage: (userId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: UserProfileViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val posts = viewModel.posts.collectAsLazyPagingItems()
    val currentOnBack by rememberUpdatedState(onBack)
    LaunchedEffect(uiState.isFinished) {
        if (uiState.isFinished) currentOnBack()
    }
    LifecycleResumeEffect(viewModel, posts) {
        viewModel.onResume()
        posts.refresh()
        onPauseOrDispose {}
    }
    ProfileContent(
        uiState = uiState,
        lists = ProfileLists(posts, viewModel.walks.collectAsLazyPagingItems(), saved = null),
        reportDetails = viewModel.reportDetails,
        actions = ProfileActions(
            onBack = onBack,
            onRefresh = viewModel::refresh,
            onEditProfile = onEditProfile,
            onOpenPeople = { kind -> uiState.profile?.let { onOpenPeople(it.id, kind) } },
            onOpenWalk = onOpenWalk,
            onOpenPost = onOpenPost,
            onFollow = viewModel::onFollow,
            onUnfollow = viewModel::onUnfollow,
            onSendMessage = { uiState.profile?.let { onSendMessage(it.id) } },
            onAcceptRequest = viewModel::onAcceptRequest,
            onRejectRequest = viewModel::onRejectRequest,
            onShowDialog = viewModel::onShowDialog,
            onDismissDialog = viewModel::onDismissDialog,
            onConfirmFollowBack = viewModel::onConfirmFollowBack,
            onConfirmUnfollow = viewModel::onConfirmUnfollow,
            onConfirmRemoveFollower = viewModel::onConfirmRemoveFollower,
            onConfirmBlock = viewModel::onConfirmBlock,
            onSelectReportReason = viewModel::onSelectReportReason,
            onConfirmReport = viewModel::onConfirmReport,
            onVerifyEmail = {
                viewModel.onDismissDialog()
                onVerifyEmail()
            },
            onMessageShown = viewModel::onMessageShown,
        ),
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfileContent(
    uiState: ProfileUiState,
    lists: ProfileLists,
    reportDetails: TextFieldState,
    actions: ProfileActions,
    modifier: Modifier = Modifier,
) {
    val snackbars = rememberProfileMessages(uiState, actions.onMessageShown)
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbars) },
        topBar = { ProfileTopBar(uiState, actions) },
    ) { innerPadding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
        val profile = uiState.profile
        when {
            uiState.isUnavailable -> EmptyState(
                icon = GeoWoofIcons.Person,
                title = stringResource(R.string.feature_profile_unavailable_title),
                message = stringResource(R.string.feature_profile_unavailable_message),
                modifier = contentModifier,
            )

            profile == null && uiState.loadError != null -> ErrorState(
                title = stringResource(R.string.feature_profile_error_title),
                message = uiState.loadError.userMessage(),
                retryLabel = stringResource(R.string.feature_profile_retry),
                onRetry = actions.onRefresh,
                modifier = contentModifier,
            )

            profile == null -> Box(contentModifier, contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            else -> PullToRefreshBox(
                isRefreshing = uiState.isLoading,
                onRefresh = actions.onRefresh,
                modifier = contentModifier,
            ) {
                ProfileList(uiState, profile, lists, actions)
            }
        }
    }
    ProfileDialogs(uiState, reportDetails, actions)
}

@Composable
private fun rememberProfileMessages(uiState: ProfileUiState, onDismiss: () -> Unit): SnackbarHostState {
    val snackbars = remember { SnackbarHostState() }
    val reported = stringResource(R.string.feature_profile_report_sent)
    // With something on screen, a failed refresh is only a message. Without it, it fills the screen with a retry.
    val loadMessage = uiState.loadError?.takeIf { uiState.profile != null }?.userMessage()
    val message = when {
        uiState.wasReported -> reported
        uiState.actionError != null -> uiState.actionError.userMessage()
        else -> loadMessage
    }
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(message) {
        if (message != null) {
            snackbars.showSnackbar(message)
            currentOnDismiss()
        }
    }
    return snackbars
}

private val PREVIEW_PROFILE = Profile(
    id = "1",
    username = "bea",
    displayName = "Bea",
    bio = "Paseo por el Retiro",
    city = "Madrid",
    avatar = null,
    createdAt = Instant.parse("2026-11-05T10:00:00Z"),
)

@PreviewLightDark
@PreviewFontScale
@Composable
private fun ProfilePreview() {
    val finished = LoadStates(LoadState.NotLoading(true), LoadState.NotLoading(true), LoadState.NotLoading(true))
    GeoWoofTheme(dynamicColor = false) {
        ProfileContent(
            uiState = ProfileUiState(
                isLoading = false,
                profile = PREVIEW_PROFILE,
                relationship = Relationship(RelationshipStatus.None, followsYou = true),
                counts = ProfileCounts(followers = 12, following = 8, friends = 5),
            ),
            lists = ProfileLists(
                posts = flowOf(PagingData.empty<TimelineItem>(finished)).collectAsLazyPagingItems(),
                walks = flowOf(PagingData.empty<Walk>(finished)).collectAsLazyPagingItems(),
                saved = null,
            ),
            reportDetails = TextFieldState(),
            actions = ProfileActions(),
        )
    }
}

@PreviewLightDark
@Composable
private fun RestrictedProfilePreview() {
    val finished = LoadStates(LoadState.NotLoading(true), LoadState.NotLoading(true), LoadState.NotLoading(true))
    GeoWoofTheme(dynamicColor = false) {
        ProfileContent(
            uiState = ProfileUiState(
                isLoading = false,
                profile = PREVIEW_PROFILE.copy(displayName = "bea", city = null, isPrivate = true),
                relationship = Relationship(RelationshipStatus.Requested),
                counts = ProfileCounts(followers = 12, following = 8, friends = 5),
            ),
            lists = ProfileLists(
                posts = flowOf(PagingData.empty<TimelineItem>(finished)).collectAsLazyPagingItems(),
                walks = flowOf(PagingData.empty<Walk>(finished)).collectAsLazyPagingItems(),
                saved = null,
            ),
            reportDetails = TextFieldState(),
            actions = ProfileActions(),
        )
    }
}
