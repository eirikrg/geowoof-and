package com.geowoof.feature.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import com.geowoof.core.designsystem.component.EmptyState
import com.geowoof.core.designsystem.icon.GeoWoofIcons
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.core.model.Pet
import com.geowoof.core.model.Profile
import com.geowoof.core.model.Walk
import com.geowoof.core.ui.RemoteAvatar
import com.geowoof.core.ui.WalkCard
import com.geowoof.core.ui.WalkTotalsGrid
import com.geowoof.core.ui.shortDescription

@Composable
internal fun ProfileList(
    uiState: ProfileUiState,
    profile: Profile,
    walks: LazyPagingItems<Walk>,
    actions: ProfileActions,
    modifier: Modifier = Modifier,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(TAB_WALKS) }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = GeoWoofTheme.spacing.huge),
    ) {
        item { ProfileHeader(uiState, profile) }
        item { ProfileCounters(uiState.counts, uiState.canOpenLists, actions.onOpenPeople) }
        uiState.relationship?.let { relationship ->
            item { RelationshipButton(relationship, uiState.isActing, actions) }
            if (relationship.incomingRequestId != null) {
                item {
                    IncomingRequest(
                        name = profile.displayName,
                        isActing = uiState.isActing,
                        onAccept = actions.onAcceptRequest,
                        onReject = actions.onRejectRequest,
                    )
                }
            }
        }
        if (uiState.isOwn && uiState.receivedRequests + uiState.sentRequests > 0) {
            item { RequestsRow(uiState.receivedRequests, actions.onOpenRequests) }
        }
        if (uiState.isRestricted) {
            item {
                EmptyState(
                    icon = GeoWoofIcons.Lock,
                    title = stringResource(R.string.feature_profile_private_title),
                    message = stringResource(R.string.feature_profile_private_message),
                )
            }
        } else {
            stickyHeader {
                PrimaryTabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == TAB_WALKS,
                        onClick = { selectedTab = TAB_WALKS },
                        text = { Text(stringResource(R.string.feature_profile_tab_walks)) },
                    )
                    Tab(
                        selected = selectedTab == TAB_PETS,
                        onClick = { selectedTab = TAB_PETS },
                        text = { Text(stringResource(R.string.feature_profile_tab_pets)) },
                    )
                }
            }
            if (selectedTab == TAB_WALKS) {
                walksTab(uiState, walks, actions)
            } else {
                petsTab(uiState, actions)
            }
        }
    }
}

private fun LazyListScope.walksTab(uiState: ProfileUiState, walks: LazyPagingItems<Walk>, actions: ProfileActions) {
    uiState.totals?.takeIf { it.walkCount > 0 }?.let { totals ->
        item {
            WalkTotalsGrid(
                totals = totals,
                modifier = Modifier.padding(GeoWoofTheme.spacing.medium),
            )
        }
    }
    if (walks.itemCount == 0 && walks.loadState.refresh !is LoadState.Loading) {
        item {
            EmptyLine(
                stringResource(
                    when {
                        walks.loadState.refresh is LoadState.Error -> R.string.feature_profile_walks_failed
                        uiState.isOwn -> R.string.feature_profile_no_own_walks
                        else -> R.string.feature_profile_no_walks
                    },
                ),
            )
        }
    }
    val petNames = uiState.pets.associate { it.id to it.name }
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

private fun LazyListScope.petsTab(uiState: ProfileUiState, actions: ProfileActions) {
    actions.onAddPet?.let { onAddPet ->
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = GeoWoofTheme.spacing.large, vertical = GeoWoofTheme.spacing.small),
                horizontalArrangement = Arrangement.End,
            ) {
                FilledTonalButton(onClick = onAddPet) { Text(stringResource(R.string.feature_profile_add_pet)) }
            }
        }
    }
    if (uiState.pets.isEmpty() && !uiState.isLoading) {
        item {
            if (uiState.isOwn) {
                EmptyState(
                    icon = GeoWoofIcons.Pets,
                    title = stringResource(R.string.feature_profile_no_pets_title),
                    message = stringResource(R.string.feature_profile_no_pets_message),
                )
            } else {
                EmptyLine(stringResource(R.string.feature_profile_no_pets_of_person))
            }
        }
    }
    items(uiState.pets, key = Pet::id) { pet ->
        PetRow(pet = pet, onClick = actions.onOpenPet?.let { onOpenPet -> { onOpenPet(pet.id) } })
    }
}

@Composable
private fun EmptyLine(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.padding(GeoWoofTheme.spacing.large),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Only your own pets open: those of other people have no screen yet. */
@Composable
private fun PetRow(pet: Pet, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    ListItem(
        headlineContent = { Text(pet.name) },
        supportingContent = { Text(pet.shortDescription()) },
        leadingContent = {
            RemoteAvatar(image = pet.avatar, fallbackIcon = GeoWoofIcons.Pets, contentDescription = null)
        },
        trailingContent = onClick?.let {
            { Icon(painterResource(GeoWoofIcons.ChevronRight), contentDescription = null) }
        },
        modifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier,
    )
}

private const val TAB_WALKS = 0
private const val TAB_PETS = 1
