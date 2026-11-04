package com.geowoof.feature.settings.account

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geowoof.core.designsystem.component.PasswordField
import com.geowoof.core.designsystem.component.ProgressButton
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.core.ui.FormError
import com.geowoof.core.ui.FormScaffold
import com.geowoof.core.ui.PasswordRequired
import com.geowoof.core.ui.PasswordRulesHint
import com.geowoof.core.ui.messageRes
import com.geowoof.core.ui.rememberCredentialSubmit
import com.geowoof.feature.settings.R

@Composable
internal fun ChangePasswordScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChangePasswordViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnDone by rememberUpdatedState(onDone)
    LaunchedEffect(uiState.isDone) {
        if (uiState.isDone) currentOnDone()
    }
    ChangePasswordContent(
        uiState = uiState,
        currentPassword = viewModel.currentPassword,
        newPassword = viewModel.newPassword,
        onSubmit = rememberCredentialSubmit(viewModel::submit),
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
internal fun ChangePasswordContent(
    uiState: ChangePasswordUiState,
    currentPassword: TextFieldState,
    newPassword: TextFieldState,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = stringResource(
        if (uiState.hasPassword) {
            R.string.feature_settings_change_password
        } else {
            R.string.feature_settings_create_password
        },
    )
    FormScaffold(title = title, modifier = modifier, onBack = onBack) {
        Text(
            stringResource(R.string.feature_settings_change_password_message),
            style = MaterialTheme.typography.bodyLarge,
        )
        if (uiState.hasPassword) {
            PasswordField(
                state = currentPassword,
                label = stringResource(R.string.feature_settings_current_password),
                supportingText = if (uiState.isCurrentPasswordMissing) stringResource(PasswordRequired) else null,
                isError = uiState.isCurrentPasswordMissing,
                imeAction = ImeAction.Next,
            )
        }
        PasswordField(
            state = newPassword,
            label = stringResource(R.string.feature_settings_new_password),
            supportingText = stringResource(uiState.newPasswordIssue?.messageRes() ?: PasswordRulesHint),
            isError = uiState.newPasswordIssue != null,
            contentType = ContentType.NewPassword,
            onKeyboardAction = { onSubmit() },
        )
        FormError(uiState.error)
        ProgressButton(
            text = stringResource(R.string.feature_settings_save),
            loading = uiState.isSubmitting,
            onClick = onSubmit,
        )
    }
}

@PreviewLightDark
@Composable
private fun ChangePasswordPreview() {
    GeoWoofTheme(dynamicColor = false) {
        ChangePasswordContent(
            uiState = ChangePasswordUiState(),
            currentPassword = rememberTextFieldState(),
            newPassword = rememberTextFieldState(),
            onSubmit = {},
            onBack = {},
        )
    }
}
