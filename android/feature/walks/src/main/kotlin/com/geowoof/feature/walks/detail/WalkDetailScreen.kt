package com.geowoof.feature.walks.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geowoof.core.designsystem.icon.GeoWoofIcons
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.core.model.AppError
import com.geowoof.core.model.Walk
import com.geowoof.core.model.WalkPhoto
import com.geowoof.core.model.WalkStats
import com.geowoof.core.model.WalkSyncStatus
import com.geowoof.core.model.WalkVisibility
import com.geowoof.core.ui.RouteCanvas
import com.geowoof.core.ui.WalkPhotoImage
import com.geowoof.core.ui.WalkStatsGrid
import com.geowoof.core.ui.decodeRoute
import com.geowoof.core.ui.formattedIn
import com.geowoof.core.ui.labelRes
import com.geowoof.core.ui.toLines
import com.geowoof.core.ui.userMessage
import com.geowoof.feature.walks.R
import com.google.android.gms.maps.GoogleMap
import java.time.Instant

@Composable
internal fun WalkDetailScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WalkDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnDone by rememberUpdatedState(onDone)
    LaunchedEffect(uiState.isDeleted) {
        if (uiState.isDeleted) currentOnDone()
    }
    var googleMap by remember { mutableStateOf<GoogleMap?>(null) }
    var isSharing by remember { mutableStateOf(false) }
    var snapshot by remember { mutableStateOf<ImageBitmap?>(null) }
    WalkDetailContent(
        uiState = uiState,
        actions = WalkDetailActions(
            onBack = onBack,
            onShare = {
                // Each share takes what the map shows now. Without a map, the card draws the route.
                val map = googleMap
                if (map == null) {
                    isSharing = true
                } else {
                    map.snapshot { bitmap ->
                        snapshot = bitmap?.asImageBitmap()
                        isSharing = true
                    }
                }
            },
            onRetryUpload = viewModel::onRetryUpload,
            onRequestDelete = viewModel::onRequestDelete,
            onConfirmDelete = viewModel::onConfirmDelete,
            onDismissDelete = viewModel::onDismissDelete,
            onErrorShown = viewModel::onErrorShown,
        ),
        modifier = modifier,
    ) { mapModifier ->
        if (uiState.showsMap) {
            WalkRouteMap(route = uiState.route, onMapReady = { googleMap = it }, modifier = mapModifier)
        } else {
            RouteDrawing(uiState, modifier = mapModifier)
        }
    }
    val walk = uiState.walk
    if (isSharing && walk != null) {
        ShareWalkDialog(
            walk = walk,
            lines = uiState.route?.toLines() ?: decodeRoute(walk.previewPolylines),
            mapSnapshot = snapshot,
            petNames = uiState.petNames,
            onDismiss = { isSharing = false },
        )
    }
}

@Composable
private fun RouteDrawing(uiState: WalkDetailUiState, modifier: Modifier = Modifier) {
    val lines = remember(uiState.route, uiState.walk?.previewPolylines) {
        uiState.route?.toLines() ?: decodeRoute(uiState.walk?.previewPolylines.orEmpty())
    }
    RouteCanvas(
        lines = lines,
        contentDescription = stringResource(R.string.feature_walks_route),
        modifier = modifier,
        padding = GeoWoofTheme.spacing.extraLarge,
    )
}

@Suppress("LongParameterList")
internal class WalkDetailActions(
    val onBack: () -> Unit,
    val onShare: () -> Unit,
    val onRetryUpload: () -> Unit,
    val onRequestDelete: () -> Unit,
    val onConfirmDelete: () -> Unit,
    val onDismissDelete: () -> Unit,
    val onErrorShown: () -> Unit,
)

