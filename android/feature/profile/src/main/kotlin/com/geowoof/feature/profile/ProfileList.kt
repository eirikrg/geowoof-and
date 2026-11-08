package com.geowoof.feature.profile

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import com.geowoof.core.designsystem.component.EmptyState
import com.geowoof.core.designsystem.component.FittedText
import com.geowoof.core.designsystem.component.GeoWoofIllustrations
import com.geowoof.core.designsystem.component.IconBadge
import com.geowoof.core.designsystem.component.SkeletonBox
import com.geowoof.core.designsystem.component.SkeletonList
import com.geowoof.core.designsystem.icon.GeoWoofIcons
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.core.model.Post
import com.geowoof.core.model.Profile
import com.geowoof.core.model.TimelineItem
import com.geowoof.core.model.Walk
import com.geowoof.core.ui.PagedContent
import com.geowoof.core.ui.PostGridItem
import com.geowoof.core.ui.WalkCard
import com.geowoof.core.ui.WalkTotalsGrid
import com.geowoof.core.ui.heroListPadding
import com.geowoof.core.ui.pagedContent

/** The tabs of a profile. Only you see [Saved], in your own. */
internal enum class ProfileTab(@param:StringRes val label: Int) {
    Photos(R.string.feature_profile_tab_photos),
    Walks(R.string.feature_profile_tab_walks),
    Saved(R.string.feature_profile_tab_saved),
}

/** The lists of a profile, which come page by page. */
internal class ProfileLists(
    val posts: LazyPagingItems<TimelineItem>,
    val walks: LazyPagingItems<Walk>,
    /** Only in your own profile. */
    val saved: LazyPagingItems<Post>?,
) {
    /** What coming back to the profile reloads: pulling it down or retrying has to show the same. */
    fun refresh() {
        posts.refresh()
        saved?.refresh()
    }
}

@Composable
internal fun ProfileList(
    uiState: ProfileUiState,
    profile: Profile,
    lists: ProfileLists,
    actions: ProfileActions,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    var selectedTab by rememberSaveable { mutableStateOf(ProfileTab.Photos) }
    var shownPetId by rememberSaveable { mutableStateOf<String?>(null) }
    val tabs = ProfileTab.entries.filter { it != ProfileTab.Saved || lists.saved != null }
    val photos = lists.posts.pagedContent()
    val walks = lists.walks.pagedContent()
    val saved = lists.saved?.pagedContent()
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = listState,
        contentPadding = heroListPadding(),
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
            petHighlights(uiState.pets, actions, onShowPet = { shownPetId = it })
            stickyHeader { ProfileTabs(tabs, selectedTab, onSelect = { selectedTab = it }) }
            when (selectedTab) {
                ProfileTab.Photos -> photosTab(uiState, lists.posts, photos, actions)
                ProfileTab.Walks -> walksTab(uiState, lists.walks, walks, actions)
                ProfileTab.Saved -> if (lists.saved != null && saved != null) savedTab(lists.saved, saved, actions)
            }
        }
    }
    // Your own pets open their page, and they never come here.
    uiState.pets.find { it.id == shownPetId }?.let { pet ->
        PetSheet(pet, onDismiss = { shownPetId = null })
    }
}

