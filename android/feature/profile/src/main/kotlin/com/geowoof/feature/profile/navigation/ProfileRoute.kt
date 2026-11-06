package com.geowoof.feature.profile.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import androidx.navigation.navOptions
import com.geowoof.core.model.PeopleListKind
import com.geowoof.feature.profile.EditProfileScreen
import com.geowoof.feature.profile.ProfileScreen
import com.geowoof.feature.profile.UserProfileScreen
import com.geowoof.feature.profile.people.PeopleListScreen
import com.geowoof.feature.profile.requests.FollowRequestsScreen
import kotlinx.serialization.Serializable

// The ViewModels read the route arguments from SavedStateHandle by property name.

/** Type-safe route of the profile destination: your own. */
@Serializable
data object ProfileRoute

@Serializable
data object EditProfileRoute

/** The profile of someone else. */
@Serializable
data class UserProfileRoute(val userId: String)

/** The followers, the people followed or the friends of someone. [kind] is a [PeopleListKind]. */
@Serializable
data class PeopleListRoute(val userId: String, val kind: String)

@Serializable
data object FollowRequestsRoute

fun NavController.navigateToProfile(navOptions: NavOptions? = null) = navigate(ProfileRoute, navOptions)

fun NavController.navigateToUserProfile(userId: String, navOptions: NavOptions? = null) =
    navigate(UserProfileRoute(userId), navOptions ?: navOptions { launchSingleTop = true })

fun NavController.navigateToFollowRequests() = navigate(FollowRequestsRoute, navOptions { launchSingleTop = true })

/** Settings, pets, walks and email verification are other features: the app provides how to reach them. */
@Suppress("LongParameterList")
fun NavGraphBuilder.profileScreens(
    navController: NavController,
    onOpenSettings: () -> Unit,
    onOpenOwnWalk: (walkId: String) -> Unit,
    onOpenUserWalk: (walkId: String) -> Unit,
    onOpenPet: (petId: String) -> Unit,
    onAddPet: () -> Unit,
    onVerifyEmail: () -> Unit,
    onOpenPost: (postId: String) -> Unit,
    onCreatePost: () -> Unit,
    onSendMessage: (userId: String) -> Unit,
) {
    val openPeople = { userId: String, kind: PeopleListKind ->
        navController.navigate(PeopleListRoute(userId, kind.name)) { launchSingleTop = true }
    }
    val editProfile = { navController.navigate(EditProfileRoute) { launchSingleTop = true } }
    composable<ProfileRoute> {
        ProfileScreen(
            onOpenSettings = onOpenSettings,
            onEditProfile = editProfile,
            onOpenWalk = onOpenOwnWalk,
            onOpenPet = onOpenPet,
            onAddPet = onAddPet,
            onOpenPeople = openPeople,
            onOpenRequests = { navController.navigate(FollowRequestsRoute) { launchSingleTop = true } },
            onOpenPost = onOpenPost,
            onCreatePost = onCreatePost,
        )
    }
    composable<EditProfileRoute> {
        EditProfileScreen(onBack = { navController.popBackStack() }, onDone = { navController.popBackStack() })
    }
    composable<UserProfileRoute>(deepLinks = listOf(navDeepLink<UserProfileRoute>(basePath = "geowoof://user"))) {
        UserProfileScreen(
            onBack = { navController.popBackStack() },
            onEditProfile = editProfile,
            onOpenWalk = onOpenUserWalk,
            onOpenPeople = openPeople,
            onOpenPost = onOpenPost,
            onVerifyEmail = onVerifyEmail,
            onSendMessage = onSendMessage,
        )
    }
    composable<PeopleListRoute> {
        PeopleListScreen(
            onBack = { navController.popBackStack() },
            onOpenUser = { navController.navigateToUserProfile(it) },
        )
    }
    composable<FollowRequestsRoute>(
        deepLinks = listOf(navDeepLink<FollowRequestsRoute>(basePath = "geowoof://follow-requests")),
    ) {
        FollowRequestsScreen(
            onBack = { navController.popBackStack() },
            onOpenUser = { navController.navigateToUserProfile(it) },
        )
    }
}