/** The map is a slot, because it needs Google Play services and the tests of the screen do not. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WalkDetailContent(
    uiState: WalkDetailUiState,
    actions: WalkDetailActions,
    modifier: Modifier = Modifier,
    map: @Composable (Modifier) -> Unit,
) {
    val snackbars = remember { SnackbarHostState() }
    val errorMessage = uiState.error?.userMessage()
    val currentOnErrorShown by rememberUpdatedState(actions.onErrorShown)
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            snackbars.showSnackbar(errorMessage)
            currentOnErrorShown()
        }
    }
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbars) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feature_walks_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(
                            painter = painterResource(GeoWoofIcons.ArrowBack),
                            contentDescription = stringResource(R.string.feature_walks_back),
                        )
                    }
                },
                actions = {
                    if (uiState.walk != null) {
                        IconButton(onClick = actions.onShare) {
                            Icon(
                                painter = painterResource(GeoWoofIcons.Share),
                                contentDescription = stringResource(R.string.feature_walks_share),
                            )
                        }
                        IconButton(onClick = actions.onRequestDelete) {
                            Icon(
                                painter = painterResource(GeoWoofIcons.Delete),
                                contentDescription = stringResource(R.string.feature_walks_delete),
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            Box(
                modifier = Modifier
                    .padding(horizontal = GeoWoofTheme.spacing.large)
                    .fillMaxWidth()
                    .height(MapHeight)
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
            ) {
                map(Modifier.fillMaxSize())
            }
            uiState.walk?.let { walk -> WalkDetails(walk, uiState, actions) }
        }
    }
    if (uiState.isConfirmingDelete) {
        AlertDialog(
            onDismissRequest = actions.onDismissDelete,
            title = { Text(stringResource(R.string.feature_walks_delete_title)) },
            text = { Text(stringResource(R.string.feature_walks_delete_message)) },
            confirmButton = {
                TextButton(onClick = actions.onConfirmDelete) { Text(stringResource(R.string.feature_walks_delete)) }
            },
            dismissButton = {
                TextButton(onClick = actions.onDismissDelete) { Text(stringResource(R.string.feature_walks_keep)) }
            },
        )
    }
}

@Composable
private fun WalkDetails(
    walk: Walk,
    uiState: WalkDetailUiState,
    actions: WalkDetailActions,
    modifier: Modifier = Modifier,
) {
    val spacing = GeoWoofTheme.spacing
    Column(
        modifier = modifier.padding(spacing.large),
        verticalArrangement = Arrangement.spacedBy(spacing.medium),
    ) {
        SyncStatusBanner(walk, actions)
        Column {
            Text(
                text = walk.startedAt.formattedIn(walk.timeZone),
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleLarge,
            )
            if (uiState.petNames.isNotEmpty()) {
                Text(
                    text = uiState.petNames,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        WalkStatsGrid(walk.stats)
        Text(
            text = stringResource(R.string.feature_walks_visibility, stringResource(walk.visibility.labelRes())),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        walk.note?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
        if (walk.photos.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                items(walk.photos) { photo -> WalkPhotoImage(photo, modifier = Modifier.size(PhotoSize)) }
            }
        }
        if (uiState.route == null && !uiState.isRefreshing) {
            Text(
                text = stringResource(R.string.feature_walks_loading_route),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SyncStatusBanner(walk: Walk, actions: WalkDetailActions, modifier: Modifier = Modifier) {
    val spacing = GeoWoofTheme.spacing
    when (walk.syncStatus) {
        WalkSyncStatus.Synced, WalkSyncStatus.PendingDelete -> Unit

        WalkSyncStatus.PendingSync -> Surface(
            modifier = modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = MaterialTheme.shapes.medium,
        ) {
            Row(
                modifier = Modifier.padding(spacing.medium),
                horizontalArrangement = Arrangement.spacedBy(spacing.small),
            ) {
                Icon(painterResource(GeoWoofIcons.CloudUpload), contentDescription = null)
                Text(stringResource(R.string.feature_walks_pending), style = MaterialTheme.typography.bodyMedium)
            }
        }

        WalkSyncStatus.Failed -> Surface(
            modifier = modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.errorContainer,
            shape = MaterialTheme.shapes.medium,
        ) {
            Column(
                modifier = Modifier.padding(spacing.medium),
                verticalArrangement = Arrangement.spacedBy(spacing.small),
            ) {
                Text(stringResource(R.string.feature_walks_failed), style = MaterialTheme.typography.titleSmall)
                walk.syncErrorCode?.let { code ->
                    Text(
                        AppError.Api(HTTP_UNPROCESSABLE, code).userMessage(),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Button(onClick = actions.onRetryUpload) { Text(stringResource(R.string.feature_walks_retry)) }
            }
        }
    }
}

private const val HTTP_UNPROCESSABLE = 422
private val MapHeight = 260.dp
private val PhotoSize = 120.dp

@PreviewLightDark
@Composable
private fun WalkDetailPreview() {
    GeoWoofTheme(dynamicColor = false) {
        WalkDetailContent(
            uiState = WalkDetailUiState(
                walk = Walk(
                    id = "walk",
                    startedAt = Instant.EPOCH,
                    endedAt = Instant.EPOCH.plusSeconds(2530),
                    timeZone = "Europe/Madrid",
                    petIds = listOf("luna"),
                    visibility = WalkVisibility.Friends,
                    note = "Por el Retiro",
                    stats = WalkStats(3120, 2530, 2380, 1.31, 763),
                    previewPolylines = emptyList(),
                    photos = emptyList<WalkPhoto>(),
                    syncStatus = WalkSyncStatus.Failed,
                    syncErrorCode = "walks.invalid_track",
                    updatedAt = Instant.EPOCH,
                ),
                petNames = "Luna",
            ),
            actions = WalkDetailActions({}, {}, {}, {}, {}, {}, {}),
            map = {},
        )
    }
}
