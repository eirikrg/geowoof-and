package com.geowoof.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import com.geowoof.feature.map.navigation.MapRoute
import com.geowoof.feature.map.navigation.mapScreen
import com.geowoof.feature.profile.navigation.profileScreen
import com.geowoof.feature.search.navigation.searchScreen
import com.geowoof.feature.social.navigation.communityScreen

/** Wires the feature graphs together; features never reference each other directly. */
@Composable
fun GeoWoofNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(
        navController = navController,
        startDestination = MapRoute,
        modifier = modifier,
    ) {
        mapScreen()
        communityScreen()
        searchScreen()
        profileScreen()
    }
}
