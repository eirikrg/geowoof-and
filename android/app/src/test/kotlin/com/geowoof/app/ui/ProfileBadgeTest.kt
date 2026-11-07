package com.geowoof.app.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileBadgeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun show(pendingRequests: Int, unreadNotifications: Int) {
        composeRule.setContent {
            GeoWoofTheme(dynamicColor = false) { ProfileBadge(pendingRequests, unreadNotifications) }
        }
    }

    @Test
    fun new_notifications_are_told_in_the_tab_of_the_profile() {
        show(pendingRequests = 0, unreadNotifications = 3)

        composeRule.onNodeWithText("3", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("3 new notifications", useUnmergedTree = true).assertExists()
    }

    /** A request to follow you is also a notification: adding them up, a single request would say 2. */
    @Test
    fun a_request_that_is_also_a_notification_does_not_count_twice() {
        show(pendingRequests = 1, unreadNotifications = 1)

        composeRule.onNodeWithText("1", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("2", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun talkback_hears_each_one_by_its_name() {
        show(pendingRequests = 2, unreadNotifications = 1)

        val description = composeRule
            .onNodeWithContentDescription("follow requests", substring = true, useUnmergedTree = true)
            .fetchSemanticsNode()
            .config[SemanticsProperties.ContentDescription]
            .single()
        assertEquals("2 follow requests, 1 new notification", description)
    }

    @Test
    fun with_nothing_waiting_there_is_no_badge() {
        show(pendingRequests = 0, unreadNotifications = 0)

        composeRule.onNodeWithText("0", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun a_very_large_number_does_not_grow_the_badge() {
        show(pendingRequests = 0, unreadNotifications = 150)

        composeRule.onNodeWithText("99+", useUnmergedTree = true).assertIsDisplayed()
    }
}
