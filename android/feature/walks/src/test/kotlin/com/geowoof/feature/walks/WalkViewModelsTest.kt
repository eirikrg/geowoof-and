package com.geowoof.feature.walks

import androidx.lifecycle.SavedStateHandle
import com.geowoof.core.common.AppConfig
import com.geowoof.core.model.AppError
import com.geowoof.core.model.Outcome
import com.geowoof.core.model.WalkSyncStatus
import com.geowoof.core.testing.FakePetsRepository
import com.geowoof.core.testing.FakeWalksRepository
import com.geowoof.core.testing.MainDispatcherRule
import com.geowoof.core.testing.TestPets
import com.geowoof.core.testing.TestWalks
import com.geowoof.feature.walks.detail.WalkDetailViewModel
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

class WalkViewModelsTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val walks = FakeWalksRepository()
    private val pets = FakePetsRepository(listOf(TestPets.pet(id = "luna", name = "Luna")))

    private fun config(hasMaps: Boolean = false) = AppConfig(
        apiBaseUrl = "http://localhost/",
        googleServerClientId = null,
        logHttpTraffic = false,
        isMapsConfigured = hasMaps,
    )

    private fun detail(hasMaps: Boolean = false) =
        WalkDetailViewModel(SavedStateHandle(mapOf("walkId" to "walk-1")), walks, pets, config(hasMaps))

    private fun TestScope.subscribe(viewModel: WalkDetailViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
    }

    @Test
    fun the_list_of_a_person_refreshes_its_totals() = runTest {
        walks.setTotals(null, TestWalks.totals(walkCount = 42))
        val viewModel = WalksViewModel(SavedStateHandle(), walks, pets)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        assertEquals(listOf<String?>(null), walks.refreshedTotals)
        assertEquals(42, viewModel.uiState.value.totals?.walkCount)
        assertNull(viewModel.uiState.value.petName)
    }

    @Test
    fun the_list_of_a_pet_has_its_name_and_its_totals() = runTest {
        walks.setTotals("luna", TestWalks.totals(walkCount = 7))
        val viewModel = WalksViewModel(SavedStateHandle(mapOf("petId" to "luna")), walks, pets)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        assertEquals(listOf<String?>("luna"), walks.refreshedTotals)
        assertEquals("Luna", viewModel.uiState.value.petName)
        assertEquals(7, viewModel.uiState.value.totals?.walkCount)
    }

    @Test
    fun the_detail_brings_the_route_of_a_synced_walk() = runTest {
        walks.setWalks(listOf(TestWalks.walk()))
        val viewModel = detail()
        subscribe(viewModel)

        assertEquals(listOf("walk-1"), walks.refreshedWalks)
        assertEquals("Luna", viewModel.uiState.value.petNames)
        assertFalse(viewModel.uiState.value.isRefreshing)
    }

    @Test
    fun a_walk_still_on_the_device_has_nothing_to_bring() = runTest {
        walks.setWalks(listOf(TestWalks.walk(syncStatus = WalkSyncStatus.PendingSync)))
        subscribe(detail())

        assertEquals(emptyList<String>(), walks.refreshedWalks)
    }

    @Test
    fun without_a_connection_it_only_complains_if_the_route_is_missing() = runTest {
        walks.setWalks(listOf(TestWalks.walk()))
        walks.refreshResult = Outcome.Failure(AppError.Network)
        val withoutRoute = detail()
        subscribe(withoutRoute)
        assertEquals(AppError.Network, withoutRoute.uiState.value.error)

        walks.setRoute("walk-1", listOf(TestWalks.segment(3)))
        val withRoute = detail()
        subscribe(withRoute)
        assertNull(withRoute.uiState.value.error)
    }

    @Test
    fun the_map_is_only_used_with_a_key() = runTest {
        walks.setWalks(listOf(TestWalks.walk()))
        val withKey = detail(hasMaps = true)
        subscribe(withKey)
        val withoutKey = detail(hasMaps = false)
        subscribe(withoutKey)

        assertTrue(withKey.uiState.value.showsMap)
        assertFalse(withoutKey.uiState.value.showsMap)
    }

    @Test
    fun deleting_asks_first_and_then_goes_back() = runTest {
        walks.setWalks(listOf(TestWalks.walk()))
        val viewModel = detail()
        subscribe(viewModel)

        viewModel.onRequestDelete()
        assertTrue(viewModel.uiState.value.isConfirmingDelete)
        viewModel.onDismissDelete()
        assertEquals(emptyList<String>(), walks.deleted)

        viewModel.onRequestDelete()
        viewModel.onConfirmDelete()

        assertEquals(listOf("walk-1"), walks.deleted)
        assertTrue(viewModel.uiState.value.isDeleted)
    }

    @Test
    fun a_rejected_walk_can_be_tried_again() = runTest {
        walks.setWalks(listOf(TestWalks.walk(syncStatus = WalkSyncStatus.Failed)))
        val viewModel = detail()
        subscribe(viewModel)

        viewModel.onRetryUpload()

        assertEquals(listOf("walk-1"), walks.retried)
    }
}
