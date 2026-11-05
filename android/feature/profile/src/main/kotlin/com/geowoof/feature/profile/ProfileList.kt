package com.geowoof.feature.profile

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import com.geowoof.core.designsystem.component.EmptyState
import com.geowoof.core.designsystem.icon.GeoWoofIcons
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.core.model.Pet
import com.geowoof.core.model.Post
import com.geowoof.core.model.Profile
import com.geowoof.core.model.TimelineItem
import com.geowoof.core.model.Walk
import com.geowoof.core.ui.PostGridItem
import com.geowoof.core.ui.RemoteAvatar
import com.geowoof.core.ui.WalkCard
import com.geowoof.core.ui.WalkTotalsGrid
import com.geowoof.core.ui.shortDescription

/** The tabs of a profile. Only you see [Saved], in your own. */
internal enum class ProfileTab(@param:StringRes val label: Int) {
    Photos(R.string.feature_profile_tab_photos),
    Walks(R.string.feature_profile_tab_walks),
    Pets(R.string.feature_profile_tab_pets),
    Saved(R.string.feature_profile_tab_saved),
}

/** The lists of a profile, which come page by page. */
internal class ProfileLists(
    val posts: LazyPagingItems<TimelineItem>,
    val walks: LazyPagingItems<Walk>,
    /** Only in your own profile. */
    val saved: LazyPagingItems<Post>?,
)

@Composable
internal fun ProfileList(
    uiState: ProfileUiState,
    profile: Profile,
    lists: ProfileLists,
    actions: ProfileActions,
    modifier: Modifier = Modifier,
) {
    var selectedTab by rememberSaveable { mutableStateOf(ProfileTab.Photos) }
    val tabs = ProfileTab.entries.filter { it != ProfileTab.Saved || lists.saved != null }
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
                PrimaryScrollableTabRow(selectedTabIndex = tabs.indexOf(selectedTab), edgePadding = 0.dp) {
                    tabs.forEach { tab ->
                        Tab(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            text = { Text(stringResource(tab.label)) },
                        )
                    }
                }
            }
            when (selectedTab) {
                ProfileTab.Photos -> photosTab(uiState, lists.posts, actions)
                ProfileTab.Walks -> walksTab(uiState, lists.walks, actions)
                ProfileTab.Pets -> petsTab(uiState, actions)
                ProfileTab.Saved -> lists.saved?.let { savedTab(it, actions) }
            }
        }
    }
}

/** A grid inside the list of the profile, which already scrolls: each row is an element of that list. */
private fun LazyListScope.gridRows(count: Int, cell: @Composable (index: Int) -> Unit) {
    items((count + GRID_COLUMNS - 1) / GRID_COLUMNS) { row ->
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(GridSpacing)) {
            repeat(GRID_COLUMNS) { column ->
                val index = row * GRID_COLUMNS + column
                Box(modifier = Modifier.weight(1f).padding(bottom = GridSpacing)) {
                    if (index < count) cell(index)
                }
            }
        }
    }
}

private fun LazyListScope.photosTab(
    uiState: ProfileUiState,
    posts: LazyPagingItems<TimelineItem>,
    actions: ProfileActions,
) {
    actions.onCreatePost?.let { onCreatePost ->
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = GeoWoofTheme.spacing.large, vertical = GeoWoofTheme.spacing.small),
                horizontalArrangement = Arrangement.End,
            ) {
                FilledTonalButton(onClick = onCreatePost) { Text(stringResource(R.string.feature_profile_add_photo)) }
            }
        }
    }
    if (posts.itemCount == 0 && posts.loadState.refresh !is LoadState.Loading) {
        item {
            EmptyLine(
                stringResource(
                    when {
                        posts.loadState.refresh is LoadState.Error -> R.string.feature_profile_photos_failed
                        uiState.isOwn -> R.string.feature_profile_no_own_photos
                        else -> R.string.feature_profile_no_photos
                    },
                ),
            )
        }
    }
    gridRows(posts.itemCount) { index ->
        posts[index]?.let { item ->
            PostGridItem(
                image = item.post.media.firstOrNull(),
                isRepost = item.repostedBy != null,
                onClick = { actions.onOpenPost(item.post.id) },
            )
        }
    }
}

private fun LazyListScope.savedTab(saved: LazyPagingItems<Post>, actions: ProfileActions) {
    if (saved.itemCount == 0 && saved.loadState.refresh !is LoadState.Loading) {
        item {
            EmptyLine(
                stringResource(
                    if (saved.loadState.refresh is LoadState.Error) {
                        R.string.feature_profile_saved_failed
                    } else {
                        R.string.feature_profile_no_saved
                    },
                ),
            )
        }
    }
    gridRows(saved.itemCount) { index ->
        saved[index]?.let { post ->
            PostGridItem(
                image = post.media.firstOrNull(),
                isRepost = false,
                onClick = { actions.onOpenPost(post.id) },
            )
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

private const val GRID_COLUMNS = 3
private val GridSpacing = 2.dp
