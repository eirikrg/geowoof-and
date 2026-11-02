package com.geowoof.feature.profile

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun shows_its_title_and_empty_state() {
        composeRule.setContent { GeoWoofTheme(dynamicColor = false) { ProfileScreen() } }

        composeRule.onNodeWithText("Profile").assertIsDisplayed()
        composeRule.onNodeWithText("Your profile").assertIsDisplayed()
    }
}
