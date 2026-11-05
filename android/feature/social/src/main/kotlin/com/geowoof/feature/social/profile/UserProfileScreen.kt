package com.geowoof.feature.social.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewFontScale
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.geowoof.core.designsystem.component.EmptyState
import com.geowoof.core.designsystem.icon.GeoWoofIcons
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.core.model.Pet
import com.geowoof.core.model.Profile
import com.geowoof.core.model.Relationship
import com.geowoof.core.model.RelationshipStatus
import com.geowoof.core.model.Walk
import com.geowoof.core.ui.ImageVariant
import com.geowoof.core.ui.RemoteAvatar
import com.geowoof.core.ui.WalkCard
import com.geowoof.core.ui.shortDescription
import com.geowoof.core.ui.userMessage
import com.geowoof.feature.social.R
import kotlinx.coroutines.flow.flowOf
import java.time.Instant

@Composable
internal fun UserProfileScreen(
    onBack: () -> Unit,
    onOpenWalk: (walkId: String) -> Unit,
    onVerifyEmail: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: UserProfileViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnBack by rememberUpdatedState(onBack)
    LaunchedEffect(uiState.isFinished) {
        if (uiState.isFinished) currentOnBack()
    }
    UserProfileContent(
        uiState = uiState,
        walks = viewModel.walks.collectAsLazyPagingItems(),
        reportDetails = viewModel.reportDetails,
        actions = UserProfileActions(
            onBack = onBack,
            onRefresh = viewModel::refresh,
            onSendRequest = viewModel::onSendRequest,
            onAcceptRequest = viewModel::onAcceptRequest,
            onRejectRequest = viewModel::onRejectRequest,
            onCancelRequest = viewModel::onCancelRequest,
            onShowDialog = viewModel::onShowDialog,
            onDismissDialog = viewModel::onDismissDialog,
            onConfirmRemoveFriend = viewModel::onConfirmRemoveFriend,
            onConfirmBlock = viewModel::onConfirmBlock,
            onSelectReportReason = viewModel::onSelectReportReason,
            onConfirmReport = viewModel::onConfirmReport,
            onVerifyEmail = {
                viewModel.onDismissDialog()
                onVerifyEmail()
            },
            onOpenWalk = onOpenWalk,
            onMessageShown = viewModel::onMessageShown,
        ),
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UserProfileContent(
    uiState: UserProfileUiState,
    walks: LazyPagingItems<Walk>,
    reportDetails: TextFieldState,
    actions: UserProfileActions,
    modifier: Modifier = Modifier,
) {
    val snackbars = remember { SnackbarHostState() }
    val reported = stringResource(R.string.feature_social_report_sent)
    val message = when {
        uiState.wasReported -> reported
        else -> uiState.actionError?.userMessage()
    }
    val currentOnMessageShown by rememberUpdatedState(actions.onMessageShown)
    LaunchedEffect(message) {
        if (message != null) {
            snackbars.showSnackbar(message)
            currentOnMessageShown()
        }
    }
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbars) },
        topBar = {
            TopAppBar(
                title = { Text(uiState.profile?.username?.let { "@$it" }.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(
                            painter = painterResource(GeoWoofIcons.ArrowBack),
                            contentDescription = stringResource(R.string.feature_social_back),
                        )
                    }
                },
                actions = {
                    if (uiState.canBeBlockedOrReported) ProfileMenu(onShowDialog = actions.onShowDialog)
                },
            )
        },
    ) { innerPadding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
        when {
            uiState.isUnavailable -> EmptyState(
                icon = GeoWoofIcons.Person,
                title = stringResource(R.string.feature_social_unavailable_title),
                message = stringResource(R.string.feature_social_unavailable_message),
                modifier = contentModifier,
            )

            uiState.profile == null && uiState.loadError != null -> Column(
                modifier = contentModifier.padding(GeoWoofTheme.spacing.large),
                verticalArrangement = Arrangement.spacedBy(GeoWoofTheme.spacing.medium, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(uiState.loadError.userMessage())
                OutlinedButton(onClick = actions.onRefresh) { Text(stringResource(R.string.feature_social_retry)) }
            }

            uiState.profile == null -> Box(contentModifier, contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            else -> ProfileList(uiState, uiState.profile, walks, actions, contentModifier)
        }
    }
    ProfileDialogs(uiState, reportDetails, actions)
}

private val UserProfileUiState.canBeBlockedOrReported: Boolean
    get() = profile != null &&
        !isUnavailable &&
        relationship != null &&
        relationship.status != RelationshipStatus.Self &&
        relationship.status != RelationshipStatus.Blocked

@Composable
private fun ProfileMenu(onShowDialog: (ProfileDialog) -> Unit, modifier: Modifier = Modifier) {
    var isOpen by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        IconButton(onClick = { isOpen = true }) {
            Icon(
                painter = painterResource(GeoWoofIcons.MoreVert),
                contentDescription = stringResource(R.string.feature_social_more_options),
            )
        }
        DropdownMenu(expanded = isOpen, onDismissRequest = { isOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.feature_social_report)) },
                leadingIcon = { Icon(painterResource(GeoWoofIcons.Flag), contentDescription = null) },
                onClick = {
                    isOpen = false
                    onShowDialog(ProfileDialog.Report)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.feature_social_block)) },
                leadingIcon = { Icon(painterResource(GeoWoofIcons.Block), contentDescription = null) },
                onClick = {
                    isOpen = false
                    onShowDialog(ProfileDialog.ConfirmBlock)
                },
            )
        }
    }
}

