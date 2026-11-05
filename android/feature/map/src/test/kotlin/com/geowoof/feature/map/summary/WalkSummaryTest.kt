package com.geowoof.feature.map.summary

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.core.domain.walks.ComputeWalkStatsUseCase
import com.geowoof.core.domain.walks.SaveWalkUseCase
import com.geowoof.core.model.ActiveWalkStatus
import com.geowoof.core.model.AppError
import com.geowoof.core.model.Outcome
import com.geowoof.core.model.WalkVisibility
import com.geowoof.core.testing.FakeActiveWalkRepository
import com.geowoof.core.testing.FakePetsRepository
import com.geowoof.core.testing.FakeWalksRepository
import com.geowoof.core.testing.MainDispatcherRule
import com.geowoof.core.testing.TestPets
import com.geowoof.core.testing.TestWalks
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock

@RunWith(AndroidJUnit4::class)
class WalkSummaryTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val composeRule = createComposeRule()

    private val finished = TestWalks.activeWalk(
        status = ActiveWalkStatus.Finished,
        segments = listOf(TestWalks.segment(11)),
        endedAt = TestWalks.START.plusSeconds(600),
    )
    private val active = FakeActiveWalkRepository(finished)
    private val saved = FakeWalksRepository()
    private val pets = FakePetsRepository(listOf(TestPets.pet(id = "luna", name = "Luna")))
    private val viewModel by lazy {
        val computeStats = ComputeWalkStatsUseCase()
        WalkSummaryViewModel(active, pets, SaveWalkUseCase(saved, computeStats, Clock.systemUTC()), computeStats)
    }

    private val state get() = viewModel.uiState.value

    private fun TestScope.subscribe() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
    }

    @Test
    fun it_shows_the_numbers_and_the_pets_of_the_walk() = runTest {
        subscribe()

        assertEquals(111, state.stats?.distanceM)
        assertEquals(600, state.stats?.durationS)
        assertEquals(listOf("Luna"), state.petNames)
        assertTrue(state.canSave)
    }

    @Test
    fun saving_keeps_the_note_the_visibility_and_the_photos() = runTest {
        subscribe()
        viewModel.noteText.setTextAndPlaceCursorAtEnd(" Por el Retiro ")
        Snapshot.sendApplyNotifications()
        viewModel.onVisibilityChange(WalkVisibility.OnlyMe)
        viewModel.onAddPhoto("content://one")
        viewModel.onAddPhoto("content://two")
        viewModel.onRemovePhoto("content://one")

        viewModel.onSaveClick()

        val walk = saved.saved.single()
        assertEquals("Por el Retiro", walk.note)
        assertEquals(WalkVisibility.OnlyMe, walk.visibility)
        assertEquals(listOf("content://two"), walk.photoUris)
        assertTrue(state.isDone)
    }

    @Test
    fun a_photo_cannot_be_added_twice_or_beyond_the_limit() = runTest {
        subscribe()

        repeat(12) { viewModel.onAddPhoto("content://$it") }
        viewModel.onAddPhoto("content://0")

        assertEquals(10, state.photoUris.size)
    }

    @Test
    fun a_note_that_is_too_long_is_not_saved() = runTest {
        subscribe()
        viewModel.noteText.setTextAndPlaceCursorAtEnd("a".repeat(1001))
        Snapshot.sendApplyNotifications()

        viewModel.onSaveClick()

        assertTrue(state.isNoteTooLong)
        assertEquals(0, saved.saved.size)
    }

    @Test
    fun a_failed_save_keeps_everything_to_try_again() = runTest {
        saved.saveResult = Outcome.Failure(AppError.Api(422, "media.invalid_image"))
        subscribe()

        viewModel.onSaveClick()

        assertEquals(AppError.Api(422, "media.invalid_image"), state.error)
        assertFalse(state.isDone)
        assertFalse(state.isSaving)
    }

    @Test
    fun discarding_asks_first_and_then_removes_the_walk() = runTest {
        subscribe()

        viewModel.onRequestDiscard()
        assertTrue(state.isConfirmingDiscard)
        viewModel.onDismissDiscard()
        assertFalse(state.isConfirmingDiscard)
        assertEquals(finished, active.value)

        viewModel.onRequestDiscard()
        viewModel.onConfirmDiscard()

        assertNull(active.value)
        assertTrue(state.isDone)
        assertEquals(0, saved.saved.size)
    }

    @Test
    fun a_walk_too_short_can_only_be_discarded() = runTest {
        active.set(finished.copy(segments = listOf(TestWalks.segment(1))))
        subscribe()

        viewModel.onSaveClick()

        assertFalse(state.canSave)
        assertEquals(0, saved.saved.size)
    }

    private fun show(uiState: WalkSummaryUiState, actions: MutableList<String>) {
        composeRule.setContent {
            GeoWoofTheme(dynamicColor = false) {
                WalkSummaryContent(
                    uiState = uiState,
                    noteText = TextFieldState(),
                    actions = WalkSummaryActions(
                        onBack = { actions += "back" },
                        onVisibilityChange = { actions += "visibility:$it" },
                        onAddPhoto = { actions += "photo" },
                        onRemovePhoto = { actions += "remove:$it" },
                        onSave = { actions += "save" },
                        onRequestDiscard = { actions += "discard" },
                        onConfirmDiscard = { actions += "confirmDiscard" },
                        onDismissDiscard = { actions += "dismissDiscard" },
                        onErrorShown = { actions += "errorShown" },
                    ),
                )
            }
        }
    }

    @Test
    fun the_screen_can_save_and_choose_who_sees_the_walk() {
        val actions = mutableListOf<String>()
        show(WalkSummaryUiState(walk = finished, stats = TestWalks.stats(distanceM = 3120)), actions)

        composeRule.onNodeWithText("3.12 km").assertIsDisplayed()
        composeRule.onNodeWithText("Only me").performScrollTo().performClick()
        composeRule.onNodeWithText("Save walk").performScrollTo().performClick()

        assertEquals(listOf("visibility:OnlyMe", "save"), actions)
    }

    @Test
    fun the_screen_explains_a_walk_too_short() {
        val actions = mutableListOf<String>()
        show(WalkSummaryUiState(walk = finished.copy(segments = emptyList())), actions)

        composeRule.onNodeWithText("This walk is too short to be saved: there is no route yet.").assertIsDisplayed()
        composeRule.onNodeWithText("Discard").performClick()

        assertEquals(listOf("discard"), actions)
    }
}
