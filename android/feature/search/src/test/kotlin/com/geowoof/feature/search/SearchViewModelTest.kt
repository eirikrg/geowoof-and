package com.geowoof.feature.search

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import com.geowoof.core.testing.FakePeopleRepository
import com.geowoof.core.testing.MainDispatcherRule
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
    private val viewModel by lazy { SearchViewModel(people) }

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

    @Test
    fun it_can_show_people_or_pets() = runTest {
        subscribe()
        assertEquals(SearchTab.People, viewModel.uiState.value.tab)

        viewModel.onSelectTab(SearchTab.Pets)
        runCurrent()

        assertEquals(SearchTab.Pets, viewModel.uiState.value.tab)
    }
}
