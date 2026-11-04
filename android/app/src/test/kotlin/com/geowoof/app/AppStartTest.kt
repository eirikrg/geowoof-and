package com.geowoof.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Starts the real activity with the real Hilt graph. Checks the wiring that unit tests replace with
 * fakes: the session gate, hiltViewModel in each destination and the signed-out navigation.
 *
 * It needs no backend and must pass with or without local keys. With a Google client ID in
 * secrets.properties the welcome screen asks the configured API whether Google is available, so
 * nothing here can depend on that button; SignInFlowTest and GetGoogleClientIdUseCaseTest cover it.
 */
@RunWith(AndroidJUnit4::class)
class AppStartTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun button(text: String) = composeRule.onNode(hasText(text) and hasClickAction())

    private fun waitForWelcome() {
        composeRule.waitUntil(WAIT_MILLIS) {
            composeRule.onAllNodes(hasText("Create account") and hasClickAction()).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun a_new_install_shows_the_welcome_screen() {
        waitForWelcome()

        button("Create account").assertIsDisplayed()
        button("Sign in").assertIsDisplayed()
    }

    @Test
    fun the_signed_out_forms_can_be_reached() {
        waitForWelcome()

        button("Sign in").performClick()
        composeRule.onNodeWithText("Forgot your password?").performClick()
        composeRule.onNodeWithText("Reset your password").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.onNodeWithText("New to GeoWoof? Create an account").performClick()
        composeRule.onNodeWithText("Create your account").assertIsDisplayed()
    }

    private companion object {
        const val WAIT_MILLIS = 10_000L
    }
}
