package com.geowoof.feature.notifications.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import androidx.navigation.navOptions
import com.geowoof.feature.notifications.NotificationsActions
import com.geowoof.feature.notifications.NotificationsScreen
import kotlinx.serialization.Serializable

@Serializable
data object NotificationsRoute

fun NavController.navigateToNotifications() = navigate(NotificationsRoute, navOptions { launchSingleTop = true })

/** Where a notification leads belongs to other features: the app provides how to reach it. */
fun NavGraphBuilder.notificationsScreen(actions: NotificationsActions) {
    composable<NotificationsRoute>(
        deepLinks = listOf(navDeepLink<NotificationsRoute>(basePath = "geowoof://notifications")),
    ) {
        NotificationsScreen(actions = actions)
    }
}
