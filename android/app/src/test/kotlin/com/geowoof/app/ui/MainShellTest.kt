package com.geowoof.app.ui

import androidx.compose.material3.Button
import androidx.compose.material3.Text
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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.feature.map.navigation.MapRoute
import com.geowoof.feature.profile.navigation.ProfileRoute
import com.geowoof.feature.search.navigation.SearchRoute
import com.geowoof.feature.social.navigation.CommunityRoute
import kotlinx.serialization.Serializable
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The shell around the screens: a graph of plain texts stands in for the real one, because its
 * screens get their ViewModels from Hilt. AppStartTest covers the real wiring.
 */
@RunWith(AndroidJUnit4::class)
class MainShellTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Serializable
    data object NestedRoute

    @Before
    fun setUp() {
        composeRule.setContent {
            GeoWoofTheme(dynamicColor = false) {
                MainShell { navController ->
                    NavHost(navController = navController, startDestination = MapRoute) {
                        composable<MapRoute> { Text("map screen") }
                        composable<CommunityRoute> { Text("community screen") }
                        composable<SearchRoute> { Text("search screen") }
                        composable<ProfileRoute> {
                            Button(onClick = { navController.navigate(NestedRoute) }) { Text("open nested") }
                        }
                        composable<NestedRoute> { Text("nested screen") }
                    }
                }
            }
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
        composeRule.onNodeWithText("search screen").assertIsDisplayed()
    }

    @Test
    fun every_destination_can_be_reached_and_the_map_restored() {
        for (label in listOf("Community", "Profile", "Map")) {
            navigationItem(label).performClick()
            navigationItem(label).assertIsSelected()
        }
    }

    @Test
    fun a_screen_opened_from_a_destination_uses_the_whole_window() {
        navigationItem("Profile").performClick()

        composeRule.onNodeWithText("open nested").performClick()

        composeRule.onNodeWithText("nested screen").assertIsDisplayed()
        navigationItem("Map").assertDoesNotExist()
    }

    private fun navigationItem(label: String): SemanticsNodeInteraction =
        composeRule.onNode(hasText(label) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected))
}
