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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.geowoof.core.model.RouteSegment
import com.geowoof.core.ui.isThemeDark
import com.geowoof.core.ui.mapStyleResource
import com.geowoof.feature.walks.R
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.Dash
import com.google.android.gms.maps.model.Gap
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.RoundCap
import com.google.maps.android.compose.ComposeMapColorScheme
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapEffect
import com.google.maps.android.compose.MapProperties
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
    val context = LocalContext.current
    val styleResource = mapStyleResource()
    val style = remember(context, styleResource) { MapStyleOptions.loadRawResourceStyle(context, styleResource) }
    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraPositionState,
        contentDescription = stringResource(R.string.feature_walks_route),
        properties = MapProperties(mapStyleOptions = style),
        uiSettings = MapUiSettings(zoomControlsEnabled = false, mapToolbarEnabled = false),
        mapColorScheme = if (isThemeDark()) ComposeMapColorScheme.DARK else ComposeMapColorScheme.LIGHT,
        onMapLoaded = { isLoaded = true },
    ) {
        MapEffect(Unit) { map -> currentOnMapReady(map) }
        lines.forEach { line ->
            Polyline(
                points = line,
                color = MaterialTheme.colorScheme.primary,
                width = ROUTE_WIDTH,
                pattern = RoutePattern,
                jointType = JointType.ROUND,
                startCap = RoundCap(),
                endCap = RoundCap(),
            )
        }
    }
}

private const val FRAME_PADDING = 96
private const val ROUTE_WIDTH = 14f

/** The dashed trail of the logo. */
private val RoutePattern = listOf(Dash(ROUTE_WIDTH * 2.4f), Gap(ROUTE_WIDTH * 1.8f))
