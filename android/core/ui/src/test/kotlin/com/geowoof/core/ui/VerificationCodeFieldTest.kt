package com.geowoof.core.ui

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VerificationCodeFieldTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val state = TextFieldState()
    private var submitted = false

    private fun show(supportingText: String? = null) {
        composeRule.setContent {
            GeoWoofTheme(dynamicColor = false) {
                VerificationCodeField(
                    state = state,
                    supportingText = supportingText,
                    isError = supportingText != null,
                    onKeyboardAction = { submitted = true },
                )
            }
        }
    }

    private fun field() = composeRule.onNodeWithContentDescription("Code")

    @Test
    fun it_takes_the_focus_because_it_is_the_only_thing_to_do() {
        show()

        field().assertIsFocused()
    }

    @Test
    fun it_only_takes_digits() {
        show()

        field().performTextInput("12a3")

        assertEquals("123", state.text.toString())
    }

    @Test
    fun it_does_not_go_past_six_digits() {
        show()

        field().performTextInput("123456")
        field().performTextInput("7")

        assertEquals("123456", state.text.toString())
    }

    @Test
    fun the_action_of_the_keyboard_sends_it() {
        show()

        field().performImeAction()

        assertTrue(submitted)
    }

    @Test
    fun a_wrong_code_is_explained_below() {
        show(supportingText = "That code is not right")

        composeRule.onNodeWithText("That code is not right").assertIsDisplayed()
    }
}
