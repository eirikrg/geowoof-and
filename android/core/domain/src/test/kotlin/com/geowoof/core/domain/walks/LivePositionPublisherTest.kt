package com.geowoof.core.domain.walks

import com.geowoof.core.model.AppError
import com.geowoof.core.model.GeoPoint
import com.geowoof.core.model.LiveVisibility
import com.geowoof.core.model.LocationFix
import com.geowoof.core.model.Outcome
import com.geowoof.core.model.RoutePoint
import com.geowoof.core.model.RouteSegment
import com.geowoof.core.model.UserSettings
import com.geowoof.core.testing.FakeActiveWalkRepository
import com.geowoof.core.testing.FakeLiveRepository
import com.geowoof.core.testing.FakeLocationDataSource
import com.geowoof.core.testing.FakeSettingsRepository
import com.geowoof.core.testing.TestWalks
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LivePositionPublisherTest {
    private val location = FakeLocationDataSource()
    private val settings = FakeSettingsRepository(UserSettings(LiveVisibility.Everyone, hideStartDistanceM = 200))
    private val live = FakeLiveRepository()
    private val walk = TestWalks.activeWalk(segments = emptyList()).copy(id = "walk", petIds = listOf("luna"))
    private val walks = FakeActiveWalkRepository(walk)
    private val publisher = LivePositionPublisher(location, settings, live, walks, FilterLocationUseCase())

    /** This far north of the start, which is at 40.4. */
    private fun latitude(metersNorth: Int) = START.latitude + metersNorth / METERS_PER_DEGREE

    private fun fix(timeSeconds: Int, metersNorth: Int = 0, accuracyM: Float = 8f) =
        LocationFix(GeoPoint(latitude(metersNorth), START.longitude), accuracyM, timeSeconds * 1_000L)

    private fun position(metersNorth: Int) = "position:${latitude(metersNorth)}"

    private fun TestScope.publish() =
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { publisher.publish(walk) }

    /** Past the first position, which is where the zone is measured from. */
    private suspend fun TestScope.leaveTheStart() {
        location.send(fix(0))
        location.send(fix(10, metersNorth = 300))
        runCurrent()
    }

    @Test
    fun nothing_is_shared_near_the_start_and_it_starts_when_leaving_it() = runTest {
        publish()
        runCurrent()

        location.send(fix(0))
        location.send(fix(10, metersNorth = 199))
        runCurrent()
        assertTrue(live.calls.isEmpty())

        location.send(fix(20, metersNorth = 201))
        runCurrent()
        assertEquals(listOf("start:walk:luna", position(201)), live.calls)
    }

    @Test
    fun coming_back_to_the_start_hides_them_again() = runTest {
        publish()
        leaveTheStart()
        live.calls.clear()

        location.send(fix(20, metersNorth = 100))
        runCurrent()
        assertEquals(listOf("stop"), live.calls)

        location.send(fix(30, metersNorth = 150))
        runCurrent()
        assertEquals(listOf("stop"), live.calls)

        location.send(fix(40, metersNorth = 300))
        runCurrent()
        assertEquals(listOf("stop", "start:walk:luna", position(300)), live.calls)
    }

    @Test
    fun without_hiding_the_start_it_shares_from_the_first_position() = runTest {
        settings.settings.value = UserSettings(LiveVisibility.Everyone, hideStartDistanceM = 0)
        publish()
        runCurrent()

        location.send(fix(0))
        runCurrent()

        assertEquals(listOf("start:walk:luna", position(0)), live.calls)
    }

    @Test
    fun after_a_pause_the_start_is_still_where_the_walk_began() = runTest {
        walks.startAt(latitude(1_000))
        publish()
        runCurrent()

        location.send(fix(0))
        runCurrent()
        assertEquals(listOf("start:walk:luna", position(0)), live.calls)
    }

    @Test
    fun a_position_is_sent_every_few_seconds_also_standing_still() = runTest {
        settings.settings.value = UserSettings(LiveVisibility.Everyone, hideStartDistanceM = 0)
        publish()
        runCurrent()

        listOf(0, 2, 4, 5, 7, 10).forEach { second -> location.send(fix(second)) }
        runCurrent()

        assertEquals(3, live.calls.count { it.startsWith("position") })
    }

    @Test
    fun a_position_too_imprecise_is_not_where_they_are() = runTest {
        settings.settings.value = UserSettings(LiveVisibility.Everyone, hideStartDistanceM = 0)
        publish()
        runCurrent()

        location.send(fix(0))
        runCurrent()
        assertEquals(1, live.calls.count { it.startsWith("position") })

        location.send(fix(10, accuracyM = 51f))
        runCurrent()
        assertEquals(1, live.calls.count { it.startsWith("position") })

        location.send(fix(20, accuracyM = 50f))
        runCurrent()
        assertEquals(2, live.calls.count { it.startsWith("position") })
    }

    @Test
    fun with_nobody_or_without_a_choice_nothing_is_even_listened_to() = runTest {
        listOf(LiveVisibility.Nobody, null).forEach { visibility ->
            settings.settings.value = UserSettings(visibility)
            val publishing = publish()
            runCurrent()

            assertEquals(0, location.listeners)
            assertEquals(0, live.connectionUsers)
            publishing.cancel()
            runCurrent()
        }
    }

    @Test
    fun choosing_nobody_during_the_walk_says_goodbye_and_stops() = runTest {
        publish()
        leaveTheStart()
        live.calls.clear()

        settings.settings.value = UserSettings(LiveVisibility.Nobody)
        runCurrent()

        assertEquals(listOf("stop"), live.calls)
        assertEquals(0, location.listeners)
        assertEquals(0, live.connectionUsers)
    }

    @Test
    fun choosing_during_the_walk_starts_sharing_from_the_next_position() = runTest {
        settings.settings.value = UserSettings(liveVisibility = null)
        publish()
        runCurrent()
        assertEquals(0, location.listeners)

        settings.settings.value = UserSettings(LiveVisibility.FriendsOnly, hideStartDistanceM = 0)
        runCurrent()
        location.send(fix(0))
        runCurrent()

        assertEquals(listOf("start:walk:luna", position(0)), live.calls)
    }

    @Test
    fun choosing_during_the_walk_does_not_move_where_it_started() = runTest {
        walks.startAt(latitude(0))
        settings.settings.value = UserSettings(liveVisibility = null)
        publish()
        runCurrent()

        settings.settings.value = UserSettings(LiveVisibility.Everyone, hideStartDistanceM = 200)
        runCurrent()
        location.send(fix(0, metersNorth = 150))
        runCurrent()
        assertTrue(live.calls.isEmpty())

        location.send(fix(10, metersNorth = 250))
        runCurrent()
        assertEquals(listOf("start:walk:luna", position(250)), live.calls)
    }

    @Test
    fun the_zone_is_not_measured_from_a_position_that_is_not_good_enough() = runTest {
        publish()
        runCurrent()

        location.send(fix(0, accuracyM = 40f))
        location.send(fix(10, metersNorth = 300, accuracyM = 40f))
        runCurrent()
        assertTrue(live.calls.isEmpty())

        location.send(fix(20, metersNorth = 300, accuracyM = 10f))
        location.send(fix(30, metersNorth = 400, accuracyM = 10f))
        runCurrent()
        assertTrue(live.calls.isEmpty())

        location.send(fix(40, metersNorth = 501, accuracyM = 40f))
        runCurrent()
        assertEquals(listOf("start:walk:luna", position(501)), live.calls)
    }

    private fun FakeActiveWalkRepository.startAt(latitude: Double) {
        val first = RouteSegment(listOf(RoutePoint(GeoPoint(latitude, START.longitude), 0)))
        set(walk.copy(segments = listOf(first)))
    }

    @Test
    fun ending_says_goodbye_only_if_it_was_sharing() = runTest {
        val notYet = publish()
        runCurrent()
        location.send(fix(0))
        runCurrent()
        notYet.cancel()
        runCurrent()
        assertTrue(live.calls.isEmpty())

        val publishing = publish()
        leaveTheStart()
        live.calls.clear()
        publishing.cancel()
        runCurrent()

        assertEquals(listOf("stop"), live.calls)
        assertEquals(0, live.connectionUsers)
    }

    @Test
    fun without_a_connection_nothing_queues_and_the_next_position_tries_again() = runTest {
        settings.settings.value = UserSettings(LiveVisibility.Everyone, hideStartDistanceM = 0)
        live.startResult = Outcome.Failure(AppError.Network)
        publish()
        runCurrent()

        location.send(fix(0))
        location.send(fix(6))
        runCurrent()
        assertEquals(listOf("start:walk:luna", "start:walk:luna"), live.calls)

        live.startResult = Outcome.Success(LiveVisibility.Everyone)
        location.send(fix(12))
        runCurrent()
        assertEquals(1, live.calls.count { it.startsWith("position") })
    }

    @Test
    fun if_the_server_forgot_the_session_it_is_started_again() = runTest {
        settings.settings.value = UserSettings(LiveVisibility.Everyone, hideStartDistanceM = 0)
        publish()
        runCurrent()
        location.send(fix(0))
        runCurrent()
        live.calls.clear()

        live.positionResult = Outcome.Failure(AppError.Api(0, "live.not_sharing"))
        location.send(fix(6))
        runCurrent()
        live.positionResult = Outcome.Success(Unit)
        location.send(fix(12))
        runCurrent()

        assertEquals(listOf(position(0), "start:walk:luna", position(0)), live.calls)
    }

    @Test
    fun if_the_server_refuses_it_stops_asking_and_finds_out_why() = runTest {
        settings.settings.value = UserSettings(LiveVisibility.Everyone, hideStartDistanceM = 0)
        live.startResult = Outcome.Failure(AppError.Api(0, "live.sharing_disabled"))
        publish()
        runCurrent()

        location.send(fix(0))
        location.send(fix(10))
        location.send(fix(20))
        runCurrent()

        assertEquals(listOf("start:walk:luna"), live.calls)
        assertEquals(1, settings.refreshes)
    }

    @Test
    fun chosen_nobody_on_another_device_ends_when_it_finds_out() = runTest {
        settings.settings.value = UserSettings(LiveVisibility.Everyone, hideStartDistanceM = 0)
        settings.refreshed = UserSettings(LiveVisibility.Nobody)
        publish()
        runCurrent()
        location.send(fix(0))
        runCurrent()
        live.calls.clear()

        live.positionResult = Outcome.Failure(AppError.Api(0, "live.sharing_disabled"))
        location.send(fix(6))
        runCurrent()

        assertEquals(0, location.listeners)
        assertEquals(0, live.connectionUsers)
    }

    @Test
    fun losing_the_permission_does_not_end_in_a_crash() = runTest {
        location.hasPermission = false

        val publishing = publish()
        runCurrent()

        assertTrue(publishing.isActive)
    }

    @Test
    fun an_unexpected_failure_stays_here_and_never_reaches_the_recording() = runTest {
        val broken = LivePositionPublisher(location, settings, BrokenLive(live), walks, FilterLocationUseCase())
        settings.settings.value = UserSettings(LiveVisibility.Everyone, hideStartDistanceM = 0)
        var finished = false
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            broken.publish(walk)
            finished = true
        }
        runCurrent()

        location.send(fix(0))
        runCurrent()

        assertTrue(finished)
    }

    private class BrokenLive(delegate: FakeLiveRepository) :
        com.geowoof.core.data.repository.LiveRepository by delegate {
        override suspend fun startSharing(walkId: String, petIds: List<String>): Outcome<LiveVisibility> =
            error("Something nobody expected")
    }

    private companion object {
        val START = GeoPoint(40.4, -3.7)
        const val METERS_PER_DEGREE = 111_195.0
    }
}
