package com.geowoof.core.datastore

import androidx.datastore.core.CorruptionException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class AppearanceSerializerTest {
    private fun read(text: String) = ByteArrayInputStream(text.encodeToByteArray())

    @Test
    fun a_new_device_follows_the_system_with_the_colors_of_the_brand() {
        val appearance = AppearanceSerializer.defaultValue

        assertEquals("System", appearance.themeMode)
        assertFalse(appearance.useWallpaperColors)
    }

    @Test
    fun what_is_written_is_read_back() = runTest {
        val appearance = StoredAppearance(themeMode = "Dark", useWallpaperColors = true)
        val output = ByteArrayOutputStream()

        AppearanceSerializer.writeTo(appearance, output)

        assertEquals(appearance, AppearanceSerializer.readFrom(ByteArrayInputStream(output.toByteArray())))
    }

    @Test
    fun what_a_later_version_added_does_not_lose_the_rest() = runTest {
        val stored = AppearanceSerializer.readFrom(read("""{"themeMode":"Light","somethingNew":1}"""))

        assertEquals(StoredAppearance(themeMode = "Light"), stored)
    }

    @Test(expected = CorruptionException::class)
    fun a_broken_file_is_reported_as_corrupt() = runTest {
        AppearanceSerializer.readFrom(read("{not json"))
    }
}
