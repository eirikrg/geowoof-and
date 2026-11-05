package com.geowoof.feature.settings.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.geowoof.feature.settings.SettingsScreen
import com.geowoof.feature.settings.account.ChangePasswordScreen
import com.geowoof.feature.settings.account.DeleteAccountScreen
import com.geowoof.feature.settings.blocks.BlockedUsersScreen
import kotlinx.serialization.Serializable

@Serializable
data object SettingsRoute

@Serializable
data object ChangePasswordRoute

@Serializable
data object DeleteAccountRoute

@Serializable
data object BlockedUsersRoute

fun NavController.navigateToSettings(navOptions: NavOptions? = null) = navigate(SettingsRoute, navOptions)

/**
 * Settings and its account screens. Email verification belongs to another feature, so the app
 * provides [onVerifyEmail]. Signing out or deleting the account changes the session, and the app
 * goes back to the welcome screen.
 */
fun NavGraphBuilder.settingsScreens(navController: NavController, onVerifyEmail: () -> Unit) {
    composable<SettingsRoute> {
        SettingsScreen(
            onBack = { navController.popBackStack() },
            onVerifyEmail = onVerifyEmail,
            onChangePassword = { navController.navigate(ChangePasswordRoute) { launchSingleTop = true } },
            onOpenBlockedUsers = { navController.navigate(BlockedUsersRoute) { launchSingleTop = true } },
            onDeleteAccount = { navController.navigate(DeleteAccountRoute) { launchSingleTop = true } },
        )
    }
    composable<ChangePasswordRoute> {
        ChangePasswordScreen(
            onBack = { navController.popBackStack() },
            onDone = { navController.popBackStack() },
        )
    }
    composable<DeleteAccountRoute> {
        DeleteAccountScreen(onBack = { navController.popBackStack() })
    }
    composable<BlockedUsersRoute> {
        BlockedUsersScreen(onBack = { navController.popBackStack() })
    }
}
