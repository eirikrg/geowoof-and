package com.geowoof.feature.map

import app.cash.turbine.test
import com.geowoof.core.domain.walks.ComputeWalkStatsUseCase
import com.geowoof.core.domain.walks.FilterLocationUseCase
import com.geowoof.core.domain.walks.LivePositionPublisher
import com.geowoof.core.domain.walks.SmoothRouteUseCase
import com.geowoof.core.domain.walks.TrackWalkUseCase
import com.geowoof.core.model.ActiveWalk
import com.geowoof.core.model.ActiveWalkStatus
import com.geowoof.core.model.AppError
import com.geowoof.core.model.GeoPoint
import com.geowoof.core.model.LiveVisibility
import com.geowoof.core.model.LocationFix
import com.geowoof.core.model.Outcome
import com.geowoof.core.model.UserSettings
import com.geowoof.core.testing.FakeActiveWalkRepository
import com.geowoof.core.testing.FakeLiveRepository
import com.geowoof.core.testing.FakeLocationDataSource
import com.geowoof.core.testing.FakeSettingsRepository
import com.geowoof.core.testing.TestWalks
import com.geowoof.feature.map.tracking.WalkNotificationState
import com.geowoof.feature.map.tracking.WalkTrackingSession
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** The life of the service with fakes: what it does when the walk changes and when the system interferes. */
class WalkTrackingSessionTest {
    private val location = FakeLocationDataSource()
    private val computeStats = ComputeWalkStatsUseCase()
    private val settings = FakeSettingsRepository(UserSettings(LiveVisibility.Nobody))
    private val live = FakeLiveRepository()

    private fun session(walks: FakeActiveWalkRepository) = WalkTrackingSession(
        walks,
        location,
        TrackWalkUseCase(location, walks, FilterLocationUseCase(), SmoothRouteUseCase()),
        computeStats,
        LivePositionPublisher(location, settings, live, walks, FilterLocationUseCase()),
    )

    private fun fix(timeEpochMs: Long, metersNorth: Double = 0.0) =
        LocationFix(GeoPoint(40.4 + metersNorth / 111_195, -3.7), 5f, timeEpochMs)

    private fun recording() = TestWalks.activeWalk(segments = emptyList(), startedAt = Instant.ofEpochMilli(0))

    private fun TestScope.run(walks: FakeActiveWalkRepository, restoring: Boolean = false) =
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { session(walks).run(restoring) }

    @Test
    fun it_listens_only_while_the_walk_needs_positions() = runTest {
        val walks = FakeActiveWalkRepository(recording())
        run(walks)
        runCurrent()
        assertEquals(1, location.listeners)

        walks.pause()
        runCurrent()
        assertEquals(0, location.listeners)

        walks.resume()
        runCurrent()
        assertEquals(1, location.listeners)
        assertEquals(2, location.subscriptions)
    }

    @Test
    fun the_points_go_to_the_current_segment() = runTest {
        val walks = FakeActiveWalkRepository(recording())
        run(walks)
        runCurrent()

        location.send(fix(4_000))
        location.send(fix(8_000, metersNorth = 10.0))
        runCurrent()
        walks.pause()
        runCurrent()
        walks.resume()
        runCurrent()
        location.send(fix(600_000, metersNorth = 900.0))
        runCurrent()

        val walk = checkNotNull(walks.value)
        assertEquals(listOf(2, 1), walk.segments.map { it.points.size })
    }

    @Test
    fun finding_the_first_position_does_not_restart_the_recording() = runTest {
        val walks =
            FakeActiveWalkRepository(TestWalks.activeWalk(status = ActiveWalkStatus.Preparing, segments = emptyList()))
        run(walks)
        runCurrent()

        location.send(fix(4_000))
        runCurrent()

        assertEquals(ActiveWalkStatus.Recording, walks.value?.status)
        assertEquals(1, location.subscriptions)
    }

    @Test
    fun it_ends_when_the_walk_finishes() = runTest {
        val walks = FakeActiveWalkRepository(recording())
        val running = run(walks)
        runCurrent()

        walks.finish()
        runCurrent()

        assertFalse(running.isActive)
        assertEquals(0, location.listeners)
    }

    @Test
    fun it_ends_when_the_walk_is_discarded_or_the_session_closes() = runTest {
        val walks = FakeActiveWalkRepository(recording())
        val running = run(walks)
        runCurrent()

        walks.discard()
        runCurrent()

        assertFalse(running.isActive)
    }

    @Test
    fun without_a_walk_there_is_nothing_to_do() = runTest {
        val running = run(FakeActiveWalkRepository(null))
        runCurrent()

        assertFalse(running.isActive)
    }

    @Test
    fun losing_the_permission_interrupts_the_walk() = runTest {
        location.hasPermission = false
        val walks = FakeActiveWalkRepository(recording())
        val running = run(walks)
        runCurrent()

        assertEquals(ActiveWalkStatus.Interrupted, walks.value?.status)
        assertTrue(session(walks).isInterrupted())
        assertFalse(running.isActive)
    }

