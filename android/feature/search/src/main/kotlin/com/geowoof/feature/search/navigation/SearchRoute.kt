package com.geowoof.feature.search.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.geowoof.feature.search.SearchScreen
import kotlinx.serialization.Serializable

/** Type-safe route of the search destination. */
@Serializable
data object SearchRoute

fun NavController.navigateToSearch(navOptions: NavOptions? = null) = navigate(SearchRoute, navOptions)

/** The profile of a person is another feature: the app provides how to reach it. */
fun NavGraphBuilder.searchScreen(onOpenUser: (userId: String) -> Unit) {
    composable<SearchRoute> { SearchScreen(onOpenUser = onOpenUser) }
}
