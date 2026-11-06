package com.geowoof.core.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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

@RunWith(AndroidJUnit4::class)
class RequestNotificationPermissionTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var asks by mutableStateOf(true)
    private var answers = 0

    private fun show() {
        composeRule.setContent {
            GeoWoofTheme(dynamicColor = false) {
                val inbox = Inbox(
                    unreadMessages = 0,
                    unreadNotifications = 0,
                    onOpenMessages = {},
                    onOpenNotifications = {},
                    asksForNotifications = asks,
                    onAskedForNotifications = {
                        asks = false
                        answers++
                    },
                )
                CompositionLocalProvider(LocalInbox provides inbox) { RequestNotificationPermission() }
            }
        }
    }

    @Test
    fun it_says_what_for_before_the_system_asks() {
        show()

        composeRule.onNodeWithText(TITLE).assertIsDisplayed()
    }

    @Test
    fun not_now_is_also_an_answer_and_it_does_not_ask_again() {
        show()

        composeRule.onNodeWithText("Not now").performClick()

        assertEquals(1, answers)
        composeRule.onNodeWithText(TITLE).assertDoesNotExist()
    }

    @Test
    fun turning_them_on_is_an_answer_too() {
        show()

        composeRule.onNodeWithText("Turn on").performClick()

        assertEquals(1, answers)
        composeRule.onNodeWithText(TITLE).assertDoesNotExist()
    }

    @Test
    fun whoever_has_already_answered_is_not_asked() {
        asks = false

        show()

        composeRule.onNodeWithText(TITLE).assertDoesNotExist()
    }

    private companion object {
        const val TITLE = "Know when someone writes to you"
    }
}
