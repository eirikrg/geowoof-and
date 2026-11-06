package com.geowoof.core.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
class InboxActionsTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun show(unreadMessages: Int = 0, unreadNotifications: Int = 0) {
        val inbox = Inbox(
            unreadMessages = unreadMessages,
            unreadNotifications = unreadNotifications,
            onOpenMessages = {},
            onOpenNotifications = {},
        )
        composeRule.setContent {
            GeoWoofTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalInbox provides inbox) { InboxActions() }
            }
        }
    }

    @Test
    fun talkback_hears_how_many_are_waiting() {
        show(unreadMessages = 2, unreadNotifications = 1)

        composeRule.onNodeWithContentDescription("2 unread messages").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("1 new notification").assertIsDisplayed()
    }

    @Test
    fun over_the_map_there_are_only_the_messages() {
        val inbox = Inbox(
            unreadMessages = 2,
            unreadNotifications = 1,
            onOpenMessages = {},
            onOpenNotifications = {},
        )
        composeRule.setContent {
            GeoWoofTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalInbox provides inbox) { FloatingMessagesAction() }
            }
        }

        composeRule.onNodeWithContentDescription("2 unread messages").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("1 new notification").assertDoesNotExist()
    }

    /**
     * The circle of the button cuts whatever goes out of it, and a count that grows with the font does.
     * It needs the native graphics: with the default ones, the height of a text does not depend on its size.
     */
    @Test
    @Config(fontScale = 2f)
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun with_a_very_large_font_the_count_stays_small_enough_to_fit_in_its_button() {
        show(unreadMessages = 2)

        val count = composeRule.onNodeWithText("2", useUnmergedTree = true).fetchSemanticsNode()
        val heightInDp = count.size.height / count.layoutInfo.density.density

        assertTrue("The count is $heightInDp dp high", heightInDp <= MAX_COUNT_HEIGHT_DP)
    }

    private companion object {
        const val MAX_COUNT_HEIGHT_DP = 20f
    }
}
