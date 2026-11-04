package com.geowoof.core.domain

import com.geowoof.core.model.MicrochipIssue
import com.geowoof.core.model.PetDateIssue
import com.geowoof.core.model.PetDraft
import com.geowoof.core.model.PetIssues
import com.geowoof.core.model.PetNameIssue
import com.geowoof.core.model.PetSpecies
import com.geowoof.core.model.ProfileDraft
import com.geowoof.core.model.ProfileIssues
import com.geowoof.core.model.SpeciesOtherIssue
import com.geowoof.core.model.TextIssue
import com.geowoof.core.model.WeightIssue
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject

// Client-side mirrors of the API rules (docs/api.md, phase 2), for instant feedback. The API stays
// authoritative: these never replace its validation.

private fun String?.tooLong(max: Int): TextIssue? = if (this != null && trim().length > max) TextIssue.TooLong else null

class ValidateProfileUseCase @Inject constructor(private val validateDisplayName: ValidateDisplayNameUseCase) {
    operator fun invoke(draft: ProfileDraft) = ProfileIssues(
        displayName = validateDisplayName(draft.displayName),
        bio = draft.bio.tooLong(BIO_MAX_LENGTH),
        city = draft.city.tooLong(CITY_MAX_LENGTH),
    )

    private companion object {
        const val BIO_MAX_LENGTH = 300
        const val CITY_MAX_LENGTH = 100
    }
}

class ValidatePetUseCase @Inject constructor() {
    operator fun invoke(draft: PetDraft, today: LocalDate = LocalDate.now()) = PetIssues(
        name = when {
            draft.name.isBlank() -> PetNameIssue.Empty
            draft.name.trim().length > NAME_MAX_LENGTH -> PetNameIssue.TooLong
            else -> null
        },
        speciesOther = when {
            draft.species != PetSpecies.Other -> null
            draft.speciesOther.isNullOrBlank() -> SpeciesOtherIssue.Empty
            draft.speciesOther!!.trim().length > SPECIES_OTHER_MAX_LENGTH -> SpeciesOtherIssue.TooLong
            else -> null
        },
        breed = draft.breed.tooLong(BREED_MAX_LENGTH),
        birthDate = draft.birthDate?.let { validatePetDate(it, today) },
        color = draft.color.tooLong(COLOR_MAX_LENGTH),
        microchip = microchipIssue(draft.microchip?.trim()),
        bio = draft.bio.tooLong(BIO_MAX_LENGTH),
    )

    private fun microchipIssue(microchip: String?): MicrochipIssue? = when {
        microchip.isNullOrEmpty() -> null
        microchip.length > MICROCHIP_MAX_LENGTH -> MicrochipIssue.TooLong
        !microchip.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' } -> MicrochipIssue.InvalidCharacters
        else -> null
    }

    private companion object {
        const val NAME_MAX_LENGTH = 50
        const val SPECIES_OTHER_MAX_LENGTH = 50
        const val BREED_MAX_LENGTH = 80
        const val COLOR_MAX_LENGTH = 50
        const val MICROCHIP_MAX_LENGTH = 32
        const val BIO_MAX_LENGTH = 500
    }
}

/** Birth dates and measurements cannot be in the future or before 1990. */
fun validatePetDate(date: LocalDate, today: LocalDate = LocalDate.now()): PetDateIssue? = when {
    date.isAfter(today) -> PetDateIssue.InTheFuture
    date.isBefore(EARLIEST_PET_DATE) -> PetDateIssue.TooOld
    else -> null
}

private val EARLIEST_PET_DATE: LocalDate = LocalDate.of(1990, 1, 1)

/** The result of reading what the user typed: either a weight or why it is not valid. */
data class ParsedWeight(val weightKg: Double?, val issue: WeightIssue?)

class ParseWeightUseCase @Inject constructor() {
    /** Accepts both decimal separators, because people type what their keyboard shows ("18,4"). */
    operator fun invoke(text: String): ParsedWeight {
        val value =
            text.trim().replace(',', '.').toBigDecimalOrNull() ?: return ParsedWeight(null, WeightIssue.NotANumber)
        return when {
            value <= BigDecimal.ZERO || value > MAX_WEIGHT_KG -> ParsedWeight(null, WeightIssue.OutOfRange)
            value.stripTrailingZeros().scale() > MAX_DECIMALS -> ParsedWeight(null, WeightIssue.TooManyDecimals)
            else -> ParsedWeight(value.toDouble(), null)
        }
    }

    private companion object {
        val MAX_WEIGHT_KG = BigDecimal(200)
        const val MAX_DECIMALS = 2
    }
}
