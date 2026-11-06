package com.geowoof.core.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoZoomTest {
    /** A screen of 1000 × 2000 and a photo of its same width, 1000 × 1000, which leaves black above and below. */
    private val zoom = PhotoZoom().apply {
        containerSize = Size(1000f, 2000f)
        imageSize = Size(1000f, 1000f)
    }
    private val centre = Offset(500f, 1000f)

    @Test
    fun it_starts_with_the_whole_photo() {
        assertEquals(1f, zoom.scale)
        assertEquals(Offset.Zero, zoom.offset)
        assertFalse(zoom.isZoomed)
    }

    @Test
    fun two_fingers_enlarge_it() {
        zoom.transform(centre, Offset.Zero, gestureZoom = 2f)

        assertEquals(2f, zoom.scale)
        assertTrue(zoom.isZoomed)
    }

    @Test
    fun it_cannot_be_smaller_than_the_whole_photo_or_bigger_than_the_limit() {
        zoom.transform(centre, Offset.Zero, gestureZoom = 0.2f)
        assertEquals(1f, zoom.scale)

        zoom.transform(centre, Offset.Zero, gestureZoom = 100f)
        assertEquals(5f, zoom.scale)
    }

    @Test
    fun the_point_under_the_fingers_stays_under_them() {
        val fingers = Offset(800f, 1000f)

        zoom.transform(fingers, Offset.Zero, gestureZoom = 2f)

        // That point was 300 from the centre: now it would be 600, and the photo moves 300 back.
        assertEquals(Offset(-300f, 0f), zoom.offset)
    }

    @Test
    fun it_does_not_move_past_the_edges_of_the_photo() {
        zoom.moveTo(2f, Offset(5000f, 0f))

        // At 2× it is 2000 wide in a screen of 1000: 500 at each side.
        assertEquals(500f, zoom.offset.x)
    }

    @Test
    fun where_the_photo_still_fits_in_the_screen_it_does_not_move_at_all() {
        zoom.moveTo(2f, Offset(0f, 5000f))

        // At 2× it is 2000 high in a screen of 2000.
        assertEquals(0f, zoom.offset.y)
    }

    @Test
    fun before_knowing_the_photo_it_uses_the_screen_for_the_edges() {
        val unknown = PhotoZoom().apply { containerSize = Size(1000f, 2000f) }

        unknown.moveTo(2f, Offset(0f, 5000f))

        assertEquals(1000f, unknown.offset.y)
    }

    @Test
    fun a_double_tap_goes_closer_to_that_point() {
        val (scale, offset) = zoom.doubleTapTarget(Offset(600f, 1000f))

        assertEquals(2.5f, scale)
        assertEquals(-150f, offset.x, 0f)
        assertEquals(0f, offset.y, 0f)
    }

    @Test
    fun a_double_tap_on_an_enlarged_photo_goes_back_to_the_whole_of_it() {
        zoom.moveTo(3f, Offset(100f, 100f))

        val (scale, offset) = zoom.doubleTapTarget(centre)

        assertEquals(1f, scale)
        assertEquals(Offset.Zero, offset)
    }
}
