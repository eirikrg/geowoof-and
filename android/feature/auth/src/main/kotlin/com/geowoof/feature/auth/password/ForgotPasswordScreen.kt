package com.geowoof.feature.auth.password

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geowoof.core.designsystem.component.FormTextField
import com.geowoof.core.designsystem.component.ProgressButton
import com.geowoof.core.designsystem.icon.GeoWoofIcons
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.core.ui.FormError
import com.geowoof.core.ui.FormIntroIcon
import com.geowoof.core.ui.IntroFormScaffold
import com.geowoof.core.ui.messageRes
import com.geowoof.feature.auth.R

@Composable
internal fun ForgotPasswordScreen(
    onBack: () -> Unit,
    onEnterCode: (email: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ForgotPasswordViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnEnterCode by rememberUpdatedState(onEnterCode)
    LaunchedEffect(uiState.codeSentTo) {
        uiState.codeSentTo?.let { email ->
            currentOnEnterCode(email)
            viewModel.onCodeSentHandled()
        }
    }
    ForgotPasswordContent(
        uiState = uiState,
        email = viewModel.email,
        onSubmit = viewModel::submit,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
internal fun ForgotPasswordContent(
    uiState: ForgotPasswordUiState,
    email: TextFieldState,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IntroFormScaffold(
        title = stringResource(R.string.feature_auth_forgot_title),
        modifier = modifier,
        message = stringResource(R.string.feature_auth_forgot_message),
        onBack = onBack,
        lead = { FormIntroIcon(GeoWoofIcons.Lock) },
    ) {
        FormTextField(
            state = email,
            label = stringResource(R.string.feature_auth_email_label),
            supportingText = uiState.emailIssue?.let { stringResource(it.messageRes()) },
            isError = uiState.emailIssue != null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
            contentType = ContentType.Username + ContentType.EmailAddress,
            onKeyboardAction = { onSubmit() },
        )
        FormError(uiState.error)
        ProgressButton(
            text = stringResource(R.string.feature_auth_send_code),
            loading = uiState.isSubmitting,
            onClick = onSubmit,
        )
    }
}

@PreviewLightDark
@Composable
private fun ForgotPasswordPreview() {
    GeoWoofTheme(dynamicColor = false) {
        ForgotPasswordContent(
            uiState = ForgotPasswordUiState(),
            email = rememberTextFieldState("ana@geowoof.dev"),
            onSubmit = {},
            onBack = {},
        )
    }
}
