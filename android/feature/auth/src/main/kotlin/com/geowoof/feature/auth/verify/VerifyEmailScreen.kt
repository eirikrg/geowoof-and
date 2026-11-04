package com.geowoof.feature.auth.verify

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geowoof.core.designsystem.component.ProgressButton
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.core.ui.FormError
import com.geowoof.core.ui.FormNotice
import com.geowoof.core.ui.FormScaffold
import com.geowoof.core.ui.VerificationCodeField
import com.geowoof.core.ui.messageRes
import com.geowoof.feature.auth.R

/** Without [onBack] this is an onboarding step: it offers [onSkip] and signing out instead. */
@Composable
internal fun VerifyEmailScreen(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onSkip: (() -> Unit)? = null,
    viewModel: VerifyEmailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnDone by rememberUpdatedState(onDone)
    LaunchedEffect(uiState.isVerified) {
        if (uiState.isVerified) currentOnDone()
    }
    VerifyEmailContent(
        uiState = uiState,
        code = viewModel.code,
        onVerify = viewModel::verify,
        onResendCode = viewModel::resendCode,
        onBack = onBack,
        onSkip = onSkip,
        onSignOut = if (onBack == null) viewModel::signOut else null,
        modifier = modifier,
    )
}

@Composable
internal fun VerifyEmailContent(
    uiState: VerifyEmailUiState,
    code: TextFieldState,
    onVerify: () -> Unit,
    onResendCode: () -> Unit,
    onBack: (() -> Unit)?,
    onSkip: (() -> Unit)?,
    onSignOut: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    FormScaffold(
        title = stringResource(R.string.feature_auth_verify_title),
        modifier = modifier,
        onBack = onBack,
        actions = {
            if (onSignOut != null) {
                TextButton(onClick = onSignOut) { Text(stringResource(R.string.feature_auth_sign_out)) }
            }
        },
    ) {
        Text(
            stringResource(R.string.feature_auth_verify_message, uiState.email),
            style = MaterialTheme.typography.bodyLarge,
        )
        VerificationCodeField(
            state = code,
            supportingText = uiState.codeIssue?.let { stringResource(it.messageRes()) },
            isError = uiState.codeIssue != null,
            onKeyboardAction = { onVerify() },
        )
        FormError(uiState.error)
        if (uiState.codeResent) {
            FormNotice(stringResource(R.string.feature_auth_code_resent))
        }
        ProgressButton(
            text = stringResource(R.string.feature_auth_verify),
            loading = uiState.isVerifying,
            onClick = onVerify,
        )
        TextButton(
            onClick = onResendCode,
            enabled = uiState.canResend,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text(
                if (uiState.resendAvailableInSeconds > 0) {
                    stringResource(R.string.feature_auth_resend_code_in, uiState.resendAvailableInSeconds)
                } else {
                    stringResource(R.string.feature_auth_resend_code)
                },
            )
        }
        if (onSkip != null) {
            TextButton(onClick = onSkip, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(stringResource(R.string.feature_auth_skip))
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun VerifyEmailPreview() {
    GeoWoofTheme(dynamicColor = false) {
        VerifyEmailContent(
            uiState = VerifyEmailUiState(email = "ana@geowoof.dev", resendAvailableInSeconds = 42),
            code = rememberTextFieldState(),
            onVerify = {},
            onResendCode = {},
            onBack = null,
            onSkip = {},
            onSignOut = {},
        )
    }
}
