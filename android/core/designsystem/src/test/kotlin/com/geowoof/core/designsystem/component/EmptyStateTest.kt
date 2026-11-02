package com.geowoof.core.designsystem.component

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.geowoof.core.designsystem.icon.GeoWoofIcons
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EmptyStateTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun title_and_message_are_read_as_one_element() {
        composeRule.setContent {
            GeoWoofTheme(dynamicColor = false) {
                EmptyState(icon = GeoWoofIcons.Pets, title = "No pets yet", message = "Add your first pet.")
            }
        }

        // With merged semantics both texts belong to the same node.
        composeRule.onNodeWithText("No pets yet").assertIsDisplayed()
        composeRule.onNodeWithText("Add your first pet.").assertIsDisplayed()
        composeRule.onNodeWithText("No pets yet").assert(hasText("Add your first pet."))
    }
}
