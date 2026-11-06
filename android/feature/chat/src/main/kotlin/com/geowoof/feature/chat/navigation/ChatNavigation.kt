package com.geowoof.feature.chat.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import androidx.navigation.navOptions
import com.geowoof.feature.chat.ConversationScreen
import com.geowoof.feature.chat.ConversationsScreen
import com.geowoof.feature.chat.NewConversationScreen
import kotlinx.serialization.Serializable

/** The list of conversations. */
@Serializable
data object ConversationsRoute

/**
 * A conversation, known by its id or by the person it is with. The ViewModel reads the arguments
 * from SavedStateHandle by property name.
 */
@Serializable
data class ConversationRoute(val conversationId: String? = null, val userId: String? = null)

/** Choosing a friend to talk to. */
@Serializable
data object NewConversationRoute

fun NavController.navigateToConversations() = navigate(ConversationsRoute, navOptions { launchSingleTop = true })

fun NavController.navigateToChatWith(userId: String) = navigate(
    ConversationRoute(userId = userId),
    navOptions {
        launchSingleTop =
            true
    },
)

/** The profile of a person is another feature: the app provides how to reach it. */
fun NavGraphBuilder.chatScreens(navController: NavController, onOpenUser: (userId: String) -> Unit) {
    composable<ConversationsRoute> {
        ConversationsScreen(
            onBack = { navController.popBackStack() },
            onOpenConversation = { id ->
                navController.navigate(ConversationRoute(conversationId = id)) { launchSingleTop = true }
            },
            onNewConversation = { navController.navigate(NewConversationRoute) { launchSingleTop = true } },
        )
    }
    composable<ConversationRoute>(deepLinks = listOf(navDeepLink<ConversationRoute>(basePath = "geowoof://chat"))) {
        ConversationScreen(onBack = { navController.popBackStack() }, onOpenUser = onOpenUser)
    }
    composable<NewConversationRoute> {
        NewConversationScreen(
            onBack = { navController.popBackStack() },
            onOpenChat = { userId ->
                navController.navigateToChatWith(userId)
            },
        )
    }
}
