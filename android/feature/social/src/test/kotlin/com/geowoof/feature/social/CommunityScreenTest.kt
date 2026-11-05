package com.geowoof.feature.social

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CommunityScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun it_announces_the_feed() {
        composeRule.setContent { GeoWoofTheme(dynamicColor = false) { CommunityScreen() } }

        composeRule.onNodeWithText("Your community feed").assertIsDisplayed()
    }
}