@Composable
private fun ProfileList(
    uiState: UserProfileUiState,
    profile: Profile,
    walks: LazyPagingItems<Walk>,
    actions: UserProfileActions,
    modifier: Modifier = Modifier,
) {
    val spacing = GeoWoofTheme.spacing
    val petNames = uiState.pets.associate { it.id to it.name }
    LazyColumn(modifier = modifier, contentPadding = PaddingValues(bottom = spacing.huge)) {
        item { ProfileHeader(profile) }
        uiState.relationship?.let { relationship ->
            item { RelationshipActions(relationship, uiState.isActing, actions) }
        }
        item { SectionTitle(stringResource(R.string.feature_social_pets)) }
        if (uiState.pets.isEmpty() && !uiState.isLoading) {
            item { EmptyLine(stringResource(R.string.feature_social_no_pets)) }
        }
        items(uiState.pets, key = Pet::id) { pet -> PetRow(pet) }
        item { SectionTitle(stringResource(R.string.feature_social_walks)) }
        if (walks.itemCount == 0 && walks.loadState.refresh !is LoadState.Loading) {
            item {
                EmptyLine(
                    stringResource(
                        if (walks.loadState.refresh is LoadState.Error) {
                            R.string.feature_social_walks_failed
                        } else {
                            R.string.feature_social_no_walks
                        },
                    ),
                )
            }
        }
        items(walks.itemCount, key = walks.itemKey(Walk::id)) { index ->
            walks[index]?.let { walk ->
                WalkCard(
                    walk = walk,
                    petNames = walk.petIds.mapNotNull { petNames[it] }.joinToString(),
                    onClick = { actions.onOpenWalk(walk.id) },
                )
            }
        }
    }
}

@Composable
private fun ProfileHeader(profile: Profile, modifier: Modifier = Modifier) {
    val spacing = GeoWoofTheme.spacing
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(spacing.large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.extraSmall),
    ) {
        RemoteAvatar(
            image = profile.avatar,
            fallbackIcon = GeoWoofIcons.Person,
            contentDescription = null,
            size = AvatarSize,
            variant = ImageVariant.Medium,
        )
        Text(
            text = profile.displayName,
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineSmall,
        )
        profile.city?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        profile.bio?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }
}

/** What can be done with this person now. The server decides, because the other person can change it too. */
@Composable
private fun RelationshipActions(
    relationship: Relationship,
    isActing: Boolean,
    actions: UserProfileActions,
    modifier: Modifier = Modifier,
) {
    val spacing = GeoWoofTheme.spacing
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.large, vertical = spacing.small),
        horizontalArrangement = Arrangement.spacedBy(spacing.small, Alignment.CenterHorizontally),
    ) {
        when (relationship.status) {
            RelationshipStatus.None -> Button(onClick = actions.onSendRequest, enabled = !isActing) {
                Icon(painterResource(GeoWoofIcons.PersonAdd), contentDescription = null)
                Text(
                    text = stringResource(R.string.feature_social_add_friend),
                    modifier = Modifier.padding(start = spacing.small),
                )
            }

            RelationshipStatus.RequestSent -> OutlinedButton(onClick = actions.onCancelRequest, enabled = !isActing) {
                Text(stringResource(R.string.feature_social_cancel_sent_request))
            }

            RelationshipStatus.RequestReceived -> {
                Button(onClick = actions.onAcceptRequest, enabled = !isActing) {
                    Text(stringResource(R.string.feature_social_accept))
                }
                OutlinedButton(onClick = actions.onRejectRequest, enabled = !isActing) {
                    Text(stringResource(R.string.feature_social_reject))
                }
            }

            RelationshipStatus.Friends -> FilledTonalButton(
                onClick = { actions.onShowDialog(ProfileDialog.ConfirmRemoveFriend) },
                enabled = !isActing,
            ) {
                Icon(painterResource(GeoWoofIcons.Check), contentDescription = null)
                Text(
                    text = stringResource(R.string.feature_social_is_friend),
                    modifier = Modifier.padding(start = spacing.small),
                )
            }

            RelationshipStatus.Self, RelationshipStatus.Blocked -> Unit
        }
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    val spacing = GeoWoofTheme.spacing
    Text(
        text = text,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.large, vertical = spacing.small)
            .semantics { heading() },
        style = MaterialTheme.typography.titleMedium,
    )
}

@Composable
private fun EmptyLine(text: String, modifier: Modifier = Modifier) {
    val spacing = GeoWoofTheme.spacing
    Text(
        text = text,
        modifier = modifier.padding(horizontal = spacing.large, vertical = spacing.small),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun PetRow(pet: Pet, modifier: Modifier = Modifier) {
    ListItem(
        modifier = modifier,
        headlineContent = { Text(pet.name) },
        supportingContent = { Text(pet.shortDescription()) },
        leadingContent = {
            RemoteAvatar(
                image = pet.avatar,
                fallbackIcon = GeoWoofIcons.Pets,
                contentDescription = null,
                size = PetAvatarSize,
            )
        },
    )
}

private val AvatarSize = 96.dp
private val PetAvatarSize = 40.dp

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
private fun UserProfilePreview() {
    val finished = LoadStates(LoadState.NotLoading(true), LoadState.NotLoading(true), LoadState.NotLoading(true))
    GeoWoofTheme(dynamicColor = false) {
        UserProfileContent(
            uiState = UserProfileUiState(
                isLoading = false,
                profile = PREVIEW_PROFILE,
                relationship = Relationship(RelationshipStatus.None),
            ),
            walks = flowOf(PagingData.empty<Walk>(finished)).collectAsLazyPagingItems(),
            reportDetails = TextFieldState(),
            actions = UserProfileActions(),
        )
    }
}
