package com.geowoof.feature.map

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ElevatedAssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geowoof.core.designsystem.component.GeoWoofLogoHead
import com.geowoof.core.designsystem.icon.GeoWoofIcons
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.core.model.ActiveWalk
import com.geowoof.core.model.LiveWalker
import com.geowoof.core.model.MapViewport
import com.geowoof.core.ui.FloatingInboxActions
import com.geowoof.core.ui.isThemeDark
import com.geowoof.core.ui.mapStyleResource
import com.geowoof.core.ui.userMessage
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.Dash
import com.google.android.gms.maps.model.Gap
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.RoundCap
import com.google.maps.android.compose.ComposeMapColorScheme
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch

/**
 * Full-screen map, drawn edge to edge behind the system bars, with the walk on top. Without a Maps
 * API key the tiles are blank but everything works: the recording never depends on the map.
 */
@Composable
fun MapScreen(
    onOpenSummary: () -> Unit,
    onAddPet: () -> Unit,
    onOpenUser: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MapViewModel = hiltViewModel(),
    liveViewModel: LiveWalkersViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val live by liveViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        viewModel.onPermissionsResult()
    }
    LifecycleResumeEffect(Unit) {
        viewModel.onScreenResumed()
        onPauseOrDispose {}
    }
    val currentOnOpenSummary by rememberUpdatedState(onOpenSummary)
    LaunchedEffect(uiState.openSummary) {
        if (uiState.openSummary) {
            viewModel.onSummaryOpened()
            currentOnOpenSummary()
        }
    }
    MapContent(
        uiState = uiState,
        actions = MapActions(
            onStart = viewModel::onStartClick,
            onDismissPetPicker = viewModel::onDismissPetPicker,
            onTogglePet = viewModel::onTogglePet,
            onConfirmPets = viewModel::onConfirmPets,
            onAddPet = onAddPet,
            onPause = viewModel::onPauseClick,
            onResume = viewModel::onResumeClick,
            onFinish = viewModel::onFinishClick,
            onCancel = viewModel::onCancelClick,
            onReview = viewModel::onReviewClick,
            onRequestPermissions = { permissions.launch(walkPermissions()) },
            onOpenAppSettings = {
                viewModel.onDismissPrompt()
                context.openAppSettings()
            },
            onOpenLocationSettings = {
                viewModel.onDismissPrompt()
                context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            },
            onDismissPrompt = viewModel::onDismissPrompt,
            onChangeVisibility = viewModel::onChangeVisibilityClick,
            onChooseVisibility = viewModel::onChooseVisibility,
            onDismissVisibility = viewModel::onDismissVisibility,
            onDismissError = viewModel::onErrorShown,
            onMyLocation = viewModel::onMyLocationClick,
        ),
        live = live,
        liveActions = LiveWalkersActions(
            onOpenList = liveViewModel::onOpenList,
            onDismissList = liveViewModel::onDismissList,
            onWalkerClick = liveViewModel::onWalkerClick,
            onDismissWalker = liveViewModel::onDismissWalker,
            onOpenProfile = { userId ->
                liveViewModel.onDismissWalker()
                onOpenUser(userId)
            },
            onFollow = liveViewModel::onFollowClick,
            onConfirmFollowBack = liveViewModel::onConfirmFollowBack,
            onDismissFollowBack = liveViewModel::onDismissFollowBack,
            onDismissError = liveViewModel::onErrorShown,
        ),
        modifier = modifier,
    ) { mapModifier ->
        WalkMap(
            walk = uiState.walk,
            hasLocationPermission = uiState.hasLocationPermission,
            walkers = live.walkers,
            onViewportChange = liveViewModel::onViewportChange,
            onWalkerClick = liveViewModel::onWalkerClick,
            cameraTarget = uiState.cameraTarget,
            onReachCameraTarget = viewModel::onCameraTargetReached,
            modifier = mapModifier,
        )
    }
}

/** Without the notifications, the walk is recorded anyway. */
private fun walkPermissions(): Array<String> = buildList {
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    add(Manifest.permission.ACCESS_COARSE_LOCATION)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
}.toTypedArray()

private fun Context.openAppSettings() {
    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
}

