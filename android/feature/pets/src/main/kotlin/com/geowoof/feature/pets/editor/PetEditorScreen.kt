package com.geowoof.feature.pets.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import com.geowoof.core.designsystem.component.GeoWoofIllustration
import com.geowoof.core.designsystem.component.GeoWoofIllustrations
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
import com.geowoof.core.ui.EditablePhoto
import com.geowoof.core.ui.FormError
import com.geowoof.core.ui.FormScaffold
import com.geowoof.core.ui.ImageVariant
import com.geowoof.core.ui.IntroFormScaffold
import com.geowoof.core.ui.OnboardingProgress
import com.geowoof.core.ui.OnboardingStep
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
    val form: @Composable ColumnScope.() -> Unit = {
        PetForm(
            uiState = uiState,
            fields = fields,
            actions = actions,
            onPickPhoto = onPickPhoto,
            startsWithTheBasics = onSkip != null,
        )
    }
    if (onSkip != null) {
        IntroFormScaffold(
            title = stringResource(R.string.feature_pets_first_title),
            modifier = modifier,
            message = stringResource(R.string.feature_pets_first_message),
            actions = { TextButton(onClick = onSkip) { Text(stringResource(R.string.feature_pets_skip)) } },
            lead = {
                OnboardingProgress(OnboardingStep.FirstPet)
                GeoWoofIllustration(
                    illustration = GeoWoofIllustrations.FirstPet,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = FirstPetIllustrationMaxHeight),
                )
            },
            content = form,
        )
    } else {
        FormScaffold(
            title = stringResource(
                if (uiState.isEditing) R.string.feature_pets_edit_title else R.string.feature_pets_add_title,
            ),
            modifier = modifier,
            onBack = onBack,
            content = form,
        )
    }
}

@Composable
private fun ColumnScope.PetForm(
    uiState: PetEditorUiState,
    fields: PetEditorFields,
    actions: PetEditorActions,
    onPickPhoto: () -> Unit,
    startsWithTheBasics: Boolean,
) {
    if (uiState.isLoading) {
        CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        return
    }
    var showsDetails by rememberSaveable { mutableStateOf(!startsWithTheBasics) }
    EditablePhoto(
        image = uiState.avatar,
        localUri = uiState.pickedImageUri,
        fallbackIcon = GeoWoofIcons.Pets,
        contentDescription = stringResource(R.string.feature_pets_photo),
        chooseLabel = stringResource(R.string.feature_pets_choose_photo),
        removeLabel = stringResource(R.string.feature_pets_remove_photo).takeIf { uiState.hasPhoto },
        onChoose = onPickPhoto,
        onRemove = actions.onRemovePhoto,
    )
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
    AnimatedVisibility(visible = showsDetails) {
        PetDetailsFields(uiState = uiState, fields = fields, actions = actions)
    }
    if (!showsDetails) {
        TextButton(onClick = { showsDetails = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(stringResource(R.string.feature_pets_more_details))
        }
    }
    FormError(uiState.error)
    ProgressButton(
        text = stringResource(R.string.feature_pets_save),
        loading = uiState.isSaving,
        onClick = actions.onSave,
    )
}

/** What can wait: nobody needs the microchip number at hand to start walking. */
@Composable
private fun PetDetailsFields(
    uiState: PetEditorUiState,
    fields: PetEditorFields,
    actions: PetEditorActions,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(GeoWoofTheme.spacing.medium)) {
        BirthDateAndNeutered(uiState = uiState, actions = actions)
        LabeledChoice(stringResource(R.string.feature_pets_sex_label)) {
            SexSelector(selected = uiState.sex, onSelect = actions.onSexChange)
        }
        LabeledChoice(stringResource(R.string.feature_pets_size_label)) {
            SizeSelector(selected = uiState.size, onSelect = actions.onSizeChange)
        }
        FormTextField(
            state = fields.color,
            label = stringResource(R.string.feature_pets_color_label),
            supportingText = uiState.issues.color?.let { tooLongMessage(it) },
            isError = uiState.issues.color != null,
            keyboardOptions = WordsKeyboard,
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

private val FirstPetIllustrationMaxHeight = 120.dp
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
