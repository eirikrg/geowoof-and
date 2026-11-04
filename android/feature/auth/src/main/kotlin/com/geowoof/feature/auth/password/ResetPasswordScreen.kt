package com.geowoof.feature.auth.password

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
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
import com.geowoof.core.ui.FormNotice
import com.geowoof.core.ui.FormScaffold
import com.geowoof.core.ui.PasswordRulesHint
import com.geowoof.core.ui.VerificationCodeField
import com.geowoof.core.ui.messageRes
import com.geowoof.feature.auth.R

@Composable
internal fun ResetPasswordScreen(
    onBack: () -> Unit,
    onPasswordReset: (email: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ResetPasswordViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnPasswordReset by rememberUpdatedState(onPasswordReset)
    LaunchedEffect(uiState.isDone) {
        if (uiState.isDone) currentOnPasswordReset(uiState.email)
    }
    ResetPasswordContent(
        uiState = uiState,
        code = viewModel.code,
        newPassword = viewModel.newPassword,
        onSubmit = viewModel::submit,
        onResendCode = viewModel::resendCode,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
internal fun ResetPasswordContent(
    uiState: ResetPasswordUiState,
    code: TextFieldState,
    newPassword: TextFieldState,
    onSubmit: () -> Unit,
    onResendCode: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FormScaffold(title = stringResource(R.string.feature_auth_reset_title), modifier = modifier, onBack = onBack) {
        Text(
            stringResource(R.string.feature_auth_reset_message, uiState.email),
            style = MaterialTheme.typography.bodyLarge,
        )
        VerificationCodeField(
            state = code,
            supportingText = uiState.codeIssue?.let { stringResource(it.messageRes()) },
            isError = uiState.codeIssue != null,
        )
        PasswordField(
            state = newPassword,
            label = stringResource(R.string.feature_auth_new_password_label),
            supportingText = stringResource(uiState.passwordIssue?.messageRes() ?: PasswordRulesHint),
            isError = uiState.passwordIssue != null,
            contentType = ContentType.NewPassword,
            imeAction = ImeAction.Done,
            onKeyboardAction = { onSubmit() },
        )
        FormError(uiState.error)
        if (uiState.codeResent) {
            FormNotice(stringResource(R.string.feature_auth_code_resent))
        }
        ProgressButton(
            text = stringResource(R.string.feature_auth_change_password),
            loading = uiState.isSubmitting,
            onClick = onSubmit,
        )
        TextButton(
            onClick = onResendCode,
            enabled = !uiState.isResending,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text(stringResource(R.string.feature_auth_resend_code))
        }
    }
}

@PreviewLightDark
@Composable
private fun ResetPasswordPreview() {
    GeoWoofTheme(dynamicColor = false) {
        ResetPasswordContent(
            uiState = ResetPasswordUiState(email = "ana@geowoof.dev", codeResent = true),
            code = rememberTextFieldState("1234"),
            newPassword = rememberTextFieldState(),
            onSubmit = {},
            onResendCode = {},
            onBack = {},
        )
    }
}
