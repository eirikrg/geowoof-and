package com.geowoof.feature.auth

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.feature.auth.username.ChooseUsernameContent
import com.geowoof.feature.auth.username.ChooseUsernameUiState
import com.geowoof.feature.auth.verify.VerifyEmailContent
import com.geowoof.feature.auth.verify.VerifyEmailUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w412dp-h915dp")
class OnboardingFormsTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val calls = mutableListOf<String>()

    private fun showVerifyEmail(isOnboarding: Boolean) {
        composeRule.setContent {
            GeoWoofTheme(dynamicColor = false) {
                VerifyEmailContent(
                    uiState = VerifyEmailUiState(email = "ana@geowoof.dev"),
                    code = TextFieldState(),
                    onVerify = {},
                    onResendCode = {},
                    onBack = if (isOnboarding) null else ({ calls += "back" }),
                    onSkip = if (isOnboarding) ({ calls += "skip" }) else null,
                    onSignOut = if (isOnboarding) ({ calls += "signOut" }) else null,
                )
            }
        }
    }

    @Test
    fun verifying_the_email_is_the_first_step_and_it_can_wait() {
        showVerifyEmail(isOnboarding = true)

        composeRule.onNodeWithContentDescription("Step 1 of 3").assertIsDisplayed()
        composeRule.onNodeWithText("Not now").performClick()
        composeRule.onNodeWithText("Sign out").performClick()

        assertEquals(listOf("skip", "signOut"), calls)
    }

    @Test
    fun verifying_it_later_from_the_settings_is_not_a_step_of_anything() {
        showVerifyEmail(isOnboarding = false)

        composeRule.onNodeWithContentDescription("Step 1 of 3").assertDoesNotExist()
        composeRule.onNodeWithText("Not now").assertDoesNotExist()
        composeRule.onNodeWithText("Sign out").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Back").performClick()

        assertEquals(listOf("back"), calls)
    }

    @Test
    fun the_message_says_where_the_code_went() {
        showVerifyEmail(isOnboarding = true)

        composeRule.onNodeWithText("ana@geowoof.dev", substring = true).assertIsDisplayed()
    }

    @Test
    fun choosing_the_username_is_the_second_step_and_the_whole_row_is_the_switch() {
        composeRule.setContent {
            GeoWoofTheme(dynamicColor = false) {
                ChooseUsernameContent(
                    uiState = ChooseUsernameUiState(),
                    username = TextFieldState(),
                    displayName = TextFieldState(),
                    onPrivateChange = { calls += "private:$it" },
                    onSubmit = {},
                    onSignOut = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Step 2 of 3").assertIsDisplayed()
        composeRule.onNodeWithText("Private profile").performClick()

        assertEquals(listOf("private:true"), calls)
    }
}
