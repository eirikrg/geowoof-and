package com.geowoof.feature.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import com.geowoof.core.model.ActiveWalk
import com.geowoof.core.model.GeoPoint
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.CameraPositionState

/** Keeps the camera on the walk in progress, and takes it where it is asked. Both can come before there is a map. */
@Composable
internal fun MapCameraEffects(
    camera: CameraPositionState,
    walk: ActiveWalk?,
    target: CameraTarget?,
    onReachTarget: () -> Unit,
) {
    val lastPoint = walk?.lastPoint
    LaunchedEffect(lastPoint) {
        lastPoint?.let { camera.goTo(it.position, WALK_ZOOM) }
    }
    val currentOnReachTarget by rememberUpdatedState(onReachTarget)
    LaunchedEffect(target) {
        target?.let {
            if (it.isJump) {
                camera.position = cameraAt(it.position, PERSON_ZOOM)
            } else {
                camera.goTo(it.position, PERSON_ZOOM)
            }
            currentOnReachTarget()
        }
    }
}

/**
 * Until Google Maps has started, CameraUpdateFactory throws and the app closes. A walk in progress gets here when the
 * app opens, and the location button can while the map loads. Then it only says where the map will start.
 */
private suspend fun CameraPositionState.goTo(place: GeoPoint, zoom: Float) {
    if (projection == null) {
        position = cameraAt(place, zoom)
    } else {
        animate(CameraUpdateFactory.newLatLngZoom(LatLng(place.latitude, place.longitude), zoom))
    }
}

private fun cameraAt(place: GeoPoint, zoom: Float) =
    CameraPosition.fromLatLngZoom(LatLng(place.latitude, place.longitude), zoom)

internal const val WALK_ZOOM = 17f
internal const val PERSON_ZOOM = 15f
