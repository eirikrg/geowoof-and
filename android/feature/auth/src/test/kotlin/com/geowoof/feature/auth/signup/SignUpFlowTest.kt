package com.geowoof.feature.auth.signup

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.geowoof.core.data.credentials.PasswordSaveRequests
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.core.domain.ValidateDisplayNameUseCase
import com.geowoof.core.domain.ValidateEmailUseCase
import com.geowoof.core.domain.ValidatePasswordUseCase
import com.geowoof.core.testing.FakeAuthRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w412dp-h915dp")
class SignUpFlowTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val auth = FakeAuthRepository()

    private fun showSignUp() {
        val viewModel = SignUpViewModel(
            auth,
            ValidateDisplayNameUseCase(),
            ValidateEmailUseCase(),
            ValidatePasswordUseCase(),
            PasswordSaveRequests(),
        )
        composeRule.setContent {
            GeoWoofTheme(dynamicColor = false) {
                val uiState by viewModel.uiState.collectAsState()
                SignUpContent(
                    uiState = uiState,
                    displayName = viewModel.displayName,
                    email = viewModel.email,
                    password = viewModel.password,
                    onSubmit = viewModel::submit,
                    onSignIn = {},
                    onBack = {},
                )
            }
        }
    }

    private fun createAccountButton() = composeRule.onNode(hasText("Create account") and hasClickAction())

    @Test
    fun shows_the_password_rules_before_the_first_attempt() {
        showSignUp()

        composeRule.onNodeWithText("At least 8 characters, with letters and numbers").assertIsDisplayed()
    }

    @Test
    fun creating_an_account_calls_the_api() {
        showSignUp()

        composeRule.onNodeWithText("Your name").performTextInput("Ana")
        composeRule.onNodeWithText("Email").performTextInput("ana@geowoof.dev")
        composeRule.onNodeWithText("Password").performTextInput("GeoWoof2026")
        createAccountButton().performClick()
        composeRule.waitForIdle()

        assertEquals(listOf("register:ana@geowoof.dev:Ana"), auth.calls)
    }

    @Test
    fun a_weak_password_is_explained() {
        showSignUp()

        composeRule.onNodeWithText("Your name").performTextInput("Ana")
        composeRule.onNodeWithText("Email").performTextInput("ana@geowoof.dev")
        composeRule.onNodeWithText("Password").performTextInput("short1")
        createAccountButton().performClick()

        composeRule.onNodeWithText("Use at least 8 characters").assertIsDisplayed()
        assertTrue(auth.calls.isEmpty())
    }
}
