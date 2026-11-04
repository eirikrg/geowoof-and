package com.geowoof.app.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainShellTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun setUp() {
        composeRule.setContent {
            GeoWoofTheme(dynamicColor = false) { MainShell() }
        }
    }

    @Test
    fun shows_the_four_top_level_destinations_with_the_map_selected() {
        navigationItem("Map").assertIsDisplayed().assertIsSelected()
        navigationItem("Community").assertIsDisplayed().assertIsNotSelected()
        navigationItem("Search").assertIsDisplayed().assertIsNotSelected()
        navigationItem("Profile").assertIsDisplayed().assertIsNotSelected()
    }

    @Test
    fun selecting_a_destination_shows_its_screen() {
        navigationItem("Search").performClick()

        navigationItem("Search").assertIsSelected()
        navigationItem("Map").assertIsNotSelected()
        composeRule.onNodeWithText("People and pets").assertIsDisplayed()
    }

    @Test
    fun every_destination_can_be_reached_and_the_map_restored() {
        for (label in listOf("Community", "Profile", "Map")) {
            navigationItem(label).performClick()
            navigationItem(label).assertIsSelected()
        }
    }

    private fun navigationItem(label: String): SemanticsNodeInteraction =
        composeRule.onNode(hasText(label) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected))
}
