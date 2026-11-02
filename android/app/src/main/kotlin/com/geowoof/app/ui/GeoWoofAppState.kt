package com.geowoof.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navOptions
import com.geowoof.app.navigation.TopLevelDestination
import com.geowoof.feature.map.navigation.navigateToMap
import com.geowoof.feature.profile.navigation.navigateToProfile
import com.geowoof.feature.search.navigation.navigateToSearch
import com.geowoof.feature.social.navigation.navigateToCommunity

@Composable
fun rememberGeoWoofAppState(navController: NavHostController = rememberNavController()): GeoWoofAppState =
    remember(navController) { GeoWoofAppState(navController) }

/** Navigation state of the app shell. */
@Stable
class GeoWoofAppState(val navController: NavHostController) {
    val currentDestination: NavDestination?
        @Composable get() = navController.currentBackStackEntryAsState().value?.destination

    /**
     * Each top-level destination keeps its own back stack: switching tabs saves the current
     * stack and restores the target one, and the start destination stays at the bottom.
     */
    fun navigateToTopLevelDestination(destination: TopLevelDestination) {
        val options = navOptions {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
        when (destination) {
            TopLevelDestination.Map -> navController.navigateToMap(options)
            TopLevelDestination.Community -> navController.navigateToCommunity(options)
            TopLevelDestination.Search -> navController.navigateToSearch(options)
            TopLevelDestination.Profile -> navController.navigateToProfile(options)
        }
    }
}

internal fun NavDestination?.isInHierarchyOf(destination: TopLevelDestination): Boolean =
    this?.hierarchy?.any { it.hasRoute(destination.route) } == true
