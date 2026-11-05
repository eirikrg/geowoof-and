package com.geowoof.feature.profile.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.geowoof.feature.profile.EditProfileScreen
import com.geowoof.feature.profile.ProfileScreen
import kotlinx.serialization.Serializable

/** Type-safe route of the profile destination. */
@Serializable
data object ProfileRoute

@Serializable
data object EditProfileRoute

fun NavController.navigateToProfile(navOptions: NavOptions? = null) = navigate(ProfileRoute, navOptions)

/** Settings and pets are other features: the app provides how to reach them. */
fun NavGraphBuilder.profileScreen(
    navController: NavController,
    onOpenSettings: () -> Unit,
    onOpenWalks: () -> Unit,
    onOpenPet: (petId: String) -> Unit,
    onAddPet: () -> Unit,
) {
    composable<ProfileRoute> {
        ProfileScreen(
            onOpenSettings = onOpenSettings,
            onEditProfile = { navController.navigate(EditProfileRoute) { launchSingleTop = true } },
            onOpenWalks = onOpenWalks,
            onOpenPet = onOpenPet,
            onAddPet = onAddPet,
        )
    }
    composable<EditProfileRoute> {
        EditProfileScreen(onBack = { navController.popBackStack() }, onDone = { navController.popBackStack() })
    }
}
