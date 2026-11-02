package com.geowoof.feature.map.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.geowoof.feature.map.MapScreen
import kotlinx.serialization.Serializable

/** Type-safe route of the map destination (the app's start destination). */
@Serializable
data object MapRoute

fun NavController.navigateToMap(navOptions: NavOptions? = null) = navigate(MapRoute, navOptions)

fun NavGraphBuilder.mapScreen() {
    composable<MapRoute> { MapScreen() }
}
