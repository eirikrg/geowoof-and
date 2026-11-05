package com.geowoof.core.domain.walks

import com.geowoof.core.common.geo.GeoMath
import com.geowoof.core.model.ActiveWalk
import com.geowoof.core.model.ActiveWalkStatus
import com.geowoof.core.model.GeoPoint
import com.geowoof.core.model.LocationFix
import com.geowoof.core.model.Outcome
import com.geowoof.core.model.RouteSegment
import com.geowoof.core.model.WalkVisibility
import com.geowoof.core.testing.FakeActiveWalkRepository
import com.geowoof.core.testing.FakeLocationDataSource
import com.geowoof.core.testing.FakeWalksRepository
import com.geowoof.core.testing.TestFixtures
import com.geowoof.core.testing.TestWalks
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

/** The whole chain with the simulated traces of docs/fixtures, whose truth is known. */
class WalkTrackingTest {
    private val filter = FilterLocationUseCase()
    private val smooth = SmoothRouteUseCase()
    private val computeStats = ComputeWalkStatsUseCase()

    /** Runs a trace through the tracker, one recording run for each of its segments. */
    private fun record(trace: String): ActiveWalk {
        val fixes = TestFixtures.gpx(trace)
        var walk = TestWalks.activeWalk(
            status = ActiveWalkStatus.Preparing,
            segments = emptyList(),
            startedAt = Instant.ofEpochMilli(fixes.first().first().timeEpochMs),
        )
        fixes.forEachIndexed { index, segment ->
            val tracker = WalkPointTracker(walk, filter, smooth)
            val points = segment.mapNotNull { fix -> (tracker.process(fix) as? TrackStep.Keep)?.point }
            walk =
                walk.copy(
                    status = ActiveWalkStatus.Recording,
                    segments = walk.segments + RouteSegment(points),
                    currentSegment = index,
                )
        }

        return walk
    }

    private fun ActiveWalk.durationMs() = segments.last().points.last().offsetMs

    private fun allOffsets(walk: ActiveWalk) = walk.segments.flatMap { segment -> segment.points.map { it.offsetMs } }

    @Test
    fun a_normal_walk_measures_what_was_walked() {
        val walk = record("normal_walk")

        val stats = computeStats(walk.segments, walk.durationMs())

        // 449 steps of 5.6 m.
        assertInRange(stats.distanceM, 2514 * 0.9, 2514 * 1.1)
        assertInRange(stats.movingTimeS, 1500.0, 1796.0)
    }

    @Test
    fun standing_still_with_gps_noise_does_not_add_distance() {
        val walk = record("stationary_noise")

        val stats = computeStats(walk.segments, durationMs = Duration.ofMinutes(10).toMillis())

        // Without the filter, the noise of these 10 minutes would add up to more than 600 m.
        val unfiltered = TestFixtures.gpx("stationary_noise").single().zipWithNext { from, to ->
            GeoMath.distanceMeters(from.position, to.position)
        }.sum()
        assertTrue("The noise of the trace adds up to $unfiltered m", unfiltered > 600)
        assertTrue("It walked ${stats.distanceM} m without moving", stats.distanceM < 40)
    }

    @Test
    fun jumps_and_positions_with_poor_accuracy_are_left_out() {
        val walk = record("tunnel_jumps")

        val stats = computeStats(walk.segments, walk.durationMs())

        // 239 steps of 5.6 m, in a straight line through the tunnel; each jump alone would add more than 500 m.
        assertInRange(stats.distanceM, 1338 * 0.85, 1338 * 1.15)
        val longest = walk.segments.single().points.zipWithNext { from, to -> to.offsetMs - from.offsetMs }.max()
        assertTrue("The gap of the tunnel is $longest ms", longest >= 120_000)
    }

    @Test
    fun a_pause_is_not_part_of_the_route() {
        val walk = record("pause_walk")

        val stats = computeStats(walk.segments, walk.durationMs())

        // 119 + 119 steps of 5.6 m: during the 10 minutes of the pause it moved another 670 m.
        assertEquals(2, walk.segments.size)
        assertInRange(stats.distanceM, 1333 * 0.9, 1333 * 1.1)
        assertTrue(stats.durationS - stats.movingTimeS >= 600)
    }

