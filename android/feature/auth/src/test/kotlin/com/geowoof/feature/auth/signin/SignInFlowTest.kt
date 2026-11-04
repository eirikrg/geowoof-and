package com.geowoof.feature.auth.signin

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.geowoof.core.data.credentials.PasswordSaveRequests
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.core.domain.ValidateEmailUseCase
import com.geowoof.core.model.AppError
import com.geowoof.core.model.Outcome
import com.geowoof.core.testing.FakeAuthRepository
import com.geowoof.feature.auth.googleClientId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SignInFlowTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val auth = FakeAuthRepository()
    private var forgotPasswordEmail: String? = null

    private fun showSignIn() {
        val viewModel =
            SignInViewModel(
                SavedStateHandle(),
                auth,
                ValidateEmailUseCase(),
                PasswordSaveRequests(),
                googleClientId(auth),
            )
        composeRule.setContent {
            GeoWoofTheme(dynamicColor = false) {
                val uiState by viewModel.uiState.collectAsState()
                SignInContent(
                    uiState = uiState,
                    email = viewModel.email,
                    password = viewModel.password,
                    onSubmit = viewModel::submit,
                    onForgotPassword = { forgotPasswordEmail = viewModel.email.text.toString() },
                    onSignUp = {},
                    onBack = {},
                    onContinueWithGoogle = null,
                )
            }
        }
    }

    private fun signInButton() = composeRule.onNode(hasText("Sign in") and hasClickAction())

    @Test
    fun signing_in_with_an_email_and_password_calls_the_api() {
        showSignIn()

        composeRule.onNodeWithText("Email").performTextInput("ana@geowoof.dev")
        composeRule.onNodeWithText("Password").performTextInput("GeoWoof2026")
        signInButton().performClick()
        composeRule.waitForIdle()

        assertEquals(listOf("signIn:ana@geowoof.dev"), auth.calls)
    }

    @Test
    fun empty_fields_explain_what_is_missing() {
        showSignIn()

        signInButton().performClick()

        composeRule.onNodeWithText("Enter your email").assertIsDisplayed()
        composeRule.onNodeWithText("Enter your password").assertIsDisplayed()
        assertTrue(auth.calls.isEmpty())
    }

    @Test
    fun wrong_credentials_are_explained() {
        auth.signInResult = Outcome.Failure(AppError.Api(status = 401, code = "auth.invalid_credentials"))
        showSignIn()

        composeRule.onNodeWithText("Email").performTextInput("ana@geowoof.dev")
        composeRule.onNodeWithText("Password").performTextInput("wrong-password1")
        signInButton().performClick()

        composeRule.onNodeWithText("The email or password is incorrect.").assertIsDisplayed()
    }

    @Test
    fun forgot_password_carries_the_typed_email() {
        showSignIn()

        composeRule.onNodeWithText("Email").performTextInput("ana@geowoof.dev")
        composeRule.onNodeWithText("Forgot your password?").performClick()

        assertEquals("ana@geowoof.dev", forgotPasswordEmail)
    }

    @Test
    fun google_is_hidden_when_it_is_unavailable() {
        showSignIn()

        composeRule.onNodeWithText("Continue with Google").assertDoesNotExist()
    }
}
