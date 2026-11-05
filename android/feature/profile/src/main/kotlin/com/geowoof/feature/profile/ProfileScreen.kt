package com.geowoof.feature.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import com.geowoof.core.designsystem.component.EmptyState
import com.geowoof.core.designsystem.icon.GeoWoofIcons
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.core.model.AppError
import com.geowoof.core.model.Pet
import com.geowoof.core.model.Profile
import com.geowoof.core.ui.ImageVariant
import com.geowoof.core.ui.RemoteAvatar
import com.geowoof.core.ui.shortDescription
import com.geowoof.core.ui.userMessage

@Composable
fun ProfileScreen(
    onOpenSettings: () -> Unit,
    onEditProfile: () -> Unit,
    onOpenWalks: () -> Unit,
    onOpenPet: (petId: String) -> Unit,
    onAddPet: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ProfileContent(
        uiState = uiState,
        onRefresh = viewModel::refresh,
        onDismissError = viewModel::onErrorShown,
        onOpenSettings = onOpenSettings,
        onEditProfile = onEditProfile,
        onOpenWalks = onOpenWalks,
        onOpenPet = onOpenPet,
        onAddPet = onAddPet,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfileContent(
    uiState: ProfileUiState,
    onRefresh: () -> Unit,
    onDismissError: () -> Unit,
    onOpenSettings: () -> Unit,
    onEditProfile: () -> Unit,
    onOpenWalks: () -> Unit,
    onOpenPet: (petId: String) -> Unit,
    onAddPet: () -> Unit,
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
                title = { Text(stringResource(R.string.feature_profile_profile_title)) },
                actions = {
                    IconButton(onClick = onEditProfile) {
                        Icon(
                            painter = painterResource(GeoWoofIcons.Edit),
                            contentDescription = stringResource(R.string.feature_profile_edit),
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            painter = painterResource(GeoWoofIcons.Settings),
                            contentDescription = stringResource(R.string.feature_profile_settings),
                        )
                    }
                },
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
            ProfileList(uiState = uiState, onOpenWalks = onOpenWalks, onOpenPet = onOpenPet, onAddPet = onAddPet)
        }
    }
}

@Composable
private fun ProfileList(
    uiState: ProfileUiState,
    onOpenWalks: () -> Unit,
    onOpenPet: (petId: String) -> Unit,
    onAddPet: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = GeoWoofTheme.spacing
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = spacing.huge),
    ) {
        uiState.profile?.let { profile ->
            item { ProfileHeader(profile) }
        }
        item {
            ListItem(
                headlineContent = { Text(stringResource(R.string.feature_profile_my_walks)) },
                leadingContent = { Icon(painterResource(GeoWoofIcons.DirectionsWalk), contentDescription = null) },
                trailingContent = { Icon(painterResource(GeoWoofIcons.ChevronRight), contentDescription = null) },
                modifier = Modifier.clickable(onClick = onOpenWalks),
            )
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.large, vertical = spacing.small),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.feature_profile_my_pets),
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.titleMedium,
                )
                FilledTonalButton(onClick = onAddPet) {
                    Text(stringResource(R.string.feature_profile_add_pet))
                }
            }
        }
        if (uiState.pets.isEmpty() && !uiState.isRefreshing) {
            item {
                EmptyState(
                    icon = GeoWoofIcons.Pets,
                    title = stringResource(R.string.feature_profile_no_pets_title),
                    message = stringResource(R.string.feature_profile_no_pets_message),
                )
            }
        }
        items(uiState.pets, key = Pet::id) { pet -> PetRow(pet = pet, onClick = { onOpenPet(pet.id) }) }
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
            contentDescription = stringResource(R.string.feature_profile_photo),
            size = AvatarSize,
            variant = ImageVariant.Medium,
        )
        Text(profile.displayName, style = MaterialTheme.typography.headlineSmall)
        profile.username?.let {
            Text(
                text = stringResource(R.string.feature_profile_username, it),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        profile.city?.let { city ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(GeoWoofIcons.LocationOn),
                    contentDescription = null,
                    modifier = Modifier.size(CityIconSize),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = city,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        profile.bio?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun PetRow(pet: Pet, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ListItem(
        headlineContent = { Text(pet.name) },
        supportingContent = { Text(pet.shortDescription()) },
        leadingContent = {
            RemoteAvatar(image = pet.avatar, fallbackIcon = GeoWoofIcons.Pets, contentDescription = null)
        },
        trailingContent = { Icon(painterResource(GeoWoofIcons.ChevronRight), contentDescription = null) },
        modifier = modifier.clickable(onClick = onClick),
    )
}

private val AvatarSize = 96.dp
private val CityIconSize = 16.dp

@PreviewLightDark
@PreviewFontScale
@Composable
private fun ProfilePreview() {
    GeoWoofTheme(dynamicColor = false) {
        ProfileContent(
            uiState = ProfileUiState(error = null as AppError?),
            onRefresh = {},
            onDismissError = {},
            onOpenSettings = {},
            onEditProfile = {},
            onOpenWalks = {},
            onOpenPet = {},
            onAddPet = {},
        )
    }
}