    @Test
    fun the_offsets_always_grow_as_the_server_requires() {
        for (trace in listOf("normal_walk", "stationary_noise", "tunnel_jumps", "pause_walk")) {
            val offsets = allOffsets(record(trace))
            assertEquals(trace, offsets.sorted().distinct(), offsets)
        }
    }

    @Test
    fun the_walk_starts_with_the_first_precise_position() = runTest {
        val (location, walks) = recording(
            TestWalks.activeWalk(status = ActiveWalkStatus.Preparing, segments = emptyList()),
        )

        location.send(fix(accuracyM = 50f, timeEpochMs = 1_000))
        assertEquals(ActiveWalkStatus.Preparing, walks.value?.status)

        location.send(fix(accuracyM = 10f, timeEpochMs = 5_000))

        val walk = checkNotNull(walks.value)
        assertEquals(ActiveWalkStatus.Recording, walk.status)
        assertEquals(Instant.ofEpochMilli(5_000), walk.startedAt)
        assertEquals(0L, walk.segments.single().points.single().offsetMs)
    }

    @Test
    fun a_resumed_walk_writes_to_its_new_segment_after_the_last_point() = runTest {
        val before = TestWalks.segment(3)
        val (location, walks) = recording(
            TestWalks.activeWalk(segments = listOf(before), currentSegment = 1, startedAt = Instant.ofEpochMilli(0)),
        )

        location.send(fix(timeEpochMs = 100_000))

        val walk = checkNotNull(walks.value)
        assertEquals(2, walk.segments.size)
        assertEquals(100_000L, walk.segments[1].points.single().offsetMs)
    }

    @Test
    fun a_walk_pauses_by_itself_when_the_server_would_not_accept_more() = runTest {
        val (location, walks) = recording(TestWalks.activeWalk(startedAt = Instant.ofEpochMilli(0)))

        location.send(fix(timeEpochMs = Duration.ofHours(12).toMillis() + 1))

        assertEquals(ActiveWalkStatus.Paused, walks.value?.status)
    }

    @Test
    fun saving_keeps_the_segments_with_a_line_and_the_numbers_of_the_route() = runTest {
        val walks = FakeWalksRepository()
        val now = Instant.parse("2026-11-02T09:00:00Z")
        val save = SaveWalkUseCase(walks, computeStats, Clock.fixed(now, ZoneOffset.UTC))
        val start = TestWalks.START
        val walk = TestWalks.activeWalk(
            status = ActiveWalkStatus.Finished,
            segments = listOf(TestWalks.segment(11), TestWalks.segment(1, firstOffsetMs = 200_000)),
            endedAt = start.plusSeconds(300),
        )

        val result = save(walk, WalkVisibility.OnlyMe, "  Por el Retiro ", listOf("content://photo"))

        assertEquals(Outcome.Success(walk.id), result)
        val saved = walks.saved.single()
        assertEquals(1, saved.segments.size)
        assertEquals(11, saved.segments.single().timeOffsetsMs.size)
        assertEquals(111, saved.stats.distanceM)
        assertEquals(300, saved.stats.durationS)
        assertEquals("Por el Retiro", saved.note)
        assertEquals(WalkVisibility.OnlyMe, saved.visibility)
        assertEquals(now, saved.updatedAt)
        assertEquals(listOf("content://photo"), saved.photoUris)
        assertNotNull(saved.segments.single().polyline)
    }

    private fun TestScope.recording(walk: ActiveWalk): Pair<FakeLocationDataSource, FakeActiveWalkRepository> {
        val location = FakeLocationDataSource()
        val walks = FakeActiveWalkRepository(walk)
        val track = TrackWalkUseCase(location, walks, filter, smooth)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { track(walk) }
        return location to walks
    }

    private fun fix(accuracyM: Float = 5f, timeEpochMs: Long) =
        LocationFix(GeoPoint(40.4, -3.7), accuracyM, timeEpochMs)

    private fun assertInRange(value: Int, min: Double, max: Double) {
        assertTrue("$value is not between $min and $max", value in min.toInt()..max.toInt())
    }
}
