package com.geowoof.feature.pets.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.PreviewFontScale
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geowoof.core.designsystem.component.FittedText
import com.geowoof.core.designsystem.component.FormTextField
import com.geowoof.core.designsystem.component.ProgressButton
import com.geowoof.core.designsystem.icon.GeoWoofIcons
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.core.model.MicrochipIssue
import com.geowoof.core.model.PetNameIssue
import com.geowoof.core.model.PetSex
import com.geowoof.core.model.PetSize
import com.geowoof.core.model.PetSpecies
import com.geowoof.core.model.SpeciesOtherIssue
import com.geowoof.core.model.TextIssue
import com.geowoof.core.ui.FormError
import com.geowoof.core.ui.FormScaffold
import com.geowoof.core.ui.ImageVariant
import com.geowoof.core.ui.RemoteAvatar
import com.geowoof.core.ui.formatted
import com.geowoof.core.ui.labelRes
import com.geowoof.core.ui.messageRes
import com.geowoof.core.ui.rememberPhotoPicker
import com.geowoof.feature.pets.PetDateDialog
import com.geowoof.feature.pets.R
import java.time.LocalDate

// The small pieces of the pet form.

@Composable
internal fun PhotoPicker(
    uiState: PetEditorUiState,
    onPickPhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(GeoWoofTheme.spacing.medium, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RemoteAvatar(
            image = uiState.avatar,
            fallbackIcon = GeoWoofIcons.AddAPhoto,
            contentDescription = stringResource(R.string.feature_pets_photo),
            size = 96.dp,
            variant = ImageVariant.Medium,
            localUri = uiState.pickedImageUri,
        )
        TextButton(onClick = onPickPhoto) { Text(stringResource(R.string.feature_pets_choose_photo)) }
        if (uiState.hasPhoto) {
            TextButton(onClick = onRemovePhoto) { Text(stringResource(R.string.feature_pets_remove_photo)) }
        }
    }
}

@Composable
internal fun SpeciesSelector(selected: PetSpecies, onSelect: (PetSpecies) -> Unit, modifier: Modifier = Modifier) {
    val options = PetSpecies.entries
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        options.forEachIndexed { index, species ->
            SegmentedButton(
                selected = species == selected,
                onClick = { onSelect(species) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) {
                FittedText(stringResource(species.labelRes()))
            }
        }
    }
}

@Composable
internal fun SexSelector(selected: PetSex, onSelect: (PetSex) -> Unit, modifier: Modifier = Modifier) {
    val options = PetSex.entries
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        options.forEachIndexed { index, sex ->
            SegmentedButton(
                selected = sex == selected,
                onClick = { onSelect(sex) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) {
                FittedText(stringResource(sex.labelRes()))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SizeSelector(selected: PetSize?, onSelect: (PetSize) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(GeoWoofTheme.spacing.small),
    ) {
        PetSize.entries.forEach { size ->
            FilterChip(
                selected = size == selected,
                onClick = { onSelect(size) },
                label = { Text(stringResource(size.labelRes())) },
            )
        }
    }
}

@Composable
internal fun BirthDateRow(uiState: PetEditorUiState, onChange: (LocalDate?) -> Unit, modifier: Modifier = Modifier) {
    var picking by rememberSaveable { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(stringResource(R.string.feature_pets_birth_date_label)) },
        supportingContent = {
            val issue = uiState.issues.birthDate
            Text(
                text = when {
                    issue != null -> stringResource(issue.messageRes())
                    else -> uiState.birthDate?.formatted() ?: stringResource(R.string.feature_pets_birth_date_empty)
                },
                color = if (issue !=
                    null
                ) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        },
        leadingContent = { Icon(painterResource(GeoWoofIcons.CalendarMonth), contentDescription = null) },
        trailingContent = {
            if (uiState.birthDate != null) {
                TextButton(onClick = { onChange(null) }) { Text(stringResource(R.string.feature_pets_clear)) }
            }
        },
        modifier = modifier.clickable { picking = true },
    )
    if (picking) {
        PetDateDialog(
            initialDate = uiState.birthDate,
            onConfirm = { date ->
                onChange(date)
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}
