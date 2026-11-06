package com.geowoof.core.designsystem.component

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
class IllustratedDialogTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val calls = mutableListOf<String>()

    private fun show(message: String = "So that the route can be drawn.") {
        composeRule.setContent {
            GeoWoofTheme(dynamicColor = false) {
                IllustratedDialog(
                    illustration = GeoWoofIllustrations.PermissionLocation,
                    title = "Your location",
                    message = message,
                    confirmLabel = "Continue",
                    onConfirm = { calls += "confirm" },
                    dismissLabel = "Not now",
                    onDismiss = { calls += "dismiss" },
                )
            }
        }
    }

    @Test
    fun each_button_says_what_it_says() {
        show()

        composeRule.onNodeWithText("Continue").performClick()
        composeRule.onNodeWithText("Not now").performClick()

        assertEquals(listOf("confirm", "dismiss"), calls)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "w320dp-h480dp", fontScale = 2f)
    fun with_a_big_font_on_a_small_screen_the_buttons_can_still_be_reached() {
        show(message = List(LONG_MESSAGE_WORDS) { "word" }.joinToString(" "))

        composeRule.onNodeWithText("Your location").assertIsDisplayed()
        composeRule.onNodeWithText("Continue").assertIsDisplayed()
        composeRule.onNodeWithText("Not now").assertIsDisplayed()
    }

    private companion object {
        const val LONG_MESSAGE_WORDS = 120
    }
}
