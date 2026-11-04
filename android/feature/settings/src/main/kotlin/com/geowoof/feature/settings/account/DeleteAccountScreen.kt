package com.geowoof.feature.settings.account

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geowoof.core.designsystem.component.PasswordField
import com.geowoof.core.designsystem.component.ProgressButton
import com.geowoof.core.designsystem.icon.GeoWoofIcons
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.core.ui.FormError
import com.geowoof.core.ui.FormScaffold
import com.geowoof.core.ui.PasswordRequired
import com.geowoof.core.ui.rememberGoogleSignInLauncher
import com.geowoof.feature.settings.R

@Composable
internal fun DeleteAccountScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DeleteAccountViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val google = rememberGoogleSignInLauncher(
        serverClientId = uiState.googleClientId,
        onIdToken = viewModel::deleteWithGoogle,
        onUnavailable = viewModel::onGoogleUnavailable,
    )
    DeleteAccountContent(
        uiState = uiState,
        password = viewModel.password,
        onDelete = viewModel::requestDeletion,
        onConfirm = {
            when {
                uiState.hasPassword -> viewModel.deleteWithPassword()

                google != null -> {
                    viewModel.dismissConfirmation()
                    google.launch()
                }

                else -> viewModel.onGoogleUnavailable()
            }
        },
        onDismissConfirmation = viewModel::dismissConfirmation,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
internal fun DeleteAccountContent(
    uiState: DeleteAccountUiState,
    password: TextFieldState,
    onDelete: () -> Unit,
    onConfirm: () -> Unit,
    onDismissConfirmation: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FormScaffold(
        title = stringResource(R.string.feature_settings_delete_account),
        modifier = modifier,
        onBack = onBack,
    ) {
        Text(stringResource(R.string.feature_settings_delete_message), style = MaterialTheme.typography.bodyLarge)
        Text(
            stringResource(
                if (uiState.hasPassword) {
                    R.string.feature_settings_delete_password_message
                } else {
                    R.string.feature_settings_delete_google_message
                },
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (uiState.hasPassword) {
            PasswordField(
                state = password,
                label = stringResource(R.string.feature_settings_password_label),
                supportingText = if (uiState.isPasswordMissing) stringResource(PasswordRequired) else null,
                isError = uiState.isPasswordMissing,
                onKeyboardAction = { onDelete() },
            )
        }
        FormError(uiState.error)
        ProgressButton(
            text = stringResource(R.string.feature_settings_delete_account),
            loading = uiState.isDeleting,
            onClick = onDelete,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
        )
    }
    if (uiState.isConfirming) {
        AlertDialog(
            onDismissRequest = onDismissConfirmation,
            icon = { Icon(painterResource(GeoWoofIcons.Delete), contentDescription = null) },
            title = { Text(stringResource(R.string.feature_settings_delete_confirm_title)) },
            text = { Text(stringResource(R.string.feature_settings_delete_confirm_message)) },
            confirmButton = {
                TextButton(onClick = onConfirm) {
                    Text(stringResource(R.string.feature_settings_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissConfirmation) { Text(stringResource(R.string.feature_settings_cancel)) }
            },
        )
    }
}

@PreviewLightDark
@Composable
private fun DeleteAccountPreview() {
    GeoWoofTheme(dynamicColor = false) {
        DeleteAccountContent(
            uiState = DeleteAccountUiState(),
            password = rememberTextFieldState(),
            onDelete = {},
            onConfirm = {},
            onDismissConfirmation = {},
            onBack = {},
        )
    }
}
