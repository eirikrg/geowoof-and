package com.geowoof.feature.profile

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var settingsOpened = 0

    @Test
    fun shows_its_title_and_empty_state() {
        composeRule.setContent { GeoWoofTheme(dynamicColor = false) { ProfileScreen(onOpenSettings = {}) } }

        composeRule.onNodeWithText("Profile").assertIsDisplayed()
        composeRule.onNodeWithText("Your profile").assertIsDisplayed()
    }

    @Test
    fun opens_the_settings() {
        composeRule.setContent {
            GeoWoofTheme(dynamicColor = false) { ProfileScreen(onOpenSettings = { settingsOpened++ }) }
        }

        composeRule.onNodeWithContentDescription("Settings").performClick()

        assertEquals(1, settingsOpened)
    }
}
