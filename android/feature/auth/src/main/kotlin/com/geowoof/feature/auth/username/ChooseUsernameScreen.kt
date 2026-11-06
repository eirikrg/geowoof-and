package com.geowoof.feature.auth.username

import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geowoof.core.designsystem.component.FormTextField
import com.geowoof.core.designsystem.component.ListGroup
import com.geowoof.core.designsystem.component.ListGroupItem
import com.geowoof.core.designsystem.component.ProgressButton
import com.geowoof.core.designsystem.icon.GeoWoofIcons
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.core.domain.UsernameStatus
import com.geowoof.core.model.UsernameUnavailableReason
import com.geowoof.core.ui.FormError
import com.geowoof.core.ui.IntroFormScaffold
import com.geowoof.core.ui.OnboardingProgress
import com.geowoof.core.ui.OnboardingStep
import com.geowoof.core.ui.messageRes
import com.geowoof.feature.auth.R

@Composable
internal fun ChooseUsernameScreen(modifier: Modifier = Modifier, viewModel: ChooseUsernameViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ChooseUsernameContent(
        uiState = uiState,
        username = viewModel.username,
        displayName = viewModel.displayName,
        onPrivateChange = viewModel::onPrivateChange,
        onSubmit = viewModel::submit,
        onSignOut = viewModel::signOut,
        modifier = modifier,
    )
}

@Composable
internal fun ChooseUsernameContent(
    uiState: ChooseUsernameUiState,
    username: TextFieldState,
    displayName: TextFieldState,
    onPrivateChange: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = uiState.usernameStatus
    IntroFormScaffold(
        title = stringResource(R.string.feature_auth_username_title),
        modifier = modifier,
        message = stringResource(R.string.feature_auth_username_message),
        actions = {
            TextButton(onClick = onSignOut) { Text(stringResource(R.string.feature_auth_sign_out)) }
        },
        lead = { OnboardingProgress(OnboardingStep.ChooseUsername) },
    ) {
        FormTextField(
            state = username,
            label = stringResource(R.string.feature_auth_username_label),
            prefix = "@",
            supportingText = usernameSupportingText(uiState),
            isError = status is UsernameStatus.Invalid || status is UsernameStatus.Unavailable,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Next,
            ),
            contentType = ContentType.NewUsername,
            trailingIcon = if (status is UsernameStatus.Available) {
                {
                    Icon(
                        painter = painterResource(GeoWoofIcons.CheckCircle),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            } else {
                null
            },
        )
        FormTextField(
            state = displayName,
            label = stringResource(R.string.feature_auth_name_label),
            supportingText = uiState.displayNameIssue?.let { stringResource(it.messageRes()) },
            isError = uiState.displayNameIssue != null,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Done,
            ),
            contentType = ContentType.PersonFullName,
            onKeyboardAction = { onSubmit() },
        )
        ListGroup(inset = 0.dp) {
            ListGroupItem(
                headline = stringResource(R.string.feature_auth_private_profile),
                // The whole row is the switch, for TalkBack too.
                modifier = Modifier.toggleable(
                    value = uiState.isPrivate,
                    role = Role.Switch,
                    onValueChange = onPrivateChange,
                ),
                supporting = stringResource(
                    if (uiState.isPrivate) {
                        R.string.feature_auth_private_on
                    } else {
                        R.string.feature_auth_private_off
                    },
                ),
                icon = GeoWoofIcons.Lock,
                trailing = { Switch(checked = uiState.isPrivate, onCheckedChange = null) },
            )
        }
        FormError(uiState.error)
        ProgressButton(
            text = stringResource(R.string.feature_auth_continue),
            loading = uiState.isSubmitting,
            onClick = onSubmit,
        )
    }
}

@Composable
private fun usernameSupportingText(uiState: ChooseUsernameUiState): String {
    if (uiState.isCheckingUsername) return stringResource(R.string.feature_auth_username_checking)
    return when (val status = uiState.usernameStatus) {
        null -> stringResource(R.string.feature_auth_username_hint)

        is UsernameStatus.Invalid -> stringResource(status.issue.messageRes())

        is UsernameStatus.Available -> stringResource(R.string.feature_auth_username_available, status.username)

        is UsernameStatus.Unavailable -> stringResource(
            when (status.reason) {
                UsernameUnavailableReason.Taken -> R.string.feature_auth_username_taken
                UsernameUnavailableReason.Reserved -> R.string.feature_auth_username_reserved
                UsernameUnavailableReason.Invalid -> R.string.feature_auth_username_invalid
            },
        )

        is UsernameStatus.Unknown -> stringResource(R.string.feature_auth_username_check_failed)
    }
}

@PreviewLightDark
@Composable
private fun ChooseUsernamePreview() {
    GeoWoofTheme(dynamicColor = false) {
        ChooseUsernameContent(
            uiState = ChooseUsernameUiState(usernameStatus = UsernameStatus.Available("ana.paws")),
            username = rememberTextFieldState("ana.paws"),
            displayName = rememberTextFieldState("Ana"),
            onPrivateChange = {},
            onSubmit = {},
            onSignOut = {},
        )
    }
}
