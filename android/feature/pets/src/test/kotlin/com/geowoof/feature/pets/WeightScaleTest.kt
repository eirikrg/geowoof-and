package com.geowoof.feature.pets

import com.geowoof.feature.pets.detail.formatWeight
import com.geowoof.feature.pets.detail.weightScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class WeightScaleTest {
    @Test
    fun a_small_change_fills_the_chart_with_round_limits() {
        val scale = weightScale(listOf(17.8, 18.83, 19.01))

        assertEquals(17.5, scale.min, 0.001)
        assertEquals(19.5, scale.max, 0.001)
        assertEquals(0.5, scale.step, 0.001)
    }

    @Test
    fun a_weight_that_does_not_change_is_in_the_middle_and_not_on_an_edge() {
        val scale = weightScale(listOf(18.0, 18.0))

        assertTrue(scale.min < 18.0)
        assertTrue(scale.max > 18.0)
    }

    @Test
    fun a_puppy_that_grows_a_lot_keeps_few_lines_and_no_negative_weight() {
        val scale = weightScale(listOf(3.0, 30.0))

        assertTrue((scale.max - scale.min) / scale.step <= 4.0)
        assertTrue(scale.min >= 0.0)
        assertTrue(scale.max >= 30.0)
    }

    @Test
    fun a_very_small_pet_can_see_its_decimals() {
        val scale = weightScale(listOf(0.42, 0.51))

        assertEquals(0.1, scale.step, 0.001)
    }

    @Test
    fun the_biggest_weight_allowed_still_fits() {
        val scale = weightScale(listOf(0.01, 200.0))

        assertEquals(200.0, scale.max, 0.001)
        assertEquals(50.0, scale.step, 0.001)
    }

    @Test
    fun a_weight_is_written_in_the_way_of_each_language_with_the_decimals_it_has() {
        assertEquals("18.5", formatWeight(18.5, Locale.ENGLISH))
        assertEquals("18,5", formatWeight(18.5, Locale.forLanguageTag("es")))
        assertEquals("18", formatWeight(18.0, Locale.ENGLISH))
        assertEquals("1.21", formatWeight(19.01 - 17.8, Locale.ENGLISH))
    }
}
