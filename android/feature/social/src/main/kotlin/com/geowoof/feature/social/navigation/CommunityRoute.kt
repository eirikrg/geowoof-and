package com.geowoof.feature.social.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.navOptions
import com.geowoof.feature.social.CommunityScreen
import com.geowoof.feature.social.friends.FriendRequestsScreen
import com.geowoof.feature.social.friends.FriendsScreen
import com.geowoof.feature.social.profile.UserProfileScreen
import kotlinx.serialization.Serializable

/** Type-safe route of the community destination. */
@Serializable
data object CommunityRoute

@Serializable
data object FriendsRoute

@Serializable
data object FriendRequestsRoute

/** The profile of someone else. The ViewModel reads the argument from SavedStateHandle by property name. */
@Serializable
data class UserProfileRoute(val userId: String)

fun NavController.navigateToCommunity(navOptions: NavOptions? = null) = navigate(CommunityRoute, navOptions)

fun NavController.navigateToUserProfile(userId: String, navOptions: NavOptions? = null) =
    navigate(UserProfileRoute(userId), navOptions ?: navOptions { launchSingleTop = true })

/** Walks and email verification are other features: the app provides how to reach them. */
fun NavGraphBuilder.socialScreens(
    navController: NavController,
    onOpenWalk: (walkId: String) -> Unit,
    onVerifyEmail: () -> Unit,
) {
    composable<CommunityRoute> {
        CommunityScreen(
            onOpenRequests = { navController.navigate(FriendRequestsRoute) { launchSingleTop = true } },
            onOpenFriends = { navController.navigate(FriendsRoute) { launchSingleTop = true } },
        )
    }
    composable<FriendsRoute> {
        FriendsScreen(
            onBack = { navController.popBackStack() },
            onOpenUser = { navController.navigateToUserProfile(it) },
        )
    }
    composable<FriendRequestsRoute> {
        FriendRequestsScreen(
            onBack = { navController.popBackStack() },
            onOpenUser = { navController.navigateToUserProfile(it) },
        )
    }
    composable<UserProfileRoute> {
        UserProfileScreen(
            onBack = { navController.popBackStack() },
            onOpenWalk = onOpenWalk,
            onVerifyEmail = onVerifyEmail,
        )
    }
}
