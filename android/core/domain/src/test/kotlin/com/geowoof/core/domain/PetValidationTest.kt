package com.geowoof.core.domain

import com.geowoof.core.model.DisplayNameIssue
import com.geowoof.core.model.MicrochipIssue
import com.geowoof.core.model.PetDateIssue
import com.geowoof.core.model.PetDraft
import com.geowoof.core.model.PetIssues
import com.geowoof.core.model.PetNameIssue
import com.geowoof.core.model.PetSex
import com.geowoof.core.model.PetSpecies
import com.geowoof.core.model.ProfileDraft
import com.geowoof.core.model.SpeciesOtherIssue
import com.geowoof.core.model.TextIssue
import com.geowoof.core.model.WeightIssue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PetValidationTest {
    private val today = LocalDate.of(2026, 11, 5)
    private val validate = ValidatePetUseCase()

    private fun draft() = PetDraft(
        name = "Luna",
        species = PetSpecies.Dog,
        speciesOther = null,
        breed = null,
        birthDate = null,
        sex = PetSex.Unknown,
        size = null,
        color = null,
        neutered = false,
        microchip = null,
        avatarAssetId = null,
        bio = null,
    )

    @Test
    fun a_minimal_pet_is_valid() {
        assertEquals(PetIssues(), validate(draft(), today))
        assertTrue(validate(draft(), today).isValid)
    }

    @Test
    fun a_pet_needs_a_name_of_reasonable_length() {
        assertEquals(PetNameIssue.Empty, validate(draft().copy(name = "  "), today).name)
        assertEquals(PetNameIssue.TooLong, validate(draft().copy(name = "x".repeat(51)), today).name)
        assertFalse(validate(draft().copy(name = ""), today).isValid)
    }

    @Test
    fun the_free_text_species_is_only_required_with_other() {
        assertEquals(SpeciesOtherIssue.Empty, validate(draft().copy(species = PetSpecies.Other), today).speciesOther)
        assertEquals(
            SpeciesOtherIssue.TooLong,
            validate(draft().copy(species = PetSpecies.Other, speciesOther = "x".repeat(51)), today).speciesOther,
        )
        assertNull(validate(draft().copy(species = PetSpecies.Other, speciesOther = "Hurón"), today).speciesOther)
        assertNull(validate(draft().copy(species = PetSpecies.Cat, speciesOther = ""), today).speciesOther)
    }

    @Test
    fun texts_have_a_maximum_length() {
        assertEquals(TextIssue.TooLong, validate(draft().copy(breed = "x".repeat(81)), today).breed)
        assertEquals(TextIssue.TooLong, validate(draft().copy(color = "x".repeat(51)), today).color)
        assertEquals(TextIssue.TooLong, validate(draft().copy(bio = "x".repeat(501)), today).bio)
        assertNull(validate(draft().copy(bio = "x".repeat(500)), today).bio)
    }

    @Test
    fun microchips_only_have_letters_and_digits() {
        assertNull(validate(draft().copy(microchip = "941000024567890"), today).microchip)
        assertNull(validate(draft().copy(microchip = "  "), today).microchip)
        assertEquals(MicrochipIssue.InvalidCharacters, validate(draft().copy(microchip = "94-10"), today).microchip)
        assertEquals(MicrochipIssue.TooLong, validate(draft().copy(microchip = "1".repeat(33)), today).microchip)
    }

    @Test
    fun dates_cannot_be_in_the_future_or_too_old() {
        assertNull(validate(draft().copy(birthDate = today), today).birthDate)
        assertEquals(PetDateIssue.InTheFuture, validate(draft().copy(birthDate = today.plusDays(1)), today).birthDate)
        assertEquals(
            PetDateIssue.TooOld,
            validate(draft().copy(birthDate = LocalDate.of(1989, 12, 31)), today).birthDate,
        )
    }

    @Test
    fun weights_accept_both_decimal_separators() {
        val parse = ParseWeightUseCase()

        assertEquals(18.4, parse("18.4").weightKg)
        assertEquals(18.4, parse(" 18,4 ").weightKg)
        assertEquals(200.0, parse("200").weightKg)
        assertEquals(18.45, parse("18.450").weightKg)
    }

    @Test
    fun wrong_weights_are_explained() {
        val parse = ParseWeightUseCase()

        assertEquals(WeightIssue.NotANumber, parse("").issue)
        assertEquals(WeightIssue.NotANumber, parse("mucho").issue)
        assertEquals(WeightIssue.OutOfRange, parse("0").issue)
        assertEquals(WeightIssue.OutOfRange, parse("-3").issue)
        assertEquals(WeightIssue.OutOfRange, parse("200.01").issue)
        assertEquals(WeightIssue.TooManyDecimals, parse("18.456").issue)
    }

    @Test
    fun the_profile_follows_the_same_rules_as_the_api() {
        val validateProfile = ValidateProfileUseCase(ValidateDisplayNameUseCase())
        val valid = ProfileDraft("Ana", "x".repeat(300), "x".repeat(100), null)

        assertTrue(validateProfile(valid).isValid)
        assertEquals(DisplayNameIssue.Empty, validateProfile(valid.copy(displayName = " ")).displayName)
        assertEquals(TextIssue.TooLong, validateProfile(valid.copy(bio = "x".repeat(301))).bio)
        assertEquals(TextIssue.TooLong, validateProfile(valid.copy(city = "x".repeat(101))).city)
    }
}
