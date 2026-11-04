package com.geowoof.feature.auth.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.geowoof.feature.auth.password.ForgotPasswordScreen
import com.geowoof.feature.auth.password.ResetPasswordScreen
import com.geowoof.feature.auth.signin.SignInScreen
import com.geowoof.feature.auth.signup.SignUpScreen
import com.geowoof.feature.auth.welcome.WelcomeScreen
import kotlinx.serialization.Serializable

// Signed-out flow. ViewModels read the route arguments from SavedStateHandle by property name.

@Serializable
data object WelcomeRoute

/** [email] pre-fills the form; [passwordWasReset] tells the user that the new password is ready. */
@Serializable
data class SignInRoute(val email: String = "", val passwordWasReset: Boolean = false)

@Serializable
data object SignUpRoute

@Serializable
data class ForgotPasswordRoute(val email: String = "")

@Serializable
data class ResetPasswordRoute(val email: String)

/**
 * Welcome, sign-in, sign-up and password recovery, starting at [WelcomeRoute]. Signing in changes
 * the session, and the app replaces this graph with the onboarding or the main shell.
 */
fun NavGraphBuilder.authScreens(navController: NavController) {
    composable<WelcomeRoute> {
        WelcomeScreen(
            onSignUp = { navController.navigate(SignUpRoute) { launchSingleTop = true } },
            onSignIn = { navController.navigate(SignInRoute()) { launchSingleTop = true } },
        )
    }
    composable<SignInRoute> {
        SignInScreen(
            onBack = { navController.popBackStack() },
            onForgotPassword = { email ->
                navController.navigate(ForgotPasswordRoute(email)) { launchSingleTop = true }
            },
            onSignUp = { navController.navigate(SignUpRoute) { popUpTo(WelcomeRoute) } },
        )
    }
    composable<SignUpRoute> {
        SignUpScreen(
            onBack = { navController.popBackStack() },
            onSignIn = { email -> navController.navigate(SignInRoute(email)) { popUpTo(WelcomeRoute) } },
        )
    }
    composable<ForgotPasswordRoute> {
        ForgotPasswordScreen(
            onBack = { navController.popBackStack() },
            onEnterCode = { email -> navController.navigate(ResetPasswordRoute(email)) { launchSingleTop = true } },
        )
    }
    composable<ResetPasswordRoute> {
        ResetPasswordScreen(
            onBack = { navController.popBackStack() },
            onPasswordReset = { email ->
                navController.navigate(SignInRoute(email, passwordWasReset = true)) { popUpTo(WelcomeRoute) }
            },
        )
    }
}