    @Test
    fun a_paused_walk_keeps_the_service_waiting() = runTest {
        val walks = FakeActiveWalkRepository(recording().copy(status = ActiveWalkStatus.Paused))
        val running = run(walks)
        runCurrent()

        assertTrue(running.isActive)
        assertEquals(0, location.listeners)
    }

    @Test
    fun after_the_death_of_the_process_it_continues_in_a_new_segment() = runTest {
        val before = TestWalks.activeWalk(segments = listOf(TestWalks.segment(3)), startedAt = Instant.ofEpochMilli(0))
        val walks = FakeActiveWalkRepository(before)
        run(walks, restoring = true)
        runCurrent()
        assertEquals(ActiveWalkStatus.Interrupted, walks.value?.status)

        location.send(fix(500_000))
        runCurrent()

        assertEquals(ActiveWalkStatus.Recording, walks.value?.status)
        assertEquals(1, walks.value?.currentSegment)
    }

    @Test
    fun if_the_system_gives_no_positions_after_the_restart_the_user_decides() = runTest {
        val walks = FakeActiveWalkRepository(TestWalks.activeWalk(segments = listOf(TestWalks.segment(3))))
        val running = run(walks, restoring = true)
        runCurrent()

        advanceTimeBy(61_000)
        runCurrent()

        assertEquals(ActiveWalkStatus.Interrupted, walks.value?.status)
        assertFalse(running.isActive)
    }

    @Test
    fun a_paused_walk_stays_paused_after_a_restart() = runTest {
        val walks = FakeActiveWalkRepository(recording().copy(status = ActiveWalkStatus.Paused))
        run(walks, restoring = true)
        runCurrent()

        assertEquals(ActiveWalkStatus.Paused, walks.value?.status)
    }

    @Test
    fun the_notification_stops_changing_when_the_walk_ends() = runTest {
        val walks = FakeActiveWalkRepository(TestWalks.activeWalk())

        session(walks).notificationStates.test {
            assertEquals(ActiveWalkStatus.Recording, awaitItem().status)

            walks.pause()
            assertEquals(ActiveWalkStatus.Paused, awaitItem().status)

            walks.finish()
            expectNoEvents()
        }
    }

    @Test
    fun the_notification_follows_the_walk() = runTest {
        val walk: ActiveWalk = TestWalks.activeWalk(segments = listOf(TestWalks.segment(11)))
        val walks = FakeActiveWalkRepository(walk)

        val state = session(walks).notificationStates.first()

        assertEquals(WalkNotificationState(ActiveWalkStatus.Recording, 111, walk.startedAt.toEpochMilli()), state)
    }

    @Test
    fun while_recording_it_also_shares_and_pausing_says_goodbye() = runTest {
        settings.settings.value = UserSettings(LiveVisibility.Everyone, hideStartDistanceM = 0)
        val walks = FakeActiveWalkRepository(recording())
        run(walks)
        runCurrent()

        location.send(fix(4_000))
        runCurrent()
        assertTrue(live.calls.any { it.startsWith("position") })
        assertEquals(1, live.connectionUsers)

        walks.pause()
        runCurrent()
        assertEquals("stop", live.calls.last())
        assertEquals(0, live.connectionUsers)
        assertEquals(0, location.listeners)
    }

    @Test
    fun finishing_says_goodbye_to_the_map() = runTest {
        settings.settings.value = UserSettings(LiveVisibility.Everyone, hideStartDistanceM = 0)
        val walks = FakeActiveWalkRepository(recording())
        val running = run(walks)
        runCurrent()
        location.send(fix(4_000))
        runCurrent()

        walks.finish()
        runCurrent()

        assertEquals("stop", live.calls.last())
        assertFalse(running.isActive)
    }

    @Test
    fun the_map_failing_does_not_touch_the_route() = runTest {
        settings.settings.value = UserSettings(LiveVisibility.Everyone, hideStartDistanceM = 0)
        live.startResult = Outcome.Failure(AppError.Network)
        val walks = FakeActiveWalkRepository(recording())
        run(walks)
        runCurrent()

        location.send(fix(4_000))
        location.send(fix(10_000, metersNorth = 10.0))
        runCurrent()

        assertEquals(2, checkNotNull(walks.value).segments.single().points.size)
        assertEquals(ActiveWalkStatus.Recording, walks.value?.status)
    }

    @Test
    fun nothing_is_shared_without_having_chosen_and_the_walk_is_recorded_all_the_same() = runTest {
        settings.settings.value = UserSettings(liveVisibility = null, hideStartDistanceM = 0)
        val walks = FakeActiveWalkRepository(recording())
        run(walks)
        runCurrent()

        location.send(fix(4_000))
        runCurrent()

        assertTrue(live.calls.isEmpty())
        assertEquals(1, checkNotNull(walks.value).segments.single().points.size)
    }
}
