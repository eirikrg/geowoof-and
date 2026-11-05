package com.geowoof.feature.walks.detail

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.geowoof.core.model.RouteSegment
import com.geowoof.feature.walks.R
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.ComposeMapColorScheme
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapEffect
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MapsComposeExperimentalApi
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState

/** The route on the map, framed. [onMapReady] gives access to the map, to take a snapshot when sharing. */
@OptIn(MapsComposeExperimentalApi::class)
@Composable
internal fun WalkRouteMap(route: List<RouteSegment>?, onMapReady: (GoogleMap) -> Unit, modifier: Modifier = Modifier) {
    val cameraPositionState = rememberCameraPositionState()
    var isLoaded by remember { mutableStateOf(false) }
    val currentOnMapReady by rememberUpdatedState(onMapReady)
    val lines = remember(route) {
        route.orEmpty().map { segment -> segment.points.map { LatLng(it.position.latitude, it.position.longitude) } }
    }
    LaunchedEffect(lines, isLoaded) {
        val points = lines.flatten()
        if (isLoaded && points.isNotEmpty()) {
            val bounds = points.fold(LatLngBounds.builder()) { builder, point -> builder.include(point) }.build()
            cameraPositionState.move(CameraUpdateFactory.newLatLngBounds(bounds, FRAME_PADDING))
        }
    }
    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraPositionState,
        contentDescription = stringResource(R.string.feature_walks_route),
        uiSettings = MapUiSettings(zoomControlsEnabled = false),
        mapColorScheme = ComposeMapColorScheme.FOLLOW_SYSTEM,
        onMapLoaded = { isLoaded = true },
    ) {
        MapEffect(Unit) { map -> currentOnMapReady(map) }
        lines.forEach { line ->
            Polyline(points = line, color = MaterialTheme.colorScheme.primary, width = ROUTE_WIDTH)
        }
    }
}

private const val FRAME_PADDING = 96
private const val ROUTE_WIDTH = 14f
