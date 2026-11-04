package com.geowoof.feature.auth.navigation

import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.geowoof.core.data.credentials.PasswordSaveRequests
import com.geowoof.core.domain.ValidateEmailUseCase
import com.geowoof.core.domain.ValidatePasswordUseCase
import com.geowoof.core.domain.ValidateVerificationCodeUseCase
import com.geowoof.core.testing.FakeAuthRepository
import com.geowoof.feature.auth.googleClientId
import com.geowoof.feature.auth.password.ForgotPasswordViewModel
import com.geowoof.feature.auth.password.ResetPasswordViewModel
import com.geowoof.feature.auth.signin.SignInViewModel
import kotlinx.serialization.Serializable
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The ViewModels read the route arguments from SavedStateHandle by property name: this checks,
 * with real navigation, that the names and values get there intact.
 */
@RunWith(AndroidJUnit4::class)
class RouteArgumentsTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val auth = FakeAuthRepository()

    @Serializable
    data object StartRoute

    private fun navigateTo(route: Any) {
        composeRule.setContent {
            val navController = rememberNavController()
            NavHost(navController = navController, startDestination = StartRoute) {
                composable<StartRoute> {
                    LaunchedEffect(Unit) { navController.navigate(route) }
                }
                composable<SignInRoute> {
                    val viewModel = viewModel {
                        SignInViewModel(
                            createSavedStateHandle(),
                            auth,
                            ValidateEmailUseCase(),
                            PasswordSaveRequests(),
                            googleClientId(auth),
                        )
                    }
                    val wasReset = viewModel.uiState.collectAsState().value.passwordWasReset
                    Text("email=${viewModel.email.text} reset=$wasReset")
                }
                composable<ForgotPasswordRoute> {
                    val viewModel = viewModel {
                        ForgotPasswordViewModel(createSavedStateHandle(), auth, ValidateEmailUseCase())
                    }
                    Text("email=${viewModel.email.text}")
                }
                composable<ResetPasswordRoute> {
                    val viewModel = viewModel {
                        ResetPasswordViewModel(
                            createSavedStateHandle(),
                            auth,
                            ValidateVerificationCodeUseCase(),
                            ValidatePasswordUseCase(),
                        )
                    }
                    Text("email=${viewModel.uiState.collectAsState().value.email}")
                }
            }
        }
    }

    @Test
    fun sign_in_receives_the_email_and_the_reset_notice() {
        navigateTo(SignInRoute("ana+walks@geowoof.dev", passwordWasReset = true))

        composeRule.onNodeWithText("email=ana+walks@geowoof.dev reset=true").assertIsDisplayed()
    }

    @Test
    fun sign_in_works_with_the_default_arguments() {
        navigateTo(SignInRoute())

        composeRule.onNodeWithText("email= reset=false").assertIsDisplayed()
    }

    @Test
    fun forgot_password_receives_the_email() {
        navigateTo(ForgotPasswordRoute("ana+walks@geowoof.dev"))

        composeRule.onNodeWithText("email=ana+walks@geowoof.dev").assertIsDisplayed()
    }

    @Test
    fun reset_password_receives_the_email() {
        navigateTo(ResetPasswordRoute("ana+walks@geowoof.dev"))

        composeRule.onNodeWithText("email=ana+walks@geowoof.dev").assertIsDisplayed()
    }
}
