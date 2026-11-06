package com.geowoof.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navOptions
import com.geowoof.feature.auth.navigation.ChooseUsernameRoute
import com.geowoof.feature.auth.navigation.VerifyEmailRoute
import com.geowoof.feature.auth.navigation.WelcomeRoute
import com.geowoof.feature.auth.navigation.authScreens
import com.geowoof.feature.auth.navigation.chooseUsernameScreen
import com.geowoof.feature.auth.navigation.navigateToChooseUsername
import com.geowoof.feature.auth.navigation.navigateToVerifyEmail
import com.geowoof.feature.auth.navigation.verifyEmailScreen
import com.geowoof.feature.chat.navigation.chatScreens
import com.geowoof.feature.chat.navigation.navigateToChatWith
import com.geowoof.feature.map.navigation.MapRoute
import com.geowoof.feature.map.navigation.mapScreens
import com.geowoof.feature.notifications.NotificationsActions
import com.geowoof.feature.notifications.navigation.notificationsScreen
import com.geowoof.feature.pets.navigation.firstPetScreen
import com.geowoof.feature.pets.navigation.navigateToFirstPet
import com.geowoof.feature.pets.navigation.navigateToPet
import com.geowoof.feature.pets.navigation.navigateToPetEditor
import com.geowoof.feature.pets.navigation.petScreens
import com.geowoof.feature.profile.navigation.navigateToFollowRequests
import com.geowoof.feature.profile.navigation.navigateToUserProfile
import com.geowoof.feature.profile.navigation.profileScreens
import com.geowoof.feature.search.navigation.searchScreen
import com.geowoof.feature.settings.navigation.navigateToSettings
import com.geowoof.feature.settings.navigation.settingsScreens
import com.geowoof.feature.social.navigation.navigateToCreatePost
import com.geowoof.feature.social.navigation.navigateToPost
import com.geowoof.feature.social.navigation.socialScreens
import com.geowoof.feature.walks.navigation.navigateToUserWalk
import com.geowoof.feature.walks.navigation.navigateToWalkDetail
import com.geowoof.feature.walks.navigation.navigateToWalks
import com.geowoof.feature.walks.navigation.walkScreens

// Wires the feature graphs together; features never reference each other directly.

/** Main shell graph: the four top-level destinations plus the screens opened from them. */
@Composable
fun GeoWoofNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(
        navController = navController,
        startDestination = MapRoute,
        modifier = modifier,
    ) {
        mapScreens(
            navController = navController,
            onAddPet = { navController.navigateToPetEditor() },
            onOpenUser = { userId -> navController.navigateToUserProfile(userId) },
        )
        socialScreens(navController, onOpenUser = { userId -> navController.navigateToUserProfile(userId) })
        searchScreen(
            onOpenUser = { userId -> navController.navigateToUserProfile(userId) },
            onOpenPost = { postId -> navController.navigateToPost(postId) },
        )
        profileScreens(
            navController = navController,
            onOpenSettings = { navController.navigateToSettings() },
            onOpenOwnWalk = { walkId -> navController.navigateToWalkDetail(walkId) },
            onOpenUserWalk = { walkId -> navController.navigateToUserWalk(walkId) },
            onOpenPet = { petId -> navController.navigateToPet(petId) },
            onAddPet = { navController.navigateToPetEditor() },
            onVerifyEmail = { navController.navigateToVerifyEmail() },
            onOpenPost = { postId -> navController.navigateToPost(postId) },
            onCreatePost = { navController.navigateToCreatePost() },
            onSendMessage = { userId -> navController.navigateToChatWith(userId) },
        )
        chatScreens(navController, onOpenUser = { userId -> navController.navigateToUserProfile(userId) })
        notificationsScreen(
            NotificationsActions(
                onBack = { navController.popBackStack() },
                onOpenPost = { postId -> navController.navigateToPost(postId) },
                onOpenUser = { userId -> navController.navigateToUserProfile(userId) },
                onOpenRequests = { navController.navigateToFollowRequests() },
            ),
        )
        petScreens(navController, onOpenWalks = { petId -> navController.navigateToWalks(petId) })
        walkScreens(navController)
        settingsScreens(navController, onVerifyEmail = { navController.navigateToVerifyEmail() })
        verifyEmailScreen(
            onDone = { navController.popBackStack() },
            onBack = { navController.popBackStack() },
        )
    }
}

/** Signed out: welcome, sign in, sign up and password recovery. */
@Composable
fun AuthNavHost(modifier: Modifier = Modifier, navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = WelcomeRoute, modifier = modifier) {
        authScreens(navController)
    }
}

/** Signed in without a username: verify the email (optional), choose the username and add a pet (optional). */
@Composable
fun OnboardingNavHost(
    emailVerified: Boolean,
    offerFirstPet: Boolean,
    onFinishFirstPetStep: () -> Unit,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    // Chosen once: verifying the email later must not rebuild the graph and reset the back stack.
    val startDestination: Any = remember { if (emailVerified) ChooseUsernameRoute else VerifyEmailRoute }
    val currentOnFinish by rememberUpdatedState(onFinishFirstPetStep)
    NavHost(navController = navController, startDestination = startDestination, modifier = modifier) {
        verifyEmailScreen(
            onDone = { navController.continueToUsername() },
            onSkip = { navController.continueToUsername() },
        )
        chooseUsernameScreen()
        firstPetScreen(onDone = { currentOnFinish() })
    }
    LaunchedEffect(offerFirstPet) {
        if (offerFirstPet) {
            navController.navigateToFirstPet(navOptions { popUpTo(navController.graph.id) { inclusive = true } })
        }
    }
}

private fun NavController.continueToUsername() =
    navigateToChooseUsername(navOptions { popUpTo(VerifyEmailRoute) { inclusive = true } })
