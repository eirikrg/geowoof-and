package com.geowoof.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A value that the server learned after this app was installed is never an exception. */
class ServerValuesTest {
    @Test
    fun a_known_value_is_read_by_its_name() {
        assertEquals(PetSex.Female, "Female".toEnumOr(PetSex.Unknown))
        assertEquals(PetSex.Male, "Male".toEnumOrNull<PetSex>())
    }

    @Test
    fun an_unknown_one_is_the_fallback_or_nothing() {
        assertEquals(PetSex.Unknown, "Neutered".toEnumOr(PetSex.Unknown))
        assertNull("Neutered".toEnumOrNull<PetSex>())
        assertNull((null as String?).toEnumOrNull<PetSex>())
        assertEquals(PetSex.Unknown, (null as String?).toEnumOr(PetSex.Unknown))
    }

    @Test
    fun the_name_has_to_be_the_same_as_the_api_writes_it() {
        assertNull("female".toEnumOrNull<PetSex>())
        assertNull("".toEnumOrNull<PetSex>())
    }
}
