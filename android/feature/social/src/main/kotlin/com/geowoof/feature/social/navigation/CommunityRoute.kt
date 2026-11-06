package com.geowoof.feature.social.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import androidx.navigation.navOptions
import com.geowoof.feature.social.CommunityScreen
import com.geowoof.feature.social.create.CreatePostScreen
import com.geowoof.feature.social.post.PostDetailScreen
import kotlinx.serialization.Serializable

/** Type-safe route of the community destination. */
@Serializable
data object CommunityRoute

/** The ViewModel reads the argument from SavedStateHandle by property name. */
@Serializable
data class PostRoute(val postId: String)

@Serializable
data object CreatePostRoute

fun NavController.navigateToCommunity(navOptions: NavOptions? = null) = navigate(CommunityRoute, navOptions)

fun NavController.navigateToPost(postId: String, navOptions: NavOptions? = null) =
    navigate(PostRoute(postId), navOptions ?: navOptions { launchSingleTop = true })

fun NavController.navigateToCreatePost(navOptions: NavOptions? = null) =
    navigate(CreatePostRoute, navOptions ?: navOptions { launchSingleTop = true })

/** The profile of a person is another feature: the app provides how to reach it. */
fun NavGraphBuilder.socialScreens(navController: NavController, onOpenUser: (userId: String) -> Unit) {
    composable<CommunityRoute> {
        CommunityScreen(
            onCreatePost = { navController.navigateToCreatePost() },
            onOpenPost = { navController.navigateToPost(it) },
            onOpenUser = onOpenUser,
        )
    }
    composable<PostRoute>(deepLinks = listOf(navDeepLink<PostRoute>(basePath = "geowoof://post"))) {
        PostDetailScreen(onBack = { navController.popBackStack() }, onOpenUser = onOpenUser)
    }
    composable<CreatePostRoute> {
        CreatePostScreen(onBack = { navController.popBackStack() }, onDone = { navController.popBackStack() })
    }
}
