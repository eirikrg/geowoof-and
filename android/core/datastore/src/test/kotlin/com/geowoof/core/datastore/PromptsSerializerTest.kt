package com.geowoof.core.datastore

import androidx.datastore.core.CorruptionException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class PromptsSerializerTest {
    @Test
    fun a_new_device_has_not_been_asked_anything() {
        assertFalse(PromptsSerializer.defaultValue.askedForNotifications)
    }

    @Test
    fun what_is_written_is_read_back() = runTest {
        val prompts = StoredPrompts(askedForNotifications = true)
        val output = ByteArrayOutputStream()

        PromptsSerializer.writeTo(prompts, output)

        assertEquals(prompts, PromptsSerializer.readFrom(ByteArrayInputStream(output.toByteArray())))
    }

    @Test
    fun a_device_that_only_answered_about_notifications_has_not_been_asked_for_its_location() = runTest {
        val saved = """{"askedForNotifications":true}""".encodeToByteArray()

        val prompts = PromptsSerializer.readFrom(ByteArrayInputStream(saved))

        assertEquals(StoredPrompts(askedForNotifications = true, askedForLocation = false), prompts)
    }

    @Test(expected = CorruptionException::class)
    fun a_broken_file_is_reported_as_corrupt() = runTest {
        PromptsSerializer.readFrom(ByteArrayInputStream("{not json".encodeToByteArray()))
    }
}
