package com.geowoof.core.domain.walks

import com.geowoof.core.model.GeoPoint
import com.geowoof.core.model.LocationFix
import com.geowoof.core.testing.TestWalks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

class WalkFiltersTest {
    private val filter = FilterLocationUseCase()
    private val smooth = SmoothRouteUseCase()
    private val computeStats = ComputeWalkStatsUseCase()

    private val here = GeoPoint(40.4, -3.7)
    private val anchor = TrackAnchor(here, timeEpochMs = 10_000)

    private fun fix(metersNorth: Double = 0.0, accuracyM: Float = 5f, timeEpochMs: Long = 14_000) =
        LocationFix(GeoPoint(here.latitude + metersNorth / 111_195, here.longitude), accuracyM, timeEpochMs)

    @Test
    fun a_segment_waits_for_a_good_first_position() {
        assertEquals(FixRejection.PoorFirstFix, filter.rejection(fix(accuracyM = 20.1f), anchor = null))
        assertNull(filter.rejection(fix(accuracyM = 20f), anchor = null))
    }

    @Test
    fun positions_with_poor_accuracy_are_discarded() {
        assertEquals(FixRejection.PoorAccuracy, filter.rejection(fix(accuracyM = 25.1f), anchor))
        assertNull(filter.rejection(fix(accuracyM = 25f), anchor))
    }

    @Test
    fun jumps_at_an_impossible_speed_are_discarded() {
        assertEquals(FixRejection.ImpossibleSpeed, filter.rejection(fix(metersNorth = 35.0), anchor))
        assertNull(filter.rejection(fix(metersNorth = 33.0), anchor))
    }

    @Test
    fun a_jump_is_accepted_once_enough_time_has_passed() {
        assertNull(filter.rejection(fix(metersNorth = 500.0, timeEpochMs = 100_000), anchor))
    }

    @Test
    fun a_position_older_than_the_last_one_is_discarded() {
        assertEquals(FixRejection.OutOfOrder, filter.rejection(fix(timeEpochMs = 10_000), anchor))
    }

    @Test
    fun small_moves_are_noise() {
        assertFalse(filter.isRealMove(fix(metersNorth = 2.9).position, 5f, anchor))
        assertTrue(filter.isRealMove(fix(metersNorth = 3.1).position, 5f, anchor))

        // With a poor accuracy it takes half of it to count as a move.
        assertFalse(filter.isRealMove(fix(metersNorth = 7.0).position, 15f, anchor))
        assertTrue(filter.isRealMove(fix(metersNorth = 8.0).position, 15f, anchor))
    }

    @Test
    fun the_first_position_is_taken_as_it_is() {
        val first = smooth(null, fix(accuracyM = 10f))

        assertEquals(here, first.position)
        assertEquals(100.0, first.varianceM2, 0.0)
    }

    @Test
    fun an_imprecise_position_moves_the_estimate_less_than_a_precise_one() {
        val previous = SmoothedPosition(here, varianceM2 = 25.0, timeEpochMs = 10_000)

        val precise = smooth(previous, fix(metersNorth = 10.0, accuracyM = 3f))
        val imprecise = smooth(previous, fix(metersNorth = 10.0, accuracyM = 20f))

        assertTrue(precise.position.latitude > imprecise.position.latitude)
        assertTrue(imprecise.position.latitude > here.latitude)
    }

    @Test
    fun after_a_long_gap_the_estimate_trusts_the_new_position() {
        val previous = SmoothedPosition(here, varianceM2 = 25.0, timeEpochMs = 0)

        val next = smooth(previous, fix(metersNorth = 100.0, accuracyM = 5f, timeEpochMs = 3_600_000))

        assertEquals(here.latitude + 100.0 / 111_195, next.position.latitude, 1e-5)
    }

    @Test
    fun a_steady_walk_has_the_same_numbers_as_on_the_server() {
        val stats = computeStats(listOf(TestWalks.segment(11)), durationMs = 100_000)

        assertEquals(111, stats.distanceM)
        assertEquals(100, stats.durationS)
        assertEquals(80, stats.movingTimeS)
        assertEquals(1.39, stats.averageSpeedMps, 0.0)
        assertEquals(719, stats.paceSecondsPerKm)
    }

    @Test
    fun a_pause_adds_neither_distance_nor_moving_time() {
        val segments = listOf(TestWalks.segment(6), TestWalks.segment(6, firstOffsetMs = 600_000, latitude = 41.0))

        val stats = computeStats(segments, Duration.ofMinutes(11).toMillis())

        assertEquals(111, stats.distanceM)
        assertEquals(80, stats.movingTimeS)
    }

    @Test
    fun without_movement_there_is_no_speed_or_pace() {
        val stats = computeStats(listOf(TestWalks.segment(1)), durationMs = 4000)

        assertEquals(0, stats.distanceM)
        assertEquals(0.0, stats.averageSpeedMps, 0.0)
        assertNull(stats.paceSecondsPerKm)
    }
}
