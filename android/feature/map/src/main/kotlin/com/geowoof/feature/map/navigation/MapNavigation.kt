package com.geowoof.feature.map.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.geowoof.feature.map.MapScreen
import com.geowoof.feature.map.summary.WalkSummaryScreen
import kotlinx.serialization.Serializable

/** Type-safe route of the map destination (the app's start destination). */
@Serializable
data object MapRoute

/** What to do with the walk that has just finished. There is only one, so it needs no arguments. */
@Serializable
data object WalkSummaryRoute

fun NavController.navigateToMap(navOptions: NavOptions? = null) = navigate(MapRoute, navOptions)

/** The pets are another feature: the app provides how to reach them. */
fun NavGraphBuilder.mapScreens(navController: NavController, onAddPet: () -> Unit) {
    composable<MapRoute> {
        MapScreen(
            onOpenSummary = { navController.navigate(WalkSummaryRoute) { launchSingleTop = true } },
            onAddPet = onAddPet,
        )
    }
    composable<WalkSummaryRoute> {
        WalkSummaryScreen(
            onBack = { navController.popBackStack() },
            onDone = { navController.popBackStack() },
        )
    }
}