/** The map is a slot, because it needs Google Play services and the tests of the screen do not. */
@Composable
internal fun MapContent(
    uiState: MapUiState,
    actions: MapActions,
    live: LiveWalkersUiState,
    liveActions: LiveWalkersActions,
    modifier: Modifier = Modifier,
    map: @Composable (Modifier) -> Unit,
) {
    val snackbars = remember { SnackbarHostState() }
    MapErrors(uiState, actions, live, liveActions, snackbars)
    Box(modifier = modifier.fillMaxSize()) {
        map(Modifier.fillMaxSize())
        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
        ) {
            TopControls(uiState, actions, live, liveActions, Modifier.align(Alignment.TopCenter))
            Column(
                modifier = Modifier.align(Alignment.BottomCenter),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SnackbarHost(snackbars)
                WalkPanel(uiState = uiState, actions = actions)
            }
        }
    }
    if (uiState.isPickingPets) {
        PetPickerSheet(uiState = uiState, actions = actions)
    }
    if (uiState.isChoosingVisibility) {
        LiveVisibilitySheet(uiState = uiState, actions = actions)
    }
    if (live.isListOpen) {
        LiveWalkersListSheet(walkers = live.walkers, actions = liveActions)
    }
    live.selected?.let { walker -> LiveWalkerSheet(uiState = live, walker = walker, actions = liveActions) }
    uiState.prompt?.let { prompt -> WalkPromptDialog(prompt = prompt, actions = actions) }
}

@Composable
private fun MapErrors(
    uiState: MapUiState,
    actions: MapActions,
    live: LiveWalkersUiState,
    liveActions: LiveWalkersActions,
    snackbars: SnackbarHostState,
) {
    val walkError = uiState.error?.userMessage()
    val liveError = live.error?.userMessage()
    val currentActions by rememberUpdatedState(actions)
    val currentLiveActions by rememberUpdatedState(liveActions)
    LaunchedEffect(walkError) {
        if (walkError != null) {
            snackbars.showSnackbar(walkError)
            currentActions.onDismissError()
        }
    }
    LaunchedEffect(liveError) {
        if (liveError != null) {
            snackbars.showSnackbar(liveError)
            currentLiveActions.onDismissError()
        }
    }
}

/** What is always at hand: what is waiting, who is around and going back to where the person is. */
@Composable
private fun TopControls(
    uiState: MapUiState,
    actions: MapActions,
    live: LiveWalkersUiState,
    liveActions: LiveWalkersActions,
    modifier: Modifier = Modifier,
) {
    val spacing = GeoWoofTheme.spacing
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.medium, vertical = spacing.small),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.small),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            FloatingInboxActions()
            if (uiState.hasLocationPermission) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = StatusElevation,
                ) {
                    IconButton(onClick = actions.onMyLocation) {
                        Icon(
                            painter = painterResource(GeoWoofIcons.MyLocation),
                            contentDescription = stringResource(R.string.feature_map_my_location),
                        )
                    }
                }
            }
        }
        LiveStatus(live = live, actions = liveActions)
    }
}

/** What there is to say about the people on the map: also the way to reach them without the markers. */
@Composable
private fun LiveStatus(live: LiveWalkersUiState, actions: LiveWalkersActions, modifier: Modifier = Modifier) {
    when {
        live.isTooWide -> Surface(
            modifier = modifier.padding(GeoWoofTheme.spacing.small),
            shape = MaterialTheme.shapes.small,
            tonalElevation = StatusElevation,
            shadowElevation = StatusElevation,
        ) {
            Text(
                text = stringResource(R.string.feature_map_live_too_wide),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(
                    horizontal = GeoWoofTheme.spacing.medium,
                    vertical = GeoWoofTheme.spacing.small,
                ),
            )
        }

        live.walkers.isNotEmpty() -> ElevatedAssistChip(
            onClick = actions.onOpenList,
            modifier = modifier,
            shape = CircleShape,
            leadingIcon = { GeoWoofLogoHead(contentDescription = null, Modifier.height(LiveStatusLogoHeight)) },
            label = {
                Text(pluralStringResource(R.plurals.feature_map_live_walkers, live.walkers.size, live.walkers.size))
            },
        )
    }
}

