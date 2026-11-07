package com.geowoof.feature.search

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import com.geowoof.core.model.AppError
import com.geowoof.core.model.Outcome
import com.geowoof.core.testing.FakePeopleRepository
import com.geowoof.core.testing.FakeRecentPeopleRepository
import com.geowoof.core.testing.MainDispatcherRule
import com.geowoof.core.testing.TestPets
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SearchViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val people = FakePeopleRepository()
    private val recents = FakeRecentPeopleRepository()
    private val viewModel by lazy { SearchViewModel(people, recents) }

    private fun TestScope.subscribe() {
        backgroundScope.launch { viewModel.uiState.collect {} }
        backgroundScope.launch { viewModel.users.collect {} }
        backgroundScope.launch { viewModel.pets.collect {} }
        runCurrent()
    }

    private fun TestScope.type(text: String) {
        viewModel.query.setTextAndPlaceCursorAtEnd(text)
        Snapshot.sendApplyNotifications()
        runCurrent()
    }

    @Test
    fun it_waits_until_the_person_stops_typing() = runTest {
        subscribe()

        type("l")
        advanceTimeBy(100)
        type("lu")
        advanceTimeBy(100)
        type("lun")
        assertEquals(emptyList<String>(), people.searches)

        advanceTimeBy(350)
        runCurrent()

        assertEquals(listOf("users:lun", "pets:lun"), people.searches)
        assertTrue(viewModel.uiState.value.hasEnoughText)
    }

    @Test
    fun with_less_than_two_characters_it_does_not_search() = runTest {
        subscribe()

        type(" a ")
        advanceTimeBy(350)
        runCurrent()

        assertEquals(emptyList<String>(), people.searches)
        assertFalse(viewModel.uiState.value.hasEnoughText)
    }

    @Test
    fun spaces_around_the_text_do_not_search_again() = runTest {
        subscribe()

        type("luna")
        advanceTimeBy(350)
        runCurrent()
        type(" luna ")
        advanceTimeBy(350)
        runCurrent()

        assertEquals(listOf("users:luna", "pets:luna"), people.searches)
    }

    private fun bea() = Outcome.Success(TestPets.profile(displayName = "Bea", username = "bea").copy(id = "bea-id"))

    private fun carlos() =
        Outcome.Success(TestPets.profile(displayName = "Carlos", username = "carlos").copy(id = "carlos-id"))

    private val recent get() = viewModel.uiState.value.recent.map { it.id }

    @Test
    fun without_having_opened_anyone_there_is_no_list_and_it_is_not_waiting() = runTest {
        subscribe()

        assertEquals(emptyList<String>(), recent)
        assertFalse(viewModel.uiState.value.isLoadingRecent)
        assertFalse(viewModel.uiState.value.hasRecentFailed)
    }

    @Test
    fun whoever_is_opened_goes_first_with_what_the_server_says_of_them() = runTest {
        people.profilesById = mapOf("bea-id" to bea(), "carlos-id" to carlos())
        subscribe()

        viewModel.onPersonOpened("carlos-id")
        runCurrent()
        viewModel.onPersonOpened("bea-id")
        runCurrent()

        assertEquals(listOf("bea-id", "carlos-id"), recent)
        assertEquals("Bea", viewModel.uiState.value.recent.first().displayName)
    }

    @Test
    fun whoever_the_server_no_longer_shows_leaves_the_list_as_after_a_block() = runTest {
        recents.ids.value = listOf("bea-id", "carlos-id")
        people.profilesById = mapOf(
            "bea-id" to Outcome.Failure(AppError.Api(status = 404, code = "identity.user_not_found")),
            "carlos-id" to carlos(),
        )
        subscribe()

        assertEquals(listOf("carlos-id"), recent)
        assertEquals(listOf("carlos-id"), recents.ids.value)
    }

    @Test
    fun without_a_connection_nobody_is_forgotten_and_it_can_try_again() = runTest {
        recents.ids.value = listOf("bea-id")
        people.profilesById = mapOf("bea-id" to Outcome.Failure(AppError.Network))
        subscribe()

        assertTrue(viewModel.uiState.value.hasRecentFailed)
        assertEquals(listOf("bea-id"), recents.ids.value)

        people.profilesById = mapOf("bea-id" to bea())
        viewModel.onReloadRecent()
        runCurrent()

        assertEquals(listOf("bea-id"), recent)
        assertFalse(viewModel.uiState.value.hasRecentFailed)
    }

    @Test
    fun removing_someone_does_not_ask_the_server_for_the_others_again() = runTest {
        recents.ids.value = listOf("bea-id", "carlos-id")
        people.profilesById = mapOf("bea-id" to bea(), "carlos-id" to carlos())
        subscribe()
        people.profileReads.clear()

        viewModel.onRemoveRecent("bea-id")
        runCurrent()

        assertEquals(listOf("carlos-id"), recent)
        assertEquals(emptyList<String>(), people.profileReads)
    }

    @Test
    fun coming_back_asks_again_in_case_someone_changed() = runTest {
        recents.ids.value = listOf("bea-id")
        people.profilesById = mapOf("bea-id" to bea())
        subscribe()
        people.profileReads.clear()

        viewModel.onReloadRecent()
        runCurrent()

        assertEquals(listOf("bea-id"), people.profileReads)
    }

    @Test
    fun the_whole_list_can_be_cleared() = runTest {
        recents.ids.value = listOf("bea-id", "carlos-id")
        people.profilesById = mapOf("bea-id" to bea(), "carlos-id" to carlos())
        subscribe()

        viewModel.onRemoveAllRecent()
        runCurrent()

        assertEquals(emptyList<String>(), recent)
    }

    @Test
    fun it_can_show_people_or_pets() = runTest {
        subscribe()
        assertEquals(SearchTab.People, viewModel.uiState.value.tab)

        viewModel.onSelectTab(SearchTab.Pets)
        runCurrent()

        assertEquals(SearchTab.Pets, viewModel.uiState.value.tab)
    }
}
