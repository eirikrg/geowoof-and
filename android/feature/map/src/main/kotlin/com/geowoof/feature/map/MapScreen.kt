package com.geowoof.feature.map

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.ComposeMapColorScheme
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.rememberCameraPositionState

/**
 * Full-screen map, drawn edge to edge behind the system bars. Without a Maps API key the tiles
 * are blank but the map still works; walk tracking (phase 3) never depends on it rendering.
 */
@Composable
fun MapScreen(modifier: Modifier = Modifier) {
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(DefaultCenter, DEFAULT_ZOOM)
    }
    GoogleMap(
        modifier = modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        contentDescription = stringResource(R.string.feature_map_content_description),
        uiSettings = MapUiSettings(zoomControlsEnabled = false),
        contentPadding = WindowInsets.safeDrawing.asPaddingValues(),
        mapColorScheme = ComposeMapColorScheme.FOLLOW_SYSTEM,
    )
}

/** Initial camera until the user's location is available (Madrid). */
private val DefaultCenter = LatLng(40.4168, -3.7038)
private const val DEFAULT_ZOOM = 12f
