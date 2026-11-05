package com.geowoof.feature.map

import com.geowoof.core.model.AppError
import com.geowoof.core.model.LiveNearby
import com.geowoof.core.model.MapViewport
import com.geowoof.core.model.Outcome
import com.geowoof.core.model.Relationship
import com.geowoof.core.model.RelationshipStatus
import com.geowoof.core.testing.FakeLiveRepository
import com.geowoof.core.testing.FakeSocialRepository
import com.geowoof.core.testing.MainDispatcherRule
import com.geowoof.core.testing.TestWalkers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LiveWalkersViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val live = FakeLiveRepository()
    private val social = FakeSocialRepository()
    private val viewModel by lazy { LiveWalkersViewModel(live, social) }
    private val bea = TestWalkers.walker()

    private val state get() = viewModel.uiState.value

    private fun TestScope.subscribe() =
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

    private fun viewport(north: Double) = MapViewport(north, north - 0.1, -3.6, -3.7)

    @Test
    fun it_shows_who_is_there() = runTest {
        subscribe()

        live.nearby.value = LiveNearby(walkers = listOf(bea))
        runCurrent()

        assertEquals(listOf(bea), state.walkers)
    }

    @Test
    fun it_says_when_the_map_has_to_get_closer() = runTest {
        subscribe()

        live.nearby.value = LiveNearby(isTooWide = true)
        runCurrent()

        assertTrue(state.isTooWide)
    }

    @Test
    fun moving_the_map_only_asks_when_it_stops() = runTest {
        subscribe()
        runCurrent()

        viewModel.onViewportChange(viewport(40.1))
        advanceTimeBy(100)
        viewModel.onViewportChange(viewport(40.2))
        advanceTimeBy(100)
        viewModel.onViewportChange(viewport(40.3))
        runCurrent()
        assertTrue(live.viewports.isEmpty())

        advanceTimeBy(501)
        runCurrent()
        assertEquals(listOf(viewport(40.3)), live.viewports)
    }

    @Test
    fun the_connection_closes_a_while_after_the_screen_goes_away() = runTest {
        val screen = subscribe()
        runCurrent()
        assertEquals(1, live.connectionUsers)

        screen.cancel()
        advanceTimeBy(4_000)
        runCurrent()
        assertEquals(1, live.connectionUsers)

        advanceTimeBy(1_001)
        runCurrent()
        assertEquals(0, live.connectionUsers)
    }

    @Test
    fun coming_back_to_the_screen_asks_for_the_same_area_again() = runTest {
        val screen = subscribe()
        viewModel.onViewportChange(viewport(40.1))
        advanceTimeBy(501)
        runCurrent()
        screen.cancel()
        advanceTimeBy(6_000)
        runCurrent()

        subscribe()
        advanceTimeBy(501)
        runCurrent()

        assertEquals(listOf(viewport(40.1), viewport(40.1)), live.viewports)
    }

    @Test
    fun the_list_opens_and_closes() = runTest {
        subscribe()

        viewModel.onOpenList()
        assertTrue(state.isListOpen)

        viewModel.onDismissList()
        assertFalse(state.isListOpen)
    }

    @Test
    fun choosing_someone_from_the_list_shows_them_with_the_relationship() = runTest {
        social.relationshipResult = Outcome.Success(Relationship(RelationshipStatus.Friends))
        subscribe()
        live.nearby.value = LiveNearby(walkers = listOf(bea))
        runCurrent()
        viewModel.onOpenList()

        viewModel.onWalkerClick(bea.userId)
        runCurrent()

        assertFalse(state.isListOpen)
        assertEquals(bea, state.selected)
        assertEquals(RelationshipStatus.Friends, state.relationship?.status)
    }

    @Test
    fun a_walker_who_is_no_longer_there_cannot_be_chosen() = runTest {
        subscribe()
        runCurrent()

        viewModel.onWalkerClick("gone")

        assertNull(state.selected)
    }

    @Test
    fun without_knowing_the_relationship_nothing_about_following_is_offered() = runTest {
        social.relationshipResult = Outcome.Failure(AppError.Network)
        subscribe()
        live.nearby.value = LiveNearby(walkers = listOf(bea))
        runCurrent()

        viewModel.onWalkerClick(bea.userId)
        runCurrent()

        assertEquals(bea, state.selected)
        assertNull(state.relationship)
    }

    @Test
    fun the_sheet_stays_open_with_what_was_known_if_they_leave_and_follows_them_while_they_stay() = runTest {
        subscribe()
        live.nearby.value = LiveNearby(walkers = listOf(bea))
        runCurrent()
        viewModel.onWalkerClick(bea.userId)
        runCurrent()

        val moved = bea.copy(position = bea.position.copy(latitude = 40.5))
        live.nearby.value = LiveNearby(walkers = listOf(moved))
        runCurrent()
        assertEquals(moved, state.selected)

        live.nearby.value = LiveNearby()
        runCurrent()
        assertEquals(bea.userId, state.selected?.userId)

        viewModel.onDismissWalker()
        assertNull(state.selected)
    }

    @Test
    fun following_someone_uses_the_answer_of_the_server() = runTest {
        social.relationshipResult = Outcome.Success(Relationship(RelationshipStatus.None))
        social.followResult = Outcome.Success(Relationship(RelationshipStatus.Requested))
        subscribe()
        live.nearby.value = LiveNearby(walkers = listOf(bea))
        runCurrent()
        viewModel.onWalkerClick(bea.userId)
        runCurrent()

        viewModel.onFollowClick()
        runCurrent()

        assertEquals(RelationshipStatus.Requested, state.relationship?.status)
        assertTrue("follow:${bea.userId}" in social.calls)
        assertFalse(state.isChangingFollow)
    }

    @Test
    fun following_back_asks_first_because_it_makes_them_friends() = runTest {
        social.relationshipResult = Outcome.Success(Relationship(RelationshipStatus.None, followsYou = true))
        subscribe()
        live.nearby.value = LiveNearby(walkers = listOf(bea))
        runCurrent()
        viewModel.onWalkerClick(bea.userId)
        runCurrent()

        viewModel.onFollowClick()
        runCurrent()
        assertTrue(state.isConfirmingFollowBack)
        assertFalse("follow:${bea.userId}" in social.calls)

        viewModel.onDismissFollowBack()
        assertFalse(state.isConfirmingFollowBack)
        assertFalse("follow:${bea.userId}" in social.calls)

        viewModel.onFollowClick()
        viewModel.onConfirmFollowBack()
        runCurrent()
        assertFalse(state.isConfirmingFollowBack)
        assertTrue("follow:${bea.userId}" in social.calls)
    }

    @Test
    fun if_following_fails_it_says_so_and_can_be_tried_again() = runTest {
        social.relationshipResult = Outcome.Success(Relationship(RelationshipStatus.None))
        subscribe()
        live.nearby.value = LiveNearby(walkers = listOf(bea))
        runCurrent()
        viewModel.onWalkerClick(bea.userId)
        runCurrent()

        social.followResult = Outcome.Failure(AppError.Network)
        viewModel.onFollowClick()
        runCurrent()

        assertEquals(AppError.Network, state.error)
        assertFalse(state.isChangingFollow)
        assertEquals(RelationshipStatus.None, state.relationship?.status)

        viewModel.onErrorShown()
        assertNull(state.error)
    }
}
