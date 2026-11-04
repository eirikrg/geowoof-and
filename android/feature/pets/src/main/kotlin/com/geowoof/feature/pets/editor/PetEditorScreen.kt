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

/** Without [onBack] this is an onboarding step, which offers [onSkip] instead. */
@Composable
internal fun PetEditorScreen(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onSkip: (() -> Unit)? = null,
    viewModel: PetEditorViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnDone by rememberUpdatedState(onDone)
    LaunchedEffect(uiState.isDone) {
        if (uiState.isDone) currentOnDone()
    }
    PetEditorContent(
        uiState = uiState,
        fields = PetEditorFields(
            name = viewModel.name,
            speciesOther = viewModel.speciesOther,
            breed = viewModel.breed,
            color = viewModel.color,
            microchip = viewModel.microchip,
            bio = viewModel.bio,
        ),
        actions = PetEditorActions(
            onSpeciesChange = viewModel::onSpeciesChange,
            onSexChange = viewModel::onSexChange,
            onSizeChange = viewModel::onSizeChange,
            onNeuteredChange = viewModel::onNeuteredChange,
            onBirthDateChange = viewModel::onBirthDateChange,
            onRemovePhoto = viewModel::removePhoto,
            onSave = viewModel::save,
        ),
        onPickPhoto = rememberPhotoPicker(viewModel::onImagePicked),
        onBack = onBack,
        onSkip = onSkip,
        modifier = modifier,
    )
}

/** The text fields of the form, held by the ViewModel. */
internal class PetEditorFields(
    val name: TextFieldState,
    val speciesOther: TextFieldState,
    val breed: TextFieldState,
    val color: TextFieldState,
    val microchip: TextFieldState,
    val bio: TextFieldState,
)

@Suppress("LongParameterList")
internal class PetEditorActions(
    val onSpeciesChange: (PetSpecies) -> Unit,
    val onSexChange: (PetSex) -> Unit,
    val onSizeChange: (PetSize) -> Unit,
    val onNeuteredChange: (Boolean) -> Unit,
    val onBirthDateChange: (LocalDate?) -> Unit,
    val onRemovePhoto: () -> Unit,
    val onSave: () -> Unit,
)

@Composable
internal fun PetEditorContent(
    uiState: PetEditorUiState,
    fields: PetEditorFields,
    actions: PetEditorActions,
    onPickPhoto: () -> Unit,
    onBack: (() -> Unit)?,
    onSkip: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val title = stringResource(
        when {
            uiState.isEditing -> R.string.feature_pets_edit_title
            onSkip != null -> R.string.feature_pets_first_title
            else -> R.string.feature_pets_add_title
        },
    )
    FormScaffold(
        title = title,
        modifier = modifier,
        onBack = onBack,
        actions = {
            if (onSkip != null) {
                TextButton(onClick = onSkip) { Text(stringResource(R.string.feature_pets_skip)) }
            }
        },
    ) {
        if (uiState.isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            return@FormScaffold
        }
        PhotoPicker(uiState = uiState, onPickPhoto = onPickPhoto, onRemovePhoto = actions.onRemovePhoto)
        FormTextField(
            state = fields.name,
            label = stringResource(R.string.feature_pets_name_label),
            supportingText = uiState.issues.name?.let { nameMessage(it) },
            isError = uiState.issues.name != null,
            keyboardOptions = WordsKeyboard,
        )
        SpeciesSelector(selected = uiState.species, onSelect = actions.onSpeciesChange)
        if (uiState.species == PetSpecies.Other) {
            FormTextField(
                state = fields.speciesOther,
                label = stringResource(R.string.feature_pets_species_other_label),
                supportingText = uiState.issues.speciesOther?.let { speciesOtherMessage(it) },
                isError = uiState.issues.speciesOther != null,
                keyboardOptions = WordsKeyboard,
            )
        }
        FormTextField(
            state = fields.breed,
            label = stringResource(R.string.feature_pets_breed_label),
            supportingText = uiState.issues.breed?.let { tooLongMessage(it) },
            isError = uiState.issues.breed != null,
            keyboardOptions = WordsKeyboard,
        )
        BirthDateRow(uiState = uiState, onChange = actions.onBirthDateChange)
        SexSelector(selected = uiState.sex, onSelect = actions.onSexChange)
        SizeSelector(selected = uiState.size, onSelect = actions.onSizeChange)
        FormTextField(
            state = fields.color,
            label = stringResource(R.string.feature_pets_color_label),
            supportingText = uiState.issues.color?.let { tooLongMessage(it) },
            isError = uiState.issues.color != null,
            keyboardOptions = WordsKeyboard,
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.feature_pets_neutered_label)) },
            trailingContent = { Switch(checked = uiState.neutered, onCheckedChange = null) },
            modifier = Modifier.clickable { actions.onNeuteredChange(!uiState.neutered) },
        )
        FormTextField(
            state = fields.microchip,
            label = stringResource(R.string.feature_pets_microchip_label),
            supportingText = uiState.issues.microchip?.let { microchipMessage(it) }
                ?: stringResource(R.string.feature_pets_microchip_private),
            isError = uiState.issues.microchip != null,
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Next),
        )
        FormTextField(
            state = fields.bio,
            label = stringResource(R.string.feature_pets_bio_label),
            supportingText = uiState.issues.bio?.let { tooLongMessage(it) },
            isError = uiState.issues.bio != null,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 3),
        )
        FormError(uiState.error)
        ProgressButton(
            text = stringResource(R.string.feature_pets_save),
            loading = uiState.isSaving,
            onClick = actions.onSave,
        )
    }
}

@Composable
private fun nameMessage(issue: PetNameIssue): String = stringResource(
    when (issue) {
        PetNameIssue.Empty -> R.string.feature_pets_name_empty
        PetNameIssue.TooLong -> R.string.feature_pets_too_long
    },
)

@Composable
private fun speciesOtherMessage(issue: SpeciesOtherIssue): String = stringResource(
    when (issue) {
        SpeciesOtherIssue.Empty -> R.string.feature_pets_species_other_empty
        SpeciesOtherIssue.TooLong -> R.string.feature_pets_too_long
    },
)

@Composable
private fun microchipMessage(issue: MicrochipIssue): String = stringResource(
    when (issue) {
        MicrochipIssue.TooLong -> R.string.feature_pets_too_long
        MicrochipIssue.InvalidCharacters -> R.string.feature_pets_microchip_invalid
    },
)

@Composable
private fun tooLongMessage(issue: TextIssue): String = when (issue) {
    TextIssue.TooLong -> stringResource(R.string.feature_pets_too_long)
}

private val WordsKeyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next)

@PreviewLightDark
@PreviewFontScale
@Composable
private fun PetEditorPreview() {
    GeoWoofTheme(dynamicColor = false) {
        PetEditorContent(
            uiState = PetEditorUiState(species = PetSpecies.Other, size = PetSize.Medium),
            fields = PetEditorFields(
                name = rememberTextFieldState("Luna"),
                speciesOther = rememberTextFieldState("Hurón"),
                breed = rememberTextFieldState(),
                color = rememberTextFieldState(),
                microchip = rememberTextFieldState(),
                bio = rememberTextFieldState(),
            ),
            actions = PetEditorActions({}, {}, {}, {}, {}, {}, {}),
            onPickPhoto = {},
            onBack = {},
            onSkip = null,
        )
    }
}
