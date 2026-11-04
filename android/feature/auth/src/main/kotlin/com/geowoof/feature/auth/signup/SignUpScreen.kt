package com.geowoof.feature.auth.signup

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geowoof.core.designsystem.component.EmailKeyboardOptions
import com.geowoof.core.designsystem.component.FormTextField
import com.geowoof.core.designsystem.component.PasswordField
import com.geowoof.core.designsystem.component.ProgressButton
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.core.model.PasswordIssue
import com.geowoof.core.ui.FormError
import com.geowoof.core.ui.FormScaffold
import com.geowoof.core.ui.PasswordRulesHint
import com.geowoof.core.ui.messageRes
import com.geowoof.core.ui.rememberCredentialSubmit
import com.geowoof.feature.auth.R
import com.geowoof.feature.auth.signin.SwitchFormButton

@Composable
internal fun SignUpScreen(
    onBack: () -> Unit,
    onSignIn: (email: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SignUpViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SignUpContent(
        uiState = uiState,
        displayName = viewModel.displayName,
        email = viewModel.email,
        password = viewModel.password,
        onSubmit = rememberCredentialSubmit(viewModel::submit),
        onSignIn = { onSignIn(viewModel.email.text.toString().trim()) },
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
internal fun SignUpContent(
    uiState: SignUpUiState,
    displayName: TextFieldState,
    email: TextFieldState,
    password: TextFieldState,
    onSubmit: () -> Unit,
    onSignIn: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FormScaffold(title = stringResource(R.string.feature_auth_sign_up_title), modifier = modifier, onBack = onBack) {
        FormTextField(
            state = displayName,
            label = stringResource(R.string.feature_auth_name_label),
            supportingText = uiState.displayNameIssue?.let { stringResource(it.messageRes()) },
            isError = uiState.displayNameIssue != null,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Next,
            ),
            contentType = ContentType.PersonFullName,
        )
        FormTextField(
            state = email,
            label = stringResource(R.string.feature_auth_email_label),
            supportingText = uiState.emailIssue?.let { stringResource(it.messageRes()) },
            isError = uiState.emailIssue != null,
            keyboardOptions = EmailKeyboardOptions,
            contentType = ContentType.NewUsername + ContentType.EmailAddress,
        )
        PasswordField(
            state = password,
            label = stringResource(R.string.feature_auth_password_label),
            supportingText = stringResource(uiState.passwordIssue?.messageRes() ?: PasswordRulesHint),
            isError = uiState.passwordIssue != null,
            contentType = ContentType.NewPassword,
            onKeyboardAction = { onSubmit() },
        )
        FormError(uiState.error)
        ProgressButton(
            text = stringResource(R.string.feature_auth_create_account),
            loading = uiState.isSubmitting,
            onClick = onSubmit,
        )
        SwitchFormButton(text = stringResource(R.string.feature_auth_have_account), onClick = onSignIn)
    }
}

@PreviewLightDark
@Composable
private fun SignUpPreview() {
    GeoWoofTheme(dynamicColor = false) {
        SignUpContent(
            uiState = SignUpUiState(passwordIssue = PasswordIssue.MissingDigit),
            displayName = rememberTextFieldState("Ana"),
            email = rememberTextFieldState("ana@geowoof.dev"),
            password = rememberTextFieldState("password"),
            onSubmit = {},
            onSignIn = {},
            onBack = {},
        )
    }
}
