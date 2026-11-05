package com.geowoof.feature.map

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geowoof.core.model.ActiveWalk
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.ComposeMapColorScheme
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState

/**
 * Full-screen map, drawn edge to edge behind the system bars, with the walk on top. Without a Maps
 * API key the tiles are blank but everything works: the recording never depends on the map.
 */
@Composable
fun MapScreen(
    onOpenSummary: () -> Unit,
    onAddPet: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MapViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
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
        ),
        modifier = modifier,
    ) { mapModifier ->
        WalkMap(walk = uiState.walk, hasLocationPermission = uiState.hasLocationPermission, modifier = mapModifier)
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
    modifier: Modifier = Modifier,
    map: @Composable (Modifier) -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        map(Modifier.fillMaxSize())
        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            WalkPanel(uiState = uiState, actions = actions)
        }
    }
    if (uiState.isPickingPets) {
        PetPickerSheet(uiState = uiState, actions = actions)
    }
    uiState.prompt?.let { prompt -> WalkPromptDialog(prompt = prompt, actions = actions) }
}

@Composable
private fun WalkMap(walk: ActiveWalk?, hasLocationPermission: Boolean, modifier: Modifier = Modifier) {
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(DefaultCenter, DEFAULT_ZOOM)
    }
    val lastPoint = walk?.lastPoint
    LaunchedEffect(lastPoint) {
        lastPoint?.let {
            val target = LatLng(it.position.latitude, it.position.longitude)
            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(target, WALK_ZOOM))
        }
    }
    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraPositionState,
        contentDescription = stringResource(R.string.feature_map_content_description),
        properties = MapProperties(isMyLocationEnabled = hasLocationPermission),
        uiSettings = MapUiSettings(zoomControlsEnabled = false),
        contentPadding = WindowInsets.safeDrawing.asPaddingValues(),
        mapColorScheme = ComposeMapColorScheme.FOLLOW_SYSTEM,
    ) {
        walk?.segments?.forEach { segment ->
            Polyline(
                points = segment.points.map { LatLng(it.position.latitude, it.position.longitude) },
                color = MaterialTheme.colorScheme.primary,
                width = ROUTE_WIDTH,
            )
        }
    }
}

/** Initial camera until the user's location is available (Madrid). */
private val DefaultCenter = LatLng(40.4168, -3.7038)
private const val DEFAULT_ZOOM = 12f
private const val WALK_ZOOM = 17f
private const val ROUTE_WIDTH = 14f
