package com.geowoof.core.ui

import com.geowoof.core.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WalkFormatTest {
    @Test
    fun a_duration_only_shows_the_hours_when_there_are_any() {
        assertEquals("0:00", formatDuration(0))
        assertEquals("42:10", formatDuration(2530))
        assertEquals("1:05:09", formatDuration(3909))
    }

    @Test
    fun a_date_is_shown_where_the_walk_happened_even_with_an_unknown_zone() {
        val instant = java.time.Instant.parse("2026-11-01T23:30:00Z")

        assertTrue(
            instant.formattedIn("Europe/Madrid").contains("00:30") ||
                instant.formattedIn("Europe/Madrid").contains("12:30"),
        )
        assertTrue(instant.formattedIn("Marte/Olympus").isNotEmpty())
    }

    @Test
    fun a_route_fits_in_the_area_with_north_up() {
        val south = GeoPoint(40.0, -3.0)
        val north = GeoPoint(40.01, -3.0)

        val fitted = fitRoute(listOf(listOf(south, north)), width = 100f, height = 200f, padding = 10f).single()

        assertEquals(50f, fitted[0].x, 0.01f)
        assertEquals(190f, fitted[0].y, 0.01f)
        assertEquals(10f, fitted[1].y, 0.01f)
    }

    @Test
    fun a_route_keeps_its_proportions_and_its_stretches() {
        val first = listOf(GeoPoint(0.0, 0.0), GeoPoint(0.0, 1.0))
        val second = listOf(GeoPoint(0.5, 0.0), GeoPoint(0.5, 1.0))

        val fitted = fitRoute(listOf(first, second), width = 100f, height = 100f, padding = 0f)

        assertEquals(2, fitted.size)
        assertEquals(100f, fitted[0][1].x - fitted[0][0].x, 0.01f)
        assertEquals(50f, fitted[0][0].y - fitted[1][0].y, 0.01f)
    }

    @Test
    fun a_single_point_is_centered_and_no_route_draws_nothing() {
        val fitted = fitRoute(listOf(listOf(GeoPoint(40.0, -3.0))), width = 100f, height = 100f, padding = 10f)

        assertEquals(50f, fitted.single().single().x, 0.01f)
        assertEquals(50f, fitted.single().single().y, 0.01f)
        assertEquals(emptyList<Any>(), fitRoute(emptyList(), 100f, 100f, 10f))
    }
}
