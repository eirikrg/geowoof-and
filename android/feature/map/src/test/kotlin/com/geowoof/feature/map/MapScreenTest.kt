package com.geowoof.feature.map

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.core.model.ActiveWalkStatus
import com.geowoof.core.model.AppError
import com.geowoof.core.model.LiveVisibility
import com.geowoof.core.model.Relationship
import com.geowoof.core.model.RelationshipStatus
import com.geowoof.core.testing.TestPets
import com.geowoof.core.testing.TestWalkers
import com.geowoof.core.testing.TestWalks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MapScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val actions = mutableListOf<String>()

    private val mapActions = MapActions(
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
        onChangeVisibility = { actions += "changeVisibility" },
        onChooseVisibility = { actions += "choose:$it" },
        onDismissVisibility = { actions += "dismissVisibility" },
        onDismissError = { actions += "dismissError" },
        onAskForLocation = { actions += "askForLocation" },
        onBackToWalk = { actions += "backToWalk" },
    )

    private fun show(uiState: MapUiState, live: LiveWalkersUiState = LiveWalkersUiState()) {
        composeRule.setContent {
            GeoWoofTheme(dynamicColor = false) {
                MapContent(
                    uiState = uiState,
                    actions = mapActions,
                    live = live,
                    liveActions = LiveWalkersActions(
                        onOpenList = { actions += "openList" },
                        onDismissList = { actions += "dismissList" },
                        onWalkerClick = { actions += "walker:$it" },
                        onDismissWalker = { actions += "dismissWalker" },
                        onOpenProfile = { actions += "profile:$it" },
                        onFollow = { actions += "follow" },
                        onConfirmFollowBack = { actions += "confirmFollowBack" },
                        onDismissFollowBack = { actions += "dismissFollowBack" },
                        onDismissError = { actions += "dismissLiveError" },
                    ),
                    map = {},
                )
            }
        }
    }

    private fun click(text: String) = composeRule.onNodeWithText(text).performClick()

    private val lookingAround = MapUiState(
        walk = TestWalks.activeWalk(),
        stats = TestWalks.stats(distanceM = 3120),
        isLookingAround = true,
    )

    @Test
    fun the_first_time_it_says_why_it_wants_the_location_and_then_asks_the_system() {
        show(MapUiState(prompt = WalkPrompt.OpenAtLocation))

        composeRule.onNodeWithText("See the map around you").assertIsDisplayed()
        click("Continue")

        assertEquals(listOf("askForLocation"), actions)
    }

    @Test
    fun the_location_can_be_left_for_later() {
        show(MapUiState(prompt = WalkPrompt.OpenAtLocation))

        click("Not now")

        assertEquals(listOf("dismissPrompt"), actions)
    }

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
    fun while_looking_around_it_shows_what_matters_with_the_actions_at_hand() {
        show(
            MapUiState(
                walk = TestWalks.activeWalk(),
                stats = TestWalks.stats(distanceM = 3120),
                isLookingAround = true,
            ),
        )

        composeRule.onNodeWithText("3.12 km").assertIsDisplayed()
        composeRule.onNodeWithText("Time").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Pause").performClick()
        composeRule.onNodeWithContentDescription("Finish").performClick()

        assertEquals(listOf("pause", "finish"), actions)
    }

    @Test
    fun the_small_panel_leaves_the_map_free_on_both_sides() {
        show(MapUiState(walk = TestWalks.activeWalk(), stats = TestWalks.stats(), isLookingAround = true))

        val panel = composeRule.onNodeWithContentDescription("Pause").onParent().getUnclippedBoundsInRoot()
        val window = composeRule.onRoot().getUnclippedBoundsInRoot()

        assertTrue("${panel.width} of ${window.width}", panel.width < window.width)
    }

    @Test
    fun touching_the_small_panel_brings_back_the_whole_one() {
        show(lookingAround)

        // On the numbers: the center of the capsule is on its buttons.
        composeRule.onNodeWithText("3.12 km").performTouchInput { click(Offset(x = 48.dp.toPx(), y = height / 2f)) }

        assertEquals(listOf("backToWalk"), actions)
    }

    @Test
    fun the_small_panel_tells_talkback_what_touching_it_does() {
        show(lookingAround)

        val label = composeRule.onNodeWithText("3.12 km").fetchSemanticsNode()
            .config[SemanticsActions.OnClick].label

        assertEquals("Show the whole panel", label)
    }

    @Test
    fun a_paused_walk_in_the_small_panel_can_be_resumed() {
        show(
            MapUiState(
                walk = TestWalks.activeWalk(status = ActiveWalkStatus.Paused),
                stats = TestWalks.stats(),
                isLookingAround = true,
            ),
        )

        composeRule.onNodeWithText("Walk paused").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Resume").performClick()

        assertEquals(listOf("resume"), actions)
    }

    @Test
    fun it_goes_from_one_panel_to_the_other_when_the_person_starts_and_stops_looking_around() {
        var uiState by mutableStateOf(MapUiState(walk = TestWalks.activeWalk(), stats = TestWalks.stats()))
        composeRule.setContent {
            GeoWoofTheme(dynamicColor = false) {
                WalkPanel(uiState = uiState, actions = mapActions)
            }
        }
        composeRule.onNodeWithText("Pause").assertIsDisplayed()

        uiState = uiState.copy(isLookingAround = true)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Pause").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Pause").assertIsDisplayed()

        uiState = uiState.copy(isLookingAround = false)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Pause").assertIsDisplayed()
    }

    /** It can open by itself as a finger arrives, and where the small one has Pause, the whole one has Finish. */
    @Test
    fun while_the_panel_grows_a_touch_that_was_for_the_small_one_does_nothing() {
        var uiState by mutableStateOf(lookingAround)
        composeRule.setContent {
            GeoWoofTheme(dynamicColor = false) {
                WalkPanel(uiState = uiState, actions = mapActions)
            }
        }
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false

        uiState = uiState.copy(isLookingAround = false)
        Snapshot.sendApplyNotifications()
        composeRule.mainClock.advanceTimeBy(ANIMATION_STARTED_MILLIS)
        composeRule.onNodeWithText("Finish").performClick()
        composeRule.mainClock.advanceTimeByFrame()
        assertEquals(emptyList<String>(), actions)

        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Finish").performClick()
        assertEquals(listOf("finish"), actions)
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

    @Test
    fun it_asks_to_get_closer_when_the_map_is_too_far_away() {
        show(MapUiState(), LiveWalkersUiState(isTooWide = true))

        composeRule.onNodeWithText("Zoom in to see who is walking").assertIsDisplayed()
    }

    @Test
    fun with_nobody_around_it_says_nothing() {
        show(MapUiState(), LiveWalkersUiState())

        composeRule.onNodeWithText("Zoom in to see who is walking").assertDoesNotExist()
        composeRule.onNodeWithText("Start walk").assertIsDisplayed()
    }

    @Test
    fun who_is_around_can_be_reached_without_the_markers() {
        val walkers = listOf(TestWalkers.walker(), TestWalkers.walker("ana-id", "ana", "Ana"))
        show(MapUiState(), LiveWalkersUiState(walkers = walkers))

        click("2 people walking here")

        assertEquals(listOf("openList"), actions)
    }

    @Test
    fun one_person_around_is_said_in_singular() {
        show(MapUiState(), LiveWalkersUiState(walkers = listOf(TestWalkers.walker())))

        composeRule.onNodeWithText("1 person walking here").assertIsDisplayed()
    }

    @Test
    fun the_list_shows_each_walker_with_their_pets_and_opens_them() {
        show(MapUiState(), LiveWalkersUiState(walkers = listOf(TestWalkers.walker()), isListOpen = true))

        composeRule.onNodeWithText("Walking here").assertIsDisplayed()
        composeRule.onNodeWithText("Mishi").assertIsDisplayed()
        click("Bea")

        assertEquals(listOf("walker:bea-id"), actions)
    }

    @Test
    fun the_sheet_of_a_walker_shows_them_and_leads_to_their_profile() {
        val bea = TestWalkers.walker()
        show(MapUiState(), LiveWalkersUiState(walkers = listOf(bea), selected = bea))

        composeRule.onNodeWithText("Bea").assertIsDisplayed()
        composeRule.onNodeWithText("@bea").assertIsDisplayed()
        composeRule.onNodeWithText("Mishi").assertIsDisplayed()
        click("View profile")

        assertEquals(listOf("profile:bea-id"), actions)
    }

    @Test
    fun without_knowing_the_relationship_it_does_not_offer_to_follow() {
        val bea = TestWalkers.walker()
        show(MapUiState(), LiveWalkersUiState(selected = bea))

        composeRule.onNodeWithText("Follow").assertDoesNotExist()
    }

    @Test
    fun it_offers_to_follow_someone_it_does_not_follow() {
        val bea = TestWalkers.walker()
        show(MapUiState(), LiveWalkersUiState(selected = bea, relationship = Relationship(RelationshipStatus.None)))

        click("Follow")

        assertEquals(listOf("follow"), actions)
    }

    @Test
    fun it_says_follow_back_to_someone_who_follows_them() {
        val bea = TestWalkers.walker()
        val relationship = Relationship(RelationshipStatus.None, followsYou = true)
        show(MapUiState(), LiveWalkersUiState(selected = bea, relationship = relationship))

        composeRule.onNodeWithText("Follow back").assertIsDisplayed()
    }

    @Test
    fun what_is_already_settled_is_not_a_button_to_press() {
        val bea = TestWalkers.walker()
        show(MapUiState(), LiveWalkersUiState(selected = bea, relationship = Relationship(RelationshipStatus.Friends)))

        composeRule.onNodeWithText("Friends").assertIsNotEnabled()
    }

    @Test
    fun following_back_explains_what_it_means_first() {
        val bea = TestWalkers.walker()
        show(
            MapUiState(),
            LiveWalkersUiState(
                selected = bea,
                relationship = Relationship(RelationshipStatus.None, followsYou = true),
                isConfirmingFollowBack = true,
            ),
        )

        composeRule.onNodeWithText("Follow Bea back?").assertIsDisplayed()
        composeRule.onAllNodesWithText("Follow back").onLast().performClick()

        assertEquals(listOf("confirmFollowBack"), actions)
    }

    @Test
    fun the_first_time_nothing_is_marked_and_it_cannot_continue_without_choosing() {
        show(MapUiState(isChoosingVisibility = true))

        composeRule.onNodeWithText("Who can see you while you walk?").assertIsDisplayed()
        composeRule.onNodeWithText("Everyone nearby").assertIsNotSelected()
        composeRule.onNodeWithText("Only friends").assertIsNotSelected()
        composeRule.onNodeWithText("Nobody").assertIsNotSelected()
        composeRule.onNodeWithText("Continue").performScrollTo().assertIsNotEnabled()
        assertTrue(actions.isEmpty())
    }

    @Test
    fun choosing_takes_a_second_step() {
        show(MapUiState(isChoosingVisibility = true))

        composeRule.onNodeWithText("Only friends").performClick()
        assertTrue(actions.isEmpty())

        composeRule.onNodeWithText("Continue").performScrollTo().performClick()
        assertEquals(listOf("choose:FriendsOnly"), actions)
    }

    @Test
    fun changing_it_shows_what_is_chosen_now() {
        show(MapUiState(isChoosingVisibility = true, liveVisibility = LiveVisibility.Nobody))

        composeRule.onNodeWithText("Nobody").assertIsSelected()
        composeRule.onNodeWithText("Continue").performScrollTo().assertIsEnabled()
    }

    @Test
    fun it_says_what_is_being_told_and_how_far_the_start_is_hidden() {
        show(MapUiState(isChoosingVisibility = true, hideStartDistanceM = 500))

        composeRule.onNode(hasText("sends your position every few seconds", substring = true)).assertExists()
        composeRule.onNode(hasText("500 m from where you started", substring = true))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun it_says_so_if_the_start_is_not_hidden() {
        show(MapUiState(isChoosingVisibility = true, hideStartDistanceM = 0))

        composeRule.onNode(hasText("shared from the start of the walk", substring = true))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun before_a_walk_it_shows_who_will_see_them_and_can_be_changed() {
        show(
            MapUiState(
                isPickingPets = true,
                pets = listOf(TestPets.pet()),
                liveVisibility = LiveVisibility.FriendsOnly,
            ),
        )

        click("Who sees you: Only friends")

        assertEquals(listOf("changeVisibility"), actions)
    }

    @Test
    fun without_having_chosen_it_is_asked_later_and_not_in_the_pets() {
        show(MapUiState(isPickingPets = true, pets = listOf(TestPets.pet()), liveVisibility = null))

        composeRule.onNode(hasText("Who sees you", substring = true)).assertDoesNotExist()
        composeRule.onNodeWithText("Your position is not shared").assertDoesNotExist()
    }

    @Test
    fun during_a_walk_it_shows_who_sees_them_and_can_be_changed() {
        show(MapUiState(walk = TestWalks.activeWalk(), liveVisibility = LiveVisibility.Everyone))

        click("Who sees you: Everyone nearby")

        assertEquals(listOf("changeVisibility"), actions)
    }

    @Test
    fun during_a_walk_it_says_so_if_the_position_is_not_shared() {
        show(MapUiState(walk = TestWalks.activeWalk(), liveVisibility = LiveVisibility.Nobody))

        composeRule.onNodeWithText("Your position is not shared").assertIsDisplayed()
    }

    @Test
    fun a_paused_walk_shares_nothing_so_it_does_not_say_it_does() {
        val paused = TestWalks.activeWalk(status = ActiveWalkStatus.Paused)
        show(MapUiState(walk = paused, liveVisibility = LiveVisibility.Everyone))

        composeRule.onNode(hasText("Who sees you", substring = true)).assertDoesNotExist()
    }

    @Test
    fun an_error_is_said_once() {
        show(MapUiState(error = AppError.Network))

        composeRule.onNodeWithText("No connection. Check your internet and try again.").assertIsDisplayed()
    }

    private companion object {
        const val ANIMATION_STARTED_MILLIS = 100L
    }
}
