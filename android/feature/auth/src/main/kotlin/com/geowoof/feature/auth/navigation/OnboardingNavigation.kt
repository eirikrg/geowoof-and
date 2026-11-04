package com.geowoof.feature.auth.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.geowoof.feature.auth.username.ChooseUsernameScreen
import com.geowoof.feature.auth.verify.VerifyEmailScreen
import kotlinx.serialization.Serializable

@Serializable
data object VerifyEmailRoute

@Serializable
data object ChooseUsernameRoute

fun NavController.navigateToVerifyEmail(navOptions: NavOptions? = null) = navigate(VerifyEmailRoute, navOptions)

fun NavController.navigateToChooseUsername(navOptions: NavOptions? = null) = navigate(ChooseUsernameRoute, navOptions)

/**
 * Email verification with the code sent at sign-up. Without [onBack] it is an onboarding step,
 * which offers [onSkip] and signing out instead of going back.
 */
fun NavGraphBuilder.verifyEmailScreen(onDone: () -> Unit, onBack: (() -> Unit)? = null, onSkip: (() -> Unit)? = null) {
    composable<VerifyEmailRoute> {
        VerifyEmailScreen(onDone = onDone, onBack = onBack, onSkip = onSkip)
    }
}

/**
 * Last onboarding step. Choosing a username ends the onboarding: the session changes and the app
 * shows the main shell.
 */
fun NavGraphBuilder.chooseUsernameScreen() {
    composable<ChooseUsernameRoute> { ChooseUsernameScreen() }
}