@Composable
private fun WalkMap(
    walk: ActiveWalk?,
    hasLocationPermission: Boolean,
    walkers: List<LiveWalker>,
    onViewportChange: (MapViewport) -> Unit,
    onWalkerClick: (String) -> Unit,
    cameraTarget: CameraTarget?,
    onReachCameraTarget: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(DefaultCenter, DEFAULT_ZOOM)
    }
    val scope = rememberCoroutineScope()
    val currentOnViewportChange by rememberUpdatedState(onViewportChange)
    LaunchedEffect(cameraPositionState) {
        // The position too: a jump while the map is loading does not go through isMoving, and the map
        // would stay subscribed to where it started.
        snapshotFlow {
            cameraPositionState.position to
                cameraPositionState.takeUnless { it.isMoving }?.projection?.visibleRegion?.latLngBounds
        }
            .mapNotNull { (_, bounds) -> bounds }
            .distinctUntilChanged()
            .collect { bounds ->
                currentOnViewportChange(
                    MapViewport(
                        north = bounds.northeast.latitude,
                        south = bounds.southwest.latitude,
                        east = bounds.northeast.longitude,
                        west = bounds.southwest.longitude,
                    ),
                )
            }
    }
    val lastPoint = walk?.lastPoint
    LaunchedEffect(lastPoint) {
        lastPoint?.let {
            val target = LatLng(it.position.latitude, it.position.longitude)
            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(target, WALK_ZOOM))
        }
    }
    val currentOnReachCameraTarget by rememberUpdatedState(onReachCameraTarget)
    LaunchedEffect(cameraTarget) {
        cameraTarget?.let { target ->
            val update = CameraUpdateFactory.newLatLngZoom(
                LatLng(target.position.latitude, target.position.longitude),
                PERSON_ZOOM,
            )
            if (target.isJump) cameraPositionState.move(update) else cameraPositionState.animate(update)
            currentOnReachCameraTarget()
        }
    }
    val context = LocalContext.current
    val isDark = isThemeDark()
    val styleResource = mapStyleResource()
    val style = remember(context, styleResource) { MapStyleOptions.loadRawResourceStyle(context, styleResource) }
    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraPositionState,
        contentDescription = stringResource(R.string.feature_map_content_description),
        properties = MapProperties(isMyLocationEnabled = hasLocationPermission, mapStyleOptions = style),
        uiSettings = MapUiSettings(
            zoomControlsEnabled = false,
            myLocationButtonEnabled = false,
            mapToolbarEnabled = false,
        ),
        contentPadding = WindowInsets.safeDrawing.asPaddingValues(),
        mapColorScheme = if (isDark) ComposeMapColorScheme.DARK else ComposeMapColorScheme.LIGHT,
    ) {
        // The dashed trail of the logo: the walk is drawing it.
        walk?.segments?.forEach { segment ->
            Polyline(
                points = segment.points.map { LatLng(it.position.latitude, it.position.longitude) },
                color = MaterialTheme.colorScheme.primary,
                width = ROUTE_WIDTH,
                pattern = RoutePattern,
                jointType = JointType.ROUND,
                startCap = RoundCap(),
                endCap = RoundCap(),
            )
        }
        LiveWalkersLayer(
            walkers = walkers,
            onWalkerClick = onWalkerClick,
            onClusterClick = { target ->
                scope.launch {
                    val closer = cameraPositionState.position.zoom + CLUSTER_ZOOM_STEP
                    cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(target, closer))
                }
            },
        )
    }
}

/** Initial camera until the user's location is available (Madrid). */
private val DefaultCenter = LatLng(40.4168, -3.7038)

/** A tall phone at 12 would already need more cells than the live map takes (docs/realtime.md). */
private const val DEFAULT_ZOOM = 13f
private const val WALK_ZOOM = 17f
private const val PERSON_ZOOM = 15f
private const val ROUTE_WIDTH = 14f
private const val CLUSTER_ZOOM_STEP = 2f
private val StatusElevation = 3.dp
private val LiveStatusLogoHeight = 22.dp
private val RoutePattern = listOf(Dash(ROUTE_WIDTH * 2.4f), Gap(ROUTE_WIDTH * 1.8f))
