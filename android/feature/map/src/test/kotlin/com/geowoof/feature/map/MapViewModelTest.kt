package com.geowoof.feature.map

import com.geowoof.core.domain.walks.ComputeWalkStatsUseCase
import com.geowoof.core.location.LocationPermissionLevel
import com.geowoof.core.model.ActiveWalkStatus
import com.geowoof.core.testing.FakeActiveWalkRepository
import com.geowoof.core.testing.FakeLocationAccess
import com.geowoof.core.testing.FakePetsRepository
import com.geowoof.core.testing.MainDispatcherRule
import com.geowoof.core.testing.TestPets
import com.geowoof.core.testing.TestWalks
import com.geowoof.feature.map.tracking.WalkTrackingController
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MapViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val walks = FakeActiveWalkRepository()
    private val pets = FakePetsRepository(listOf(TestPets.pet(id = "luna"), TestPets.pet(id = "toby", name = "Toby")))
    private val access = FakeLocationAccess()
    private val tracking = FakeController()
    private val viewModel by lazy { MapViewModel(walks, pets, access, tracking, ComputeWalkStatsUseCase()) }

    private fun kotlinx.coroutines.test.TestScope.subscribe() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
    }

    private val state get() = viewModel.uiState.value

    @Test
    fun a_walk_starts_with_the_chosen_pets() = runTest {
        subscribe()

        viewModel.onStartClick()
        assertTrue(state.isPickingPets)
        viewModel.onTogglePet("luna")
        viewModel.onTogglePet("toby")
        viewModel.onTogglePet("toby")
        viewModel.onConfirmPets()

        assertFalse(state.isPickingPets)
        assertEquals(listOf("luna"), walks.value?.petIds)
        assertEquals(ActiveWalkStatus.Preparing, walks.value?.status)
        assertEquals(1, tracking.starts)
    }

    @Test
    fun with_a_single_pet_there_is_nothing_to_choose() = runTest {
        subscribe()

        viewModel.onConfirmPets("luna")

        assertEquals(listOf("luna"), walks.value?.petIds)
    }

    @Test
    fun it_cannot_start_without_pets() = runTest {
        subscribe()

        viewModel.onConfirmPets()

        assertNull(walks.value)
        assertEquals(0, tracking.starts)
    }

    @Test
    fun the_permission_is_explained_before_asking_for_it() = runTest {
        access.level = LocationPermissionLevel.None
        subscribe()
        viewModel.onConfirmPets("luna")

        assertEquals(WalkPrompt.PermissionRationale, state.prompt)
        assertNull(walks.value)

        access.level = LocationPermissionLevel.Precise
        viewModel.onPermissionsResult()

        assertNull(state.prompt)
        assertEquals(listOf("luna"), walks.value?.petIds)
    }

    @Test
    fun an_approximate_location_cannot_record_a_walk() = runTest {
        access.level = LocationPermissionLevel.None
        subscribe()
        viewModel.onConfirmPets("luna")

        access.level = LocationPermissionLevel.Approximate
        viewModel.onPermissionsResult()

        assertEquals(WalkPrompt.ApproximateLocation, state.prompt)
        assertNull(walks.value)
    }

    @Test
    fun denying_the_permission_does_not_start_anything() = runTest {
        access.level = LocationPermissionLevel.None
        subscribe()
        viewModel.onConfirmPets("luna")

        viewModel.onPermissionsResult()
        assertEquals(WalkPrompt.PermissionDenied, state.prompt)

        viewModel.onDismissPrompt()
        access.level = LocationPermissionLevel.Precise
        viewModel.onPermissionsResult()

        assertNull(state.prompt)
        assertNull(walks.value)
    }

    @Test
    fun location_turned_off_is_explained() = runTest {
        access.enabled = false
        subscribe()

        viewModel.onConfirmPets("luna")

        assertEquals(WalkPrompt.LocationDisabled, state.prompt)
        assertNull(walks.value)
    }

    @Test
    fun if_the_system_does_not_let_the_service_start_the_walk_is_interrupted() = runTest {
        tracking.canStart = false
        subscribe()

        viewModel.onConfirmPets("luna")

        assertEquals(ActiveWalkStatus.Interrupted, walks.value?.status)
    }

    @Test
    fun a_recording_without_a_service_is_an_interrupted_one() = runTest {
        walks.set(TestWalks.activeWalk())
        subscribe()

        viewModel.onScreenResumed()

        assertEquals(ActiveWalkStatus.Interrupted, walks.value?.status)
    }

    @Test
    fun a_recording_with_its_service_alive_continues() = runTest {
        walks.set(TestWalks.activeWalk())
        tracking.isRunning = true
        subscribe()

        viewModel.onScreenResumed()

        assertEquals(ActiveWalkStatus.Recording, walks.value?.status)
    }

    @Test
    fun an_interrupted_walk_can_continue_and_starts_the_service_again() = runTest {
        walks.set(TestWalks.activeWalk(status = ActiveWalkStatus.Interrupted))
        subscribe()

        viewModel.onResumeClick()

        assertEquals(ActiveWalkStatus.Recording, walks.value?.status)
        assertEquals(1, walks.value?.currentSegment)
        assertEquals(1, tracking.starts)
    }

    @Test
    fun an_interrupted_walk_asks_again_if_the_permission_was_lost() = runTest {
        walks.set(TestWalks.activeWalk(status = ActiveWalkStatus.Interrupted))
        access.level = LocationPermissionLevel.None
        subscribe()

        viewModel.onResumeClick()

        assertEquals(WalkPrompt.PermissionRationale, state.prompt)
        assertEquals(ActiveWalkStatus.Interrupted, walks.value?.status)
    }

    @Test
    fun finishing_opens_the_summary_once() = runTest {
        walks.set(TestWalks.activeWalk())
        subscribe()

        viewModel.onFinishClick()
        assertTrue(state.openSummary)
        assertEquals(ActiveWalkStatus.Finished, walks.value?.status)

        viewModel.onSummaryOpened()
        assertFalse(state.openSummary)

        viewModel.onReviewClick()
        assertTrue(state.openSummary)
    }

    @Test
    fun pausing_keeps_the_route_and_cancelling_removes_the_walk() = runTest {
        walks.set(TestWalks.activeWalk(segments = listOf(TestWalks.segment(11))))
        subscribe()

        viewModel.onPauseClick()
        assertEquals(ActiveWalkStatus.Paused, state.walk?.status)
        assertEquals(111, state.stats?.distanceM)

        viewModel.onCancelClick()
        assertNull(state.walk)
    }

    private class FakeController : WalkTrackingController {
        override var isRunning = false
        var canStart = true
        var starts = 0

        override fun start(): Boolean {
            starts++
            if (canStart) isRunning = true
            return canStart
        }
    }
}
