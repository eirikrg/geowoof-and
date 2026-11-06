package com.geowoof.feature.map

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.geowoof.core.designsystem.component.GeoWoofPin
import com.geowoof.core.designsystem.icon.GeoWoofIcons
import com.geowoof.core.model.LiveWalker
import com.geowoof.core.ui.RemoteAvatar
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.clustering.ClusterItem
import com.google.maps.android.clustering.view.DefaultClusterRenderer
import com.google.maps.android.compose.GoogleMapComposable
import com.google.maps.android.compose.MapsComposeExperimentalApi
import com.google.maps.android.compose.clustering.Clustering
import com.google.maps.android.compose.clustering.rememberClusterManager
import com.google.maps.android.compose.clustering.rememberClusterRenderer

/**
 * Two items are the same walker wherever they are, so that the renderer moves their marker
 * instead of drawing a new one each time a position arrives.
 */
private class WalkerItem(val walker: LiveWalker) : ClusterItem {
    override val position get() = LatLng(walker.position.latitude, walker.position.longitude)

    override val title get() = walker.displayName

    override val snippet: String? = null

    override val zIndex: Float? = null

    override fun equals(other: Any?) = other is WalkerItem && other.walker.userId == walker.userId

    override fun hashCode() = walker.userId.hashCode()
}

/** The walkers on the map, together when they are too close to tell apart. */
@OptIn(MapsComposeExperimentalApi::class)
@Composable
@GoogleMapComposable
internal fun LiveWalkersLayer(
    walkers: List<LiveWalker>,
    onWalkerClick: (String) -> Unit,
    onClusterClick: (LatLng) -> Unit,
) {
    val clusterManager = rememberClusterManager<WalkerItem>() ?: return
    val renderer = rememberClusterRenderer(
        clusterContent = { cluster -> WalkersBadge(cluster.size) },
        clusterItemContent = { item -> WalkerMarker(item.walker) },
        clusterManager = clusterManager,
    )
    val currentOnWalkerClick by rememberUpdatedState(onWalkerClick)
    val currentOnClusterClick by rememberUpdatedState(onClusterClick)
    SideEffect {
        if (renderer != null && clusterManager.renderer != renderer) {
            // By default only from four on: two on the same spot would hide each other.
            (renderer as? DefaultClusterRenderer<*>)?.minClusterSize = MIN_CLUSTER_SIZE
            clusterManager.renderer = renderer
        }
        clusterManager.algorithm.maxDistanceBetweenClusteredItems = CLUSTER_DISTANCE_DP
        clusterManager.setOnClusterItemClickListener { item ->
            currentOnWalkerClick(item.walker.userId)
            true
        }
        clusterManager.setOnClusterClickListener { cluster ->
            currentOnClusterClick(cluster.position)
            true
        }
    }
    Clustering(items = emptyList(), clusterManager = clusterManager)
    // Not through the items above: they ignore a list that is equal, and a walker who moves is equal.
    LaunchedEffect(clusterManager, walkers) {
        clusterManager.clearItems()
        clusterManager.addItems(walkers.map(::WalkerItem))
        clusterManager.cluster()
    }
}

/** The head of the logo with the pet in its face. It is drawn on a bitmap, which cannot hold a hardware image. */
@Composable
private fun WalkerMarker(walker: LiveWalker) {
    GeoWoofPin(width = MarkerWidth) { faceSize ->
        RemoteAvatar(
            image = walker.pets.firstOrNull()?.avatar,
            fallbackIcon = GeoWoofIcons.Pets,
            contentDescription = null,
            size = faceSize,
            allowHardware = false,
        )
    }
}

@Composable
private fun WalkersBadge(count: Int) {
    GeoWoofPin(width = MarkerWidth) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = BrandFace,
        )
    }
}

private const val MIN_CLUSTER_SIZE = 2

/** Closer than this, on screen, their markers touch. */
private const val CLUSTER_DISTANCE_DP = 52
private val MarkerWidth = CLUSTER_DISTANCE_DP.dp

/** The face of the pin is always white, also in the dark theme. */
private val BrandFace = Color(0xFF214233)