/** They share the width, unless a very large font needs them to scroll to fit. */
@Composable
private fun ProfileTabs(
    tabs: List<ProfileTab>,
    selected: ProfileTab,
    onSelect: (ProfileTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val index = tabs.indexOf(selected)
    val content: @Composable () -> Unit = {
        tabs.forEach { tab ->
            Tab(
                selected = selected == tab,
                onClick = { onSelect(tab) },
                text = { FittedText(stringResource(tab.label), style = MaterialTheme.typography.titleSmall) },
            )
        }
    }
    if (LocalDensity.current.fontScale > LARGE_FONT_SCALE) {
        PrimaryScrollableTabRow(selectedTabIndex = index, edgePadding = 0.dp, modifier = modifier, tabs = content)
    } else {
        PrimaryTabRow(selectedTabIndex = index, modifier = modifier, tabs = content)
    }
}

/**
 * A grid inside the list of the profile, which already scrolls: each row is an element of that list. It has the
 * margin of the cards of the other tabs.
 */
private fun LazyListScope.gridRows(count: Int, cell: @Composable (index: Int) -> Unit) {
    items((count + GRID_COLUMNS - 1) / GRID_COLUMNS) { row ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = GeoWoofTheme.spacing.large),
            horizontalArrangement = Arrangement.spacedBy(GridSpacing),
        ) {
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
    content: PagedContent,
    actions: ProfileActions,
) {
    val onCreatePost = actions.onCreatePost
    when (content) {
        // Nothing that could turn out wrong: neither the cell to add a photo nor that there are none.
        PagedContent.Loading -> {
            item { Spacer(Modifier.height(GeoWoofTheme.spacing.medium)) }
            gridRows(SKELETON_CELLS) { SkeletonBox(Modifier.aspectRatio(1f), MaterialTheme.shapes.small) }
        }

        PagedContent.Empty -> item {
            if (uiState.isOwn) {
                EmptyState(
                    icon = GeoWoofIcons.PhotoLibrary,
                    title = stringResource(R.string.feature_profile_no_own_photos_title),
                    message = stringResource(R.string.feature_profile_no_own_photos),
                    illustration = GeoWoofIllustrations.EmptyPhotos,
                    action = onCreatePost?.let { onClick ->
                        {
                            FilledTonalButton(onClick = onClick) {
                                Text(stringResource(R.string.feature_profile_add_photo))
                            }
                        }
                    },
                )
            } else {
                EmptyLine(icon = GeoWoofIcons.PhotoLibrary, text = stringResource(R.string.feature_profile_no_photos))
            }
        }

        PagedContent.Error -> item {
            EmptyLine(icon = GeoWoofIcons.Error, text = stringResource(R.string.feature_profile_photos_failed))
        }

        PagedContent.Items -> {
            item { Spacer(Modifier.height(GeoWoofTheme.spacing.medium)) }
            // The first cell of your own photos is the way to add another.
            val first = if (onCreatePost != null) 1 else 0
            gridRows(posts.itemCount + first) { index ->
                if (onCreatePost != null && index == 0) {
                    AddPhotoCell(onCreatePost)
                } else {
                    posts[index - first]?.let { item ->
                        PostGridItem(
                            image = item.post.media.firstOrNull(),
                            isRepost = item.repostedBy != null,
                            onClick = { actions.onOpenPost(item.post.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AddPhotoCell(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.aspectRatio(1f),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(GeoWoofTheme.spacing.small),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(painterResource(GeoWoofIcons.AddAPhoto), contentDescription = null)
            Text(
                text = stringResource(R.string.feature_profile_add_photo),
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun LazyListScope.savedTab(saved: LazyPagingItems<Post>, content: PagedContent, actions: ProfileActions) {
    if (content != PagedContent.Items) {
        item { SavedPostsPlaceholder(content, onRetry = saved::retry) }
        return
    }
    item { Spacer(Modifier.height(GeoWoofTheme.spacing.small)) }
    items(saved.itemCount, key = saved.itemKey(Post::id)) { index ->
        saved[index]?.let { post ->
            SavedPostCard(post, onClick = { actions.onOpenPost(post.id) })
        }
    }
}

private fun LazyListScope.walksTab(
    uiState: ProfileUiState,
    walks: LazyPagingItems<Walk>,
    content: PagedContent,
    actions: ProfileActions,
) {
    uiState.totals?.takeIf { it.walkCount > 0 }?.let { totals ->
        item {
            WalkTotalsGrid(
                totals = totals,
                modifier = Modifier.padding(GeoWoofTheme.spacing.large),
            )
        }
    }
    if (content == PagedContent.Loading) {
        item { SkeletonList(modifier = Modifier.padding(top = GeoWoofTheme.spacing.small), rows = SKELETON_ROWS) }
    }
    if (content == PagedContent.Empty || content == PagedContent.Error) {
        item {
            val failed = content == PagedContent.Error
            if (uiState.isOwn && !failed) {
                EmptyState(
                    icon = GeoWoofIcons.Route,
                    title = stringResource(R.string.feature_profile_no_own_walks_title),
                    message = stringResource(R.string.feature_profile_no_own_walks),
                    illustration = GeoWoofIllustrations.EmptyWalks,
                )
            } else {
                EmptyLine(
                    icon = if (failed) GeoWoofIcons.Error else GeoWoofIcons.Route,
                    text = stringResource(
                        if (failed) R.string.feature_profile_walks_failed else R.string.feature_profile_no_walks,
                    ),
                )
            }
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

/** A tab with nothing to show: smaller than an [EmptyState], because the profile is still above it. */
@Composable
private fun EmptyLine(@DrawableRes icon: Int, text: String, modifier: Modifier = Modifier) {
    val spacing = GeoWoofTheme.spacing
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.extraLarge, vertical = spacing.extraLarge),
        verticalArrangement = Arrangement.spacedBy(spacing.medium),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconBadge(icon = icon, size = EmptyLineIconSize, iconSize = EmptyLineGlyphSize)
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private const val GRID_COLUMNS = 3

/** Two rows of photos and three walks: where the first ones will be, so that nothing jumps when they come. */
private const val SKELETON_CELLS = 6
private const val SKELETON_ROWS = 3
private const val LARGE_FONT_SCALE = 1.3f
private val GridSpacing = 8.dp
private val EmptyLineIconSize = 56.dp
private val EmptyLineGlyphSize = 28.dp
