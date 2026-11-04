package com.geowoof.core.data.media

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageSizingTest {
    @Test
    fun big_images_are_reduced_to_the_maximum_on_their_longest_side() {
        assertEquals(2048 to 1536, ImageSizing.fitWithin(4000, 3000))
        assertEquals(1536 to 2048, ImageSizing.fitWithin(3000, 4000))
        assertEquals(2048 to 2048, ImageSizing.fitWithin(5000, 5000))
    }

    @Test
    fun small_images_are_never_enlarged() {
        assertEquals(800 to 600, ImageSizing.fitWithin(800, 600))
        assertEquals(2048 to 100, ImageSizing.fitWithin(2048, 100))
    }

    @Test
    fun very_thin_images_keep_at_least_one_pixel() {
        assertEquals(2048 to 1, ImageSizing.fitWithin(100_000, 3))
    }

    @Test
    fun decoding_saves_memory_without_going_below_the_final_size() {
        assertEquals(1, ImageSizing.inSampleSize(2048, 1536))
        assertEquals(1, ImageSizing.inSampleSize(4000, 3000))
        assertEquals(2, ImageSizing.inSampleSize(4096, 3072))
        assertEquals(4, ImageSizing.inSampleSize(9000, 6000))
    }
}
