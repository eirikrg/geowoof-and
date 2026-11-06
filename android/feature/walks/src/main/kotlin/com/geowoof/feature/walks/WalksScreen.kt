package com.geowoof.feature.walks

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.geowoof.core.data.repository.WalkLoadException
import com.geowoof.core.designsystem.component.EmptyState
import com.geowoof.core.designsystem.component.ErrorState
import com.geowoof.core.designsystem.component.SkeletonList
import com.geowoof.core.designsystem.icon.GeoWoofIcons
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.core.model.Walk
import com.geowoof.core.model.WalkTotals
import com.geowoof.core.ui.WalkCard
import com.geowoof.core.ui.WalkTotalsGrid
import com.geowoof.core.ui.userMessage
import kotlinx.coroutines.flow.flowOf

@Composable
internal fun WalksScreen(
    onBack: () -> Unit,
    onOpenWalk: (walkId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WalksViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val walks = viewModel.walks.collectAsLazyPagingItems()
    WalksContent(
        uiState = uiState,
        walks = walks,
        onBack = onBack,
        onOpenWalk = onOpenWalk,
        onRefresh = {
            walks.refresh()
            viewModel.refreshTotals()
        },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WalksContent(
    uiState: WalksUiState,
    walks: LazyPagingItems<Walk>,
    onBack: () -> Unit,
    onOpenWalk: (walkId: String) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbars = remember { SnackbarHostState() }
    val refresh = walks.loadState.refresh
    val refreshMessage = ((refresh as? LoadState.Error)?.error as? WalkLoadException)?.error?.userMessage()
    val hasWalks = walks.itemCount > 0
    LaunchedEffect(refreshMessage, hasWalks) {
        if (refreshMessage != null && hasWalks) snackbars.showSnackbar(refreshMessage)
    }
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbars) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        uiState.petName?.let { stringResource(R.string.feature_walks_pet_title, it) }
                            ?: stringResource(R.string.feature_walks_my_title),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(GeoWoofIcons.ArrowBack),
                            contentDescription = stringResource(R.string.feature_walks_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = refresh is LoadState.Loading && hasWalks,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
                hasWalks -> WalkList(uiState, walks, onOpenWalk)
                refresh is LoadState.Loading -> LoadingWalks()
                refresh is LoadState.Error -> NoWalksWithError(refreshMessage, onRefresh)
                else -> NoWalks()
            }
        }
    }
}

@Composable
private fun WalkList(
    uiState: WalksUiState,
    walks: LazyPagingItems<Walk>,
    onOpenWalk: (walkId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = GeoWoofTheme.spacing
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = spacing.huge),
    ) {
        uiState.totals?.let { totals ->
            item { WalkTotalsGrid(totals, modifier = Modifier.padding(spacing.large)) }
        }
        items(count = walks.itemCount, key = walks.itemKey { it.id }) { index ->
            walks[index]?.let { walk ->
                WalkCard(
                    walk = walk,
                    petNames = walk.petIds.mapNotNull(uiState.petNames::get).joinToString(),
                    onClick = { onOpenWalk(walk.id) },
                )
            }
        }
        if (walks.loadState.append is LoadState.Error) {
            item {
                TextButton(onClick = walks::retry, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.feature_walks_retry))
                }
            }
        }
    }
}

@Composable
private fun LoadingWalks(modifier: Modifier = Modifier) {
    SkeletonList(modifier = modifier)
}

@Composable
private fun NoWalks(modifier: Modifier = Modifier) {
    // Pulling to refresh needs something that scrolls.
    LazyColumn(modifier = modifier.fillMaxSize()) {
        item {
            EmptyState(
                icon = GeoWoofIcons.DirectionsWalk,
                title = stringResource(R.string.feature_walks_empty_title),
                message = stringResource(R.string.feature_walks_empty_message),
            )
        }
    }
}

@Composable
private fun NoWalksWithError(message: String?, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    ErrorState(
        title = stringResource(R.string.feature_walks_error_title),
        message = message.orEmpty(),
        retryLabel = stringResource(R.string.feature_walks_retry),
        onRetry = onRetry,
        modifier = modifier,
    )
}

@PreviewLightDark
@Composable
private fun WalksEmptyPreview() {
    GeoWoofTheme(dynamicColor = false) {
        WalksContent(
            uiState = WalksUiState(totals = WalkTotals(0, 0, 0, 0, 0)),
            walks = flowOf(
                PagingData.empty<Walk>(
                    LoadStates(LoadState.NotLoading(true), LoadState.NotLoading(true), LoadState.NotLoading(true)),
                ),
            ).collectAsLazyPagingItems(),
            onBack = {},
            onOpenWalk = {},
            onRefresh = {},
        )
    }
}
