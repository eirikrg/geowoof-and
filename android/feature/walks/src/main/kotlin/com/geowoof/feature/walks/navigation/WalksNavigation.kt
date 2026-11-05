package com.geowoof.feature.walks.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.navOptions
import com.geowoof.feature.walks.WalksScreen
import com.geowoof.feature.walks.detail.WalkDetailScreen
import kotlinx.serialization.Serializable

// The ViewModels read the route arguments from SavedStateHandle by property name.

/** The walks of the signed-in person, or those of one of their pets. */
@Serializable
data class WalksRoute(val petId: String? = null)

@Serializable
data class WalkDetailRoute(val walkId: String)

fun NavController.navigateToWalks(petId: String? = null, navOptions: NavOptions? = null) =
    navigate(WalksRoute(petId), navOptions ?: navOptions { launchSingleTop = true })

fun NavGraphBuilder.walkScreens(navController: NavController) {
    composable<WalksRoute> {
        WalksScreen(
            onBack = { navController.popBackStack() },
            onOpenWalk = { walkId -> navController.navigate(WalkDetailRoute(walkId)) },
        )
    }
    composable<WalkDetailRoute> {
        WalkDetailScreen(
            onBack = { navController.popBackStack() },
            onDone = { navController.popBackStack() },
        )
    }
}
