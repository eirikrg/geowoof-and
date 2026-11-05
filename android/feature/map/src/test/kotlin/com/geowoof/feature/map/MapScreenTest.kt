package com.geowoof.feature.map

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.core.model.ActiveWalkStatus
import com.geowoof.core.testing.TestPets
import com.geowoof.core.testing.TestWalks
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MapScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val actions = mutableListOf<String>()

    private fun show(uiState: MapUiState) {
        composeRule.setContent {
            GeoWoofTheme(dynamicColor = false) {
                MapContent(
                    uiState = uiState,
                    actions = MapActions(
                        onStart = { actions += "start" },
                        onDismissPetPicker = { actions += "dismissPets" },
                        onTogglePet = { actions += "toggle:$it" },
                        onConfirmPets = { actions += "confirm:$it" },
                        onAddPet = { actions += "addPet" },
                        onPause = { actions += "pause" },
                        onResume = { actions += "resume" },
                        onFinish = { actions += "finish" },
                        onCancel = { actions += "cancel" },
                        onReview = { actions += "review" },
                        onRequestPermissions = { actions += "permissions" },
                        onOpenAppSettings = { actions += "appSettings" },
                        onOpenLocationSettings = { actions += "locationSettings" },
                        onDismissPrompt = { actions += "dismissPrompt" },
                    ),
                    map = {},
                )
            }
        }
    }

    private fun click(text: String) = composeRule.onNodeWithText(text).performClick()

    @Test
    fun without_a_walk_it_offers_to_start_one() {
        show(MapUiState())

        click("Start walk")

        assertEquals(listOf("start"), actions)
    }

    @Test
    fun while_looking_for_a_signal_it_can_be_cancelled() {
        show(MapUiState(walk = TestWalks.activeWalk(status = ActiveWalkStatus.Preparing, segments = emptyList())))

        composeRule.onNodeWithText("Looking for a GPS signal").assertIsDisplayed()
        click("Cancel")

        assertEquals(listOf("cancel"), actions)
    }

    @Test
    fun while_recording_it_shows_the_numbers_and_can_pause_or_finish() {
        show(MapUiState(walk = TestWalks.activeWalk(), stats = TestWalks.stats(distanceM = 3120)))

        composeRule.onNodeWithText("3.12 km").assertIsDisplayed()
        click("Pause")
        click("Finish")

        assertEquals(listOf("pause", "finish"), actions)
    }

    @Test
    fun a_paused_walk_can_be_resumed_or_finished() {
        show(MapUiState(walk = TestWalks.activeWalk(status = ActiveWalkStatus.Paused), stats = TestWalks.stats()))

        composeRule.onNodeWithText("Walk paused").assertIsDisplayed()
        click("Resume")

        assertEquals(listOf("resume"), actions)
    }

    @Test
    fun an_interrupted_walk_offers_to_continue_or_finish() {
        show(MapUiState(walk = TestWalks.activeWalk(status = ActiveWalkStatus.Interrupted)))

        composeRule.onNodeWithText("The walk was interrupted").assertIsDisplayed()
        click("Continue")
        click("Finish")

        assertEquals(listOf("resume", "finish"), actions)
    }

    @Test
    fun a_finished_walk_that_was_not_saved_can_still_be_reviewed() {
        show(MapUiState(walk = TestWalks.activeWalk(status = ActiveWalkStatus.Finished)))

        click("Review and save")

        assertEquals(listOf("review"), actions)
    }

    @Test
    fun with_several_pets_at_least_one_must_be_chosen() {
        val pets = listOf(TestPets.pet(id = "luna", name = "Luna"), TestPets.pet(id = "toby", name = "Toby"))
        show(MapUiState(pets = pets, isPickingPets = true))

        composeRule.onNodeWithText("Start").assertIsNotEnabled()
        click("Toby")

        assertEquals(listOf("toggle:toby"), actions)
    }

    @Test
    fun the_chosen_pets_can_start_the_walk() {
        val pets = listOf(TestPets.pet(id = "luna", name = "Luna"), TestPets.pet(id = "toby", name = "Toby"))
        show(MapUiState(pets = pets, isPickingPets = true, selectedPetIds = setOf("luna")))

        composeRule.onNodeWithText("Start").assertIsEnabled()
        click("Start")

        assertEquals(listOf("confirm:null"), actions)
    }

    @Test
    fun with_a_single_pet_it_starts_directly() {
        show(MapUiState(pets = listOf(TestPets.pet(id = "luna", name = "Luna")), isPickingPets = true))

        click("Start")

        assertEquals(listOf("confirm:luna"), actions)
    }

    @Test
    fun without_pets_it_offers_to_add_one() {
        show(MapUiState(isPickingPets = true))

        click("Add a pet")

        assertEquals(listOf("addPet"), actions)
    }

    @Test
    fun the_permission_is_explained_before_the_system_asks() {
        show(MapUiState(prompt = WalkPrompt.PermissionRationale))

        composeRule.onNodeWithText("Your location during walks").assertIsDisplayed()
        click("Continue")

        assertEquals(listOf("permissions"), actions)
    }

    @Test
    fun an_approximate_location_sends_to_the_settings_of_the_app() {
        show(MapUiState(prompt = WalkPrompt.ApproximateLocation))

        composeRule.onNodeWithText("A precise location is needed").assertIsDisplayed()
        click("Open settings")

        assertEquals(listOf("appSettings"), actions)
    }

    @Test
    fun location_turned_off_sends_to_the_settings_of_the_device() {
        show(MapUiState(prompt = WalkPrompt.LocationDisabled))

        click("Open settings")

        assertEquals(listOf("locationSettings"), actions)
    }
}
