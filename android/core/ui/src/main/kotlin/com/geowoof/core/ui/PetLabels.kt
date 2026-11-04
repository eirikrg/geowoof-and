package com.geowoof.core.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.geowoof.core.model.Pet
import com.geowoof.core.model.PetDateIssue
import com.geowoof.core.model.PetSex
import com.geowoof.core.model.PetSize
import com.geowoof.core.model.PetSpecies
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

// Localised names of the values of a pet, shared by the profile and the pet screens.

@StringRes
fun PetSpecies.labelRes(): Int = when (this) {
    PetSpecies.Dog -> R.string.core_ui_pet_species_dog
    PetSpecies.Cat -> R.string.core_ui_pet_species_cat
    PetSpecies.Other -> R.string.core_ui_pet_species_other
}

@StringRes
fun PetSex.labelRes(): Int = when (this) {
    PetSex.Male -> R.string.core_ui_pet_sex_male
    PetSex.Female -> R.string.core_ui_pet_sex_female
    PetSex.Unknown -> R.string.core_ui_pet_sex_unknown
}

@StringRes
fun PetSize.labelRes(): Int = when (this) {
    PetSize.Small -> R.string.core_ui_pet_size_small
    PetSize.Medium -> R.string.core_ui_pet_size_medium
    PetSize.Large -> R.string.core_ui_pet_size_large
    PetSize.Giant -> R.string.core_ui_pet_size_giant
}

@StringRes
fun PetDateIssue.messageRes(): Int = when (this) {
    PetDateIssue.InTheFuture -> R.string.core_ui_field_date_future
    PetDateIssue.TooOld -> R.string.core_ui_field_date_old
}

/** What a pet is, in a few words: its breed or, without one, its species. */
@Composable
fun Pet.shortDescription(): String = breed ?: speciesOther ?: stringResource(species.labelRes())

/** A date as the user's language writes it. */
fun LocalDate.formatted(): String = format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
