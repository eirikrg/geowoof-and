package com.geowoof.core.designsystem.component

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrailProgressTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun talkback_says_the_step_in_words() {
        composeRule.setContent {
            GeoWoofTheme(dynamicColor = false) { TrailProgress(step = 2, total = 3) }
        }

        composeRule.onNodeWithContentDescription("Step 2 of 3").assertIsDisplayed()
    }
}
