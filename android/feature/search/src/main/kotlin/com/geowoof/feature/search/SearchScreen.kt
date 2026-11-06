package com.geowoof.feature.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewFontScale
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.geowoof.core.data.repository.RemoteLoadException
import com.geowoof.core.designsystem.component.EmptyState
import com.geowoof.core.designsystem.component.ErrorState
import com.geowoof.core.designsystem.component.GeoWoofIllustrations
import com.geowoof.core.designsystem.component.SkeletonList
import com.geowoof.core.designsystem.icon.GeoWoofIcons
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.core.model.PetSearchResult
import com.geowoof.core.model.PetSpecies
import com.geowoof.core.model.Post
import com.geowoof.core.model.UserSummary
import com.geowoof.core.ui.InboxActions
import com.geowoof.core.ui.PostGridItem
import com.geowoof.core.ui.RemoteAvatar
import com.geowoof.core.ui.UserRow
import com.geowoof.core.ui.labelRes
import com.geowoof.core.ui.userMessage
import kotlinx.coroutines.flow.flowOf

@Composable
fun SearchScreen(
    onOpenUser: (userId: String) -> Unit,
    onOpenPost: (postId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val users = viewModel.users.collectAsLazyPagingItems()
    val pets = viewModel.pets.collectAsLazyPagingItems()
    val explore = viewModel.explore.collectAsLazyPagingItems()
    // Back from a profile, someone may have been blocked or may have changed their name.
    LifecycleResumeEffect(users, pets, explore) {
        users.refresh()
        pets.refresh()
        explore.refresh()
        onPauseOrDispose {}
    }
    SearchContent(
        uiState = uiState,
        query = viewModel.query,
        users = users,
        pets = pets,
        explore = explore,
        onSelectTab = viewModel::onSelectTab,
        onOpenUser = onOpenUser,
        onOpenPost = onOpenPost,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SearchContent(
    uiState: SearchUiState,
    query: TextFieldState,
    users: LazyPagingItems<UserSummary>,
    pets: LazyPagingItems<PetSearchResult>,
    explore: LazyPagingItems<Post>,
    onSelectTab: (SearchTab) -> Unit,
    onOpenUser: (userId: String) -> Unit,
    onOpenPost: (postId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = GeoWoofTheme.spacing
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(title = {
                Text(stringResource(R.string.feature_search_search_title))
            }, actions = { InboxActions() })
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            OutlinedTextField(
                state = query,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.large, vertical = spacing.small),
                label = { Text(stringResource(R.string.feature_search_field)) },
                leadingIcon = { Icon(painterResource(GeoWoofIcons.Search), contentDescription = null) },
                lineLimits = TextFieldLineLimits.SingleLine,
                shape = MaterialTheme.shapes.extraLarge,
            )
            if (uiState.hasEnoughText) {
                PrimaryTabRow(selectedTabIndex = uiState.tab.ordinal) {
                    Tab(
                        selected = uiState.tab == SearchTab.People,
                        onClick = { onSelectTab(SearchTab.People) },
                        text = { Text(stringResource(R.string.feature_search_tab_people)) },
                    )
                    Tab(
                        selected = uiState.tab == SearchTab.Pets,
                        onClick = { onSelectTab(SearchTab.Pets) },
                        text = { Text(stringResource(R.string.feature_search_tab_pets)) },
                    )
                }
            }
            when {
                !uiState.hasEnoughText -> ExploreGrid(explore, onOpenPost)

                uiState.tab == SearchTab.People -> Results(
                    items = users,
                    emptyMessage = stringResource(R.string.feature_search_no_people),
                ) {
                    items(users.itemCount, key = users.itemKey(UserSummary::id)) { index ->
                        users[index]?.let { user -> UserRow(user, onClick = { onOpenUser(user.id) }) }
                    }
                }

                else -> Results(
                    items = pets,
                    emptyMessage = stringResource(R.string.feature_search_no_pets),
                ) {
                    items(pets.itemCount, key = pets.itemKey(PetSearchResult::id)) { index ->
                        pets[index]?.let { pet -> PetResultRow(pet, onClick = { onOpenUser(pet.owner.id) }) }
                    }
                }
            }
        }
    }
}

/** Before searching: the most recent of what anyone can see. Without anything, it explains what the field is for. */
@Composable
private fun ExploreGrid(
    posts: LazyPagingItems<Post>,
    onOpenPost: (postId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (posts.itemCount == 0) {
        EmptyState(
            icon = GeoWoofIcons.PersonSearch,
            title = stringResource(R.string.feature_search_search_empty_title),
            message = stringResource(R.string.feature_search_search_empty_message),
            modifier = modifier,
            illustration = GeoWoofIllustrations.EmptySearch,
        )
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(EXPLORE_COLUMNS),
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(ExploreSpacing),
            horizontalArrangement = Arrangement.spacedBy(ExploreSpacing),
            verticalArrangement = Arrangement.spacedBy(ExploreSpacing),
        ) {
            items(posts.itemCount, key = posts.itemKey(Post::id)) { index ->
                posts[index]?.let { post ->
                    PostGridItem(
                        image = post.media.firstOrNull(),
                        isRepost = false,
                        onClick = { onOpenPost(post.id) },
                    )
                }
            }
        }
    }
}

/** What is common to both lists: waiting, failing, finding nothing and showing what was found. */
@Composable
private fun <T : Any> Results(
    items: LazyPagingItems<T>,
    emptyMessage: String,
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
) {
    val refresh = items.loadState.refresh
    val isEmpty = items.itemCount == 0
    when {
        isEmpty && refresh is LoadState.Loading -> SkeletonList(modifier)

        isEmpty && refresh is LoadState.Error -> ErrorState(
            title = stringResource(R.string.feature_search_error_title),
            message = (refresh.error as? RemoteLoadException)?.error?.userMessage().orEmpty(),
            retryLabel = stringResource(R.string.feature_search_retry),
            onRetry = items::retry,
            modifier = modifier,
        )

        isEmpty -> EmptyState(
            icon = GeoWoofIcons.Search,
            title = stringResource(R.string.feature_search_nothing_found),
            message = emptyMessage,
            modifier = modifier,
            illustration = GeoWoofIllustrations.EmptySearch,
        )

        else -> LazyColumn(modifier = modifier.fillMaxSize(), content = content)
    }
}

@Composable
private fun PetResultRow(pet: PetSearchResult, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val kind = pet.breed ?: pet.speciesOther ?: stringResource(pet.species.labelRes())
    ListItem(
        modifier = modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text(pet.name, style = MaterialTheme.typography.titleSmall) },
        supportingContent = {
            Text(
                text = stringResource(R.string.feature_search_pet_owner, kind, pet.owner.username),
                style = MaterialTheme.typography.bodySmall,
            )
        },
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

private val PetAvatarSize = 48.dp
private val ExploreSpacing = 4.dp
private const val EXPLORE_COLUMNS = 3

private val FINISHED = LoadStates(
    refresh = LoadState.NotLoading(endOfPaginationReached = true),
    prepend = LoadState.NotLoading(endOfPaginationReached = true),
    append = LoadState.NotLoading(endOfPaginationReached = true),
)

@PreviewLightDark
@PreviewFontScale
@Composable
private fun SearchIdlePreview() {
    GeoWoofTheme(dynamicColor = false) {
        SearchContent(
            uiState = SearchUiState(),
            query = TextFieldState(),
            users = flowOf(PagingData.empty<UserSummary>(FINISHED)).collectAsLazyPagingItems(),
            pets = flowOf(PagingData.empty<PetSearchResult>(FINISHED)).collectAsLazyPagingItems(),
            explore = flowOf(PagingData.empty<Post>(FINISHED)).collectAsLazyPagingItems(),
            onSelectTab = {},
            onOpenUser = {},
            onOpenPost = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun SearchPetsPreview() {
    val owner = UserSummary("1", "ana", "Ana", "Madrid", null)
    GeoWoofTheme(dynamicColor = false) {
        SearchContent(
            uiState = SearchUiState(tab = SearchTab.Pets, hasEnoughText = true),
            query = TextFieldState("lu"),
            users = flowOf(PagingData.empty<UserSummary>(FINISHED)).collectAsLazyPagingItems(),
            pets = flowOf(
                PagingData.from(
                    listOf(PetSearchResult("2", "Luna", PetSpecies.Dog, null, "Border collie", null, owner)),
                    FINISHED,
                ),
            ).collectAsLazyPagingItems(),
            explore = flowOf(PagingData.empty<Post>(FINISHED)).collectAsLazyPagingItems(),
            onSelectTab = {},
            onOpenUser = {},
            onOpenPost = {},
        )
    }
}
