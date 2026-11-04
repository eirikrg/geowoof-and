package com.geowoof.feature.auth.signin

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geowoof.core.designsystem.component.EmailKeyboardOptions
import com.geowoof.core.designsystem.component.FormTextField
import com.geowoof.core.designsystem.component.PasswordField
import com.geowoof.core.designsystem.component.ProgressButton
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.core.model.EmailIssue
import com.geowoof.core.ui.FormError
import com.geowoof.core.ui.FormNotice
import com.geowoof.core.ui.FormScaffold
import com.geowoof.core.ui.GoogleSignInButton
import com.geowoof.core.ui.PasswordRequired
import com.geowoof.core.ui.messageRes
import com.geowoof.core.ui.rememberCredentialSubmit
import com.geowoof.core.ui.rememberGoogleSignInLauncher
import com.geowoof.feature.auth.R

@Composable
internal fun SignInScreen(
    onBack: () -> Unit,
    onForgotPassword: (email: String) -> Unit,
    onSignUp: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SignInViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val google = rememberGoogleSignInLauncher(
        serverClientId = uiState.googleClientId,
        onIdToken = viewModel::signInWithGoogle,
        onUnavailable = viewModel::onGoogleUnavailable,
    )
    SignInContent(
        uiState = uiState,
        email = viewModel.email,
        password = viewModel.password,
        onSubmit = rememberCredentialSubmit(viewModel::submit),
        onForgotPassword = { onForgotPassword(viewModel.email.text.toString().trim()) },
        onSignUp = onSignUp,
        onBack = onBack,
        onContinueWithGoogle = google?.let { it::launch },
        modifier = modifier,
    )
}

@Composable
internal fun SignInContent(
    uiState: SignInUiState,
    email: TextFieldState,
    password: TextFieldState,
    onSubmit: () -> Unit,
    onForgotPassword: () -> Unit,
    onSignUp: () -> Unit,
    onBack: () -> Unit,
    onContinueWithGoogle: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    FormScaffold(title = stringResource(R.string.feature_auth_sign_in_title), modifier = modifier, onBack = onBack) {
        if (uiState.passwordWasReset) {
            FormNotice(stringResource(R.string.feature_auth_password_reset_done))
        }
        FormTextField(
            state = email,
            label = stringResource(R.string.feature_auth_email_label),
            supportingText = uiState.emailIssue?.let { stringResource(it.messageRes()) },
            isError = uiState.emailIssue != null,
            keyboardOptions = EmailKeyboardOptions,
            contentType = ContentType.Username + ContentType.EmailAddress,
        )
        PasswordField(
            state = password,
            label = stringResource(R.string.feature_auth_password_label),
            supportingText = if (uiState.isPasswordMissing) stringResource(PasswordRequired) else null,
            isError = uiState.isPasswordMissing,
            onKeyboardAction = { onSubmit() },
        )
        TextButton(onClick = onForgotPassword, modifier = Modifier.align(Alignment.End)) {
            Text(stringResource(R.string.feature_auth_forgot_password))
        }
        FormError(uiState.error)
        ProgressButton(
            text = stringResource(R.string.feature_auth_sign_in),
            loading = uiState.isSubmitting,
            onClick = onSubmit,
            enabled = !uiState.isSigningInWithGoogle,
        )
        if (onContinueWithGoogle != null) {
            GoogleSignInButton(
                onClick = onContinueWithGoogle,
                loading = uiState.isSigningInWithGoogle,
                enabled = !uiState.isSubmitting,
            )
        }
        SwitchFormButton(text = stringResource(R.string.feature_auth_no_account), onClick = onSignUp)
    }
}

/** Link to the other form (sign in ↔ sign up), centred under the actions. */
@Composable
internal fun ColumnScope.SwitchFormButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onClick, modifier = modifier.align(Alignment.CenterHorizontally)) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@PreviewLightDark
@Composable
private fun SignInPreview() {
    GeoWoofTheme(dynamicColor = false) {
        SignInContent(
            uiState = SignInUiState(emailIssue = EmailIssue.Invalid, googleClientId = "id"),
            email = rememberTextFieldState("ana@"),
            password = rememberTextFieldState(),
            onSubmit = {},
            onForgotPassword = {},
            onSignUp = {},
            onBack = {},
            onContinueWithGoogle = {},
        )
    }
}
