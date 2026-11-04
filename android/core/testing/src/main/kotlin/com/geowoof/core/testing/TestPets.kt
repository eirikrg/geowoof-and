package com.geowoof.core.testing

import com.geowoof.core.model.Pet
import com.geowoof.core.model.PetSex
import com.geowoof.core.model.PetSize
import com.geowoof.core.model.PetSpecies
import com.geowoof.core.model.Profile
import com.geowoof.core.model.RemoteImage
import java.time.Instant
import java.time.LocalDate

object TestPets {
    private val CREATED_AT: Instant = Instant.parse("2026-11-01T10:00:00Z")
    private val BIRTH_DATE: LocalDate = LocalDate.of(2021, 4, 12)

    fun profile(
        displayName: String = "Ana",
        username: String? = "ana",
        bio: String? = null,
        city: String? = null,
        avatar: RemoteImage? = null,
    ) = Profile(
        id = "0193f0c2-6f1e-7cc4-9a2b-3c1d5e7f9a10",
        username = username,
        displayName = displayName,
        bio = bio,
        city = city,
        avatar = avatar,
        createdAt = CREATED_AT,
    )

    /** Every field has a value: each test names only what matters to it. */
    @Suppress("LongParameterList")
    fun pet(
        id: String = "luna",
        name: String = "Luna",
        species: PetSpecies = PetSpecies.Dog,
        speciesOther: String? = null,
        breed: String? = "Border collie",
        birthDate: LocalDate? = BIRTH_DATE,
        sex: PetSex = PetSex.Female,
        size: PetSize? = PetSize.Medium,
        color: String? = "Blanco y negro",
        neutered: Boolean = true,
        microchip: String? = "941000024567890",
        avatar: RemoteImage? = null,
        bio: String? = "Le encanta el agua",
    ) = Pet(
        id = id,
        ownerId = "0193f0c2-6f1e-7cc4-9a2b-3c1d5e7f9a10",
        name = name,
        species = species,
        speciesOther = speciesOther,
        breed = breed,
        birthDate = birthDate,
        sex = sex,
        size = size,
        color = color,
        neutered = neutered,
        microchip = microchip,
        avatar = avatar,
        bio = bio,
        createdAt = CREATED_AT,
        updatedAt = CREATED_AT,
    )
}
