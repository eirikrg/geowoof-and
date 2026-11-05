package com.geowoof.feature.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.geowoof.core.designsystem.icon.GeoWoofIcons
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.core.model.Pet
import com.geowoof.core.ui.ImageVariant
import com.geowoof.core.ui.RemoteAvatar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PetPickerSheet(uiState: MapUiState, actions: MapActions, modifier: Modifier = Modifier) {
    val spacing = GeoWoofTheme.spacing
    ModalBottomSheet(onDismissRequest = actions.onDismissPetPicker, modifier = modifier) {
        Column(
            modifier = Modifier.padding(start = spacing.large, end = spacing.large, bottom = spacing.large),
            verticalArrangement = Arrangement.spacedBy(spacing.medium),
        ) {
            Text(
                text = stringResource(R.string.feature_map_pick_pets_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            when (val onlyPet = uiState.pets.singleOrNull()) {
                null -> if (uiState.pets.isEmpty()) NoPets(actions) else ManyPets(uiState, actions)
                else -> OnePet(onlyPet, actions)
            }
            // The first time it is asked after choosing the pets.
            if (uiState.liveVisibility != null && uiState.pets.isNotEmpty()) {
                LiveVisibilityChip(visibility = uiState.liveVisibility, onClick = actions.onChangeVisibility)
            }
        }
    }
}

@Composable
private fun ColumnScope.NoPets(actions: MapActions) {
    Text(stringResource(R.string.feature_map_no_pets), style = MaterialTheme.typography.bodyMedium)
    Button(onClick = actions.onAddPet, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.feature_map_add_pet))
    }
}

@Composable
private fun ColumnScope.OnePet(pet: Pet, actions: MapActions) {
    PetLine(pet)
    Button(onClick = { actions.onConfirmPets(pet.id) }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.feature_map_start))
    }
}

@Composable
private fun ColumnScope.ManyPets(uiState: MapUiState, actions: MapActions) {
    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        items(uiState.pets, key = Pet::id) { pet ->
            val isSelected = pet.id in uiState.selectedPetIds
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(value = isSelected, role = Role.Checkbox, onValueChange = {
                        actions.onTogglePet(pet.id)
                    })
                    .padding(vertical = GeoWoofTheme.spacing.small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = isSelected, onCheckedChange = null)
                PetLine(pet, modifier = Modifier.padding(start = GeoWoofTheme.spacing.medium))
            }
        }
    }
    Button(
        onClick = { actions.onConfirmPets(null) },
        enabled = uiState.selectedPetIds.isNotEmpty(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.feature_map_start))
    }
}

@Composable
private fun PetLine(pet: Pet, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GeoWoofTheme.spacing.medium),
    ) {
        RemoteAvatar(
            image = pet.avatar,
            fallbackIcon = GeoWoofIcons.Pets,
            contentDescription = null,
            size = PetAvatarSize,
            variant = ImageVariant.Thumb,
        )
        Text(pet.name, style = MaterialTheme.typography.bodyLarge)
    }
}

private val PetAvatarSize = 40.dp
