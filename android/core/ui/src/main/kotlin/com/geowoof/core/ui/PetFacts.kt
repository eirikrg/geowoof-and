package com.geowoof.core.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.core.model.Pet

/**
 * What is known of a pet, in tiles: a long list made you scroll past it to reach the walks. The page of your pet and
 * the pet of someone else in a profile show the same. Only the owner receives the microchip, so only they see it.
 * Over a sheet, which already has the color of the tiles, they take [tileColor] to be seen.
 */
@Composable
fun PetFacts(
    pet: Pet,
    modifier: Modifier = Modifier,
    tileColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
) {
    val spacing = GeoWoofTheme.spacing
    val facts = buildList {
        add(R.string.core_ui_pet_sex_label to stringResource(pet.sex.labelRes()))
        pet.birthDate?.let { add(R.string.core_ui_pet_birth_date_label to it.formatted()) }
        pet.size?.let { add(R.string.core_ui_pet_size_label to stringResource(it.labelRes())) }
        pet.color?.let { add(R.string.core_ui_pet_color_label to it) }
        add(
            R.string.core_ui_pet_neutered_label to
                stringResource(if (pet.neutered) R.string.core_ui_yes else R.string.core_ui_no),
        )
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.large),
        verticalArrangement = Arrangement.spacedBy(spacing.small),
    ) {
        facts.chunked(FACTS_PER_ROW).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                row.forEach { (label, value) -> Fact(label, value, tileColor, Modifier.weight(1f)) }
                // A row that is not full keeps the width of its tile.
                if (row.size < FACTS_PER_ROW) Spacer(Modifier.weight(1f))
            }
        }
        pet.microchip?.let { microchip ->
            Fact(
                label = R.string.core_ui_pet_microchip_label,
                value = microchip,
                color = tileColor,
                note = stringResource(R.string.core_ui_pet_microchip_private),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun Fact(
    @StringRes label: Int,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
    note: String? = null,
) {
    Surface(
        modifier = modifier.semantics(mergeDescendants = true) {},
        shape = MaterialTheme.shapes.medium,
        color = color,
    ) {
        Column(modifier = Modifier.padding(GeoWoofTheme.spacing.medium)) {
            Text(
                text = stringResource(label),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(value, style = MaterialTheme.typography.titleSmall)
            note?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private const val FACTS_PER_ROW = 2
