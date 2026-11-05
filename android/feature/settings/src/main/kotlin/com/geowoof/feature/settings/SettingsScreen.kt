package com.geowoof.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geowoof.core.designsystem.icon.GeoWoofIcons
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.core.model.SignInMethod
import com.geowoof.core.model.User
import java.time.Instant

@Composable
internal fun SettingsScreen(
    onBack: () -> Unit,
    onVerifyEmail: () -> Unit,
    onChangePassword: () -> Unit,
    onOpenBlockedUsers: () -> Unit,
    onDeleteAccount: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsContent(
        uiState = uiState,
        onBack = onBack,
        onVerifyEmail = onVerifyEmail,
        onChangePassword = onChangePassword,
        onOpenBlockedUsers = onOpenBlockedUsers,
        onSignOut = viewModel::signOut,
        onConfirmSignOut = viewModel::confirmSignOut,
        onDismissSignOut = viewModel::dismissSignOut,
        onDeleteAccount = onDeleteAccount,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsContent(
    uiState: SettingsUiState,
    onBack: () -> Unit,
    onVerifyEmail: () -> Unit,
    onChangePassword: () -> Unit,
    onOpenBlockedUsers: () -> Unit,
    onSignOut: () -> Unit,
    onConfirmSignOut: () -> Unit,
    onDismissSignOut: () -> Unit,
    onDeleteAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (uiState.isConfirmingSignOut) {
        AlertDialog(
            onDismissRequest = onDismissSignOut,
            title = { Text(stringResource(R.string.feature_settings_sign_out_title)) },
            text = { Text(stringResource(R.string.feature_settings_sign_out_message)) },
            confirmButton = {
                TextButton(onClick = onConfirmSignOut) { Text(stringResource(R.string.feature_settings_sign_out)) }
            },
            dismissButton = {
                TextButton(onClick = onDismissSignOut) { Text(stringResource(R.string.feature_settings_stay)) }
            },
        )
    }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feature_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(GeoWoofIcons.ArrowBack),
                            contentDescription = stringResource(R.string.feature_settings_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            val user = uiState.user
            SectionHeader(stringResource(R.string.feature_settings_account))
            if (user != null) {
                ListItem(
                    headlineContent = { Text(user.displayName) },
                    supportingContent = user.username?.let {
                        { Text(stringResource(R.string.feature_settings_username, it)) }
                    },
                    leadingContent = { Icon(painterResource(GeoWoofIcons.Person), contentDescription = null) },
                )
                ListItem(
                    headlineContent = { Text(user.email) },
                    supportingContent = {
                        Text(
                            stringResource(
                                if (user.emailVerified) {
                                    R.string.feature_settings_email_verified
                                } else {
                                    R.string.feature_settings_email_unverified
                                },
                            ),
                        )
                    },
                    leadingContent = { Icon(painterResource(GeoWoofIcons.Mail), contentDescription = null) },
                    trailingContent = if (user.emailVerified) {
                        null
                    } else {
                        {
                            TextButton(onClick = onVerifyEmail) {
                                Text(stringResource(R.string.feature_settings_verify))
                            }
                        }
                    },
                )
                ListItem(
                    headlineContent = {
                        Text(
                            stringResource(
                                if (user.hasPassword) {
                                    R.string.feature_settings_change_password
                                } else {
                                    R.string.feature_settings_create_password
                                },
                            ),
                        )
                    },
                    supportingContent = if (user.hasPassword) {
                        null
                    } else {
                        { Text(stringResource(R.string.feature_settings_create_password_summary)) }
                    },
                    leadingContent = { Icon(painterResource(GeoWoofIcons.Lock), contentDescription = null) },
                    modifier = Modifier.clickable(onClick = onChangePassword),
                )
            }
            ListItem(
                headlineContent = { Text(stringResource(R.string.feature_settings_blocked_users)) },
                leadingContent = { Icon(painterResource(GeoWoofIcons.Block), contentDescription = null) },
                trailingContent = { Icon(painterResource(GeoWoofIcons.ChevronRight), contentDescription = null) },
                modifier = Modifier.clickable(onClick = onOpenBlockedUsers),
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(stringResource(R.string.feature_settings_sign_out)) },
                leadingContent = { Icon(painterResource(GeoWoofIcons.Logout), contentDescription = null) },
                modifier = Modifier.clickable(enabled = !uiState.isSigningOut, onClick = onSignOut),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.feature_settings_delete_account)) },
                leadingContent = { Icon(painterResource(GeoWoofIcons.Delete), contentDescription = null) },
                colors = ListItemDefaults.colors(
                    headlineColor = MaterialTheme.colorScheme.error,
                    leadingIconColor = MaterialTheme.colorScheme.error,
                ),
                modifier = Modifier.clickable(onClick = onDeleteAccount),
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier
            .padding(horizontal = GeoWoofTheme.spacing.large, vertical = GeoWoofTheme.spacing.small)
            .semantics { heading() },
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@PreviewLightDark
@Composable
private fun SettingsPreview() {
    GeoWoofTheme(dynamicColor = false) {
        SettingsContent(
            uiState = SettingsUiState(
                user = User(
                    id = "0193f0c2-6f1e-7cc4-9a2b-3c1d5e7f9a10",
                    email = "ana@geowoof.dev",
                    emailVerified = false,
                    username = "ana",
                    displayName = "Ana",
                    hasPassword = true,
                    signInMethods = setOf(SignInMethod.Password),
                    language = "es",
                    createdAt = Instant.parse("2026-11-01T10:00:00Z"),
                ),
            ),
            onBack = {},
            onVerifyEmail = {},
            onChangePassword = {},
            onOpenBlockedUsers = {},
            onSignOut = {},
            onConfirmSignOut = {},
            onDismissSignOut = {},
            onDeleteAccount = {},
        )
    }
}
