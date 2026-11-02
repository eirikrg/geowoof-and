package com.geowoof.feature.social.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.geowoof.feature.social.CommunityScreen
import kotlinx.serialization.Serializable

/** Type-safe route of the community destination. */
@Serializable
data object CommunityRoute

fun NavController.navigateToCommunity(navOptions: NavOptions? = null) = navigate(CommunityRoute, navOptions)

fun NavGraphBuilder.communityScreen() {
    composable<CommunityRoute> { CommunityScreen() }
}
