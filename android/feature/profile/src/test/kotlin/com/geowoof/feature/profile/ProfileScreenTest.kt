package com.geowoof.feature.profile

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.core.testing.TestPets
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val actions = mutableListOf<String>()

    private fun show(uiState: ProfileUiState) {
        composeRule.setContent {
            GeoWoofTheme(dynamicColor = false) {
                ProfileContent(
                    uiState = uiState,
                    onRefresh = { actions += "refresh" },
                    onDismissError = { actions += "errorShown" },
                    onOpenSettings = { actions += "settings" },
                    onEditProfile = { actions += "edit" },
                    onOpenWalks = { actions += "walks" },
                    onOpenPet = { actions += "pet:$it" },
                    onAddPet = { actions += "add" },
                )
            }
        }
    }

    @Test
    fun it_opens_the_walks_of_the_person() {
        show(ProfileUiState(profile = TestPets.profile()))

        composeRule.onNodeWithText("My walks").performClick()

        assertEquals(listOf("walks"), actions)
    }

    @Test
    fun shows_the_profile_and_its_pets() {
        show(
            ProfileUiState(
                profile = TestPets.profile(displayName = "Ana López", bio = "Paseo a diario", city = "Madrid"),
                pets = listOf(TestPets.pet(), TestPets.pet(id = "toby", name = "Toby", breed = null)),
            ),
        )

        composeRule.onNodeWithText("Ana López").assertIsDisplayed()
        composeRule.onNodeWithText("@ana").assertIsDisplayed()
        composeRule.onNodeWithText("Madrid").assertIsDisplayed()
        composeRule.onNodeWithText("Paseo a diario").assertIsDisplayed()
        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Border collie"))
        composeRule.onNodeWithText("Border collie").assertIsDisplayed()

        // Without a breed, the species describes it.
        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Dog"))
        composeRule.onNodeWithText("Dog").assertIsDisplayed()

        composeRule.onNodeWithText("Toby").performClick()
        assertEquals(listOf("pet:toby"), actions)
    }

    @Test
    fun invites_to_add_the_first_pet() {
        show(ProfileUiState(profile = TestPets.profile()))

        composeRule.onNodeWithText("No pets yet").assertIsDisplayed()
        composeRule.onNodeWithText("Add pet").performClick()

        assertEquals(listOf("add"), actions)
    }

    @Test
    fun opens_the_settings_and_the_editor() {
        show(ProfileUiState(profile = TestPets.profile()))

        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.onNodeWithContentDescription("Edit profile").performClick()

        assertEquals(listOf("settings", "edit"), actions)
    }
}
