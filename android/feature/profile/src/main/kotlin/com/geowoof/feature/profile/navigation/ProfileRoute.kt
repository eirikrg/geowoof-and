package com.geowoof.feature.profile.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.geowoof.feature.profile.ProfileScreen
import kotlinx.serialization.Serializable

/** Type-safe route of the profile destination. */
@Serializable
data object ProfileRoute

fun NavController.navigateToProfile(navOptions: NavOptions? = null) = navigate(ProfileRoute, navOptions)

/** Settings is another feature: the app provides [onOpenSettings]. */
fun NavGraphBuilder.profileScreen(onOpenSettings: () -> Unit) {
    composable<ProfileRoute> { ProfileScreen(onOpenSettings = onOpenSettings) }
}
