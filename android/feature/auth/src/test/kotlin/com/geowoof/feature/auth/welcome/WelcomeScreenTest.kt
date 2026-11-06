package com.geowoof.feature.auth.welcome

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class WelcomeScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun show() {
        composeRule.setContent {
            GeoWoofTheme(dynamicColor = false) {
                WelcomeContent(
                    uiState = WelcomeUiState(googleClientId = "id"),
                    onSignUp = {},
                    onSignIn = {},
                    onContinueWithGoogle = {},
                )
            }
        }
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp")
    fun on_a_phone_the_buttons_are_at_the_bottom_within_reach() {
        show()

        val bottom = composeRule.onNodeWithText("Continue with Google").getUnclippedBoundsInRoot().bottom

        assertTrue("The last button ends at $bottom", bottom > 800.dp)
    }

    @Test
    @Config(qualifiers = "w320dp-h480dp")
    fun on_a_small_screen_they_can_be_reached_by_scrolling() {
        show()

        composeRule.onNodeWithText("Continue with Google").performScrollTo().assertIsDisplayed()
    }
}
