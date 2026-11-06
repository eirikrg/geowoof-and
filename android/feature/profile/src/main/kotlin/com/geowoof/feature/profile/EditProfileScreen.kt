package com.geowoof.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geowoof.core.designsystem.component.FormTextField
import com.geowoof.core.designsystem.component.ProgressButton
import com.geowoof.core.designsystem.icon.GeoWoofIcons
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.core.model.ProfileIssues
import com.geowoof.core.model.TextIssue
import com.geowoof.core.ui.EditablePhoto
import com.geowoof.core.ui.FormError
import com.geowoof.core.ui.FormScaffold
import com.geowoof.core.ui.messageRes
import com.geowoof.core.ui.rememberPhotoPicker

@Composable
internal fun EditProfileScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EditProfileViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnDone by rememberUpdatedState(onDone)
    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) currentOnDone()
    }
    EditProfileContent(
        uiState = uiState,
        displayName = viewModel.displayName,
        bio = viewModel.bio,
        city = viewModel.city,
        onPickPhoto = rememberPhotoPicker(viewModel::onImagePicked),
        onRemovePhoto = viewModel::removePhoto,
        onSave = viewModel::save,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
internal fun EditProfileContent(
    uiState: EditProfileUiState,
    displayName: TextFieldState,
    bio: TextFieldState,
    city: TextFieldState,
    onPickPhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FormScaffold(title = stringResource(R.string.feature_profile_edit), modifier = modifier, onBack = onBack) {
        if (uiState.isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            return@FormScaffold
        }
        EditablePhoto(
            image = uiState.avatar,
            localUri = uiState.pickedImageUri,
            fallbackIcon = GeoWoofIcons.Person,
            contentDescription = stringResource(R.string.feature_profile_photo),
            chooseLabel = stringResource(R.string.feature_profile_choose_photo),
            removeLabel = stringResource(R.string.feature_profile_remove_photo).takeIf { uiState.hasPhoto },
            onChoose = onPickPhoto,
            onRemove = onRemovePhoto,
        )
        FormTextField(
            state = displayName,
            label = stringResource(R.string.feature_profile_name_label),
            supportingText = uiState.issues.displayName?.let { stringResource(it.messageRes()) },
            isError = uiState.issues.displayName != null,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Next,
            ),
        )
        FormTextField(
            state = city,
            label = stringResource(R.string.feature_profile_city_label),
            supportingText = uiState.issues.city?.let { tooLongMessage(it) },
            isError = uiState.issues.city != null,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Next,
            ),
        )
        FormTextField(
            state = bio,
            label = stringResource(R.string.feature_profile_bio_label),
            supportingText = uiState.issues.bio?.let { tooLongMessage(it) },
            isError = uiState.issues.bio != null,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 3),
        )
        FormError(uiState.error)
        ProgressButton(
            text = stringResource(R.string.feature_profile_save),
            loading = uiState.isSaving,
            onClick = onSave,
        )
    }
}

@Composable
private fun tooLongMessage(issue: TextIssue): String = when (issue) {
    TextIssue.TooLong -> stringResource(R.string.feature_profile_too_long)
}

@PreviewLightDark
@Composable
private fun EditProfilePreview() {
    GeoWoofTheme(dynamicColor = false) {
        EditProfileContent(
            uiState = EditProfileUiState(isLoading = false, issues = ProfileIssues(bio = TextIssue.TooLong)),
            displayName = rememberTextFieldState("Ana"),
            bio = rememberTextFieldState("Paseo por el Retiro"),
            city = rememberTextFieldState("Madrid"),
            onPickPhoto = {},
            onRemovePhoto = {},
            onSave = {},
            onBack = {},
        )
    }
}
