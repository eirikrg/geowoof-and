package com.geowoof.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import com.geowoof.feature.map.navigation.MapRoute
import com.geowoof.feature.map.navigation.mapScreen
import com.geowoof.feature.profile.navigation.profileScreen
import com.geowoof.feature.search.navigation.searchScreen
import com.geowoof.feature.settings.navigation.navigateToSettings
import com.geowoof.feature.settings.navigation.settingsScreens
import com.geowoof.feature.social.navigation.communityScreen

// Wires the feature graphs together; features never reference each other directly.

/** Main shell graph: the four top-level destinations plus the screens opened from them. */
@Composable
fun GeoWoofNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(
        navController = navController,
        startDestination = MapRoute,
        modifier = modifier,
    ) {
        mapScreen()
        communityScreen()
        searchScreen()
        profileScreen(onOpenSettings = { navController.navigateToSettings() })
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

/** Signed in without a username: verify the email (optional) and choose the username. */
@Composable
fun OnboardingNavHost(
    emailVerified: Boolean,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    // Chosen once: verifying the email later must not rebuild the graph and reset the back stack.
    val startDestination: Any = remember { if (emailVerified) ChooseUsernameRoute else VerifyEmailRoute }
    NavHost(navController = navController, startDestination = startDestination, modifier = modifier) {
        verifyEmailScreen(
            onDone = { navController.continueToUsername() },
            onSkip = { navController.continueToUsername() },
        )
        chooseUsernameScreen()
    }
}

private fun NavController.continueToUsername() =
    navigateToChooseUsername(navOptions { popUpTo(VerifyEmailRoute) { inclusive = true } })
