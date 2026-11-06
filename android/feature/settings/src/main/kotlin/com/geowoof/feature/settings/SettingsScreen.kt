package com.geowoof.feature.settings

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geowoof.core.designsystem.component.FittedText
import com.geowoof.core.designsystem.component.ListGroup
import com.geowoof.core.designsystem.component.ListGroupDivider
import com.geowoof.core.designsystem.component.ListGroupItem
import com.geowoof.core.designsystem.component.SectionHeader
import com.geowoof.core.designsystem.icon.GeoWoofIcons
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.core.model.Appearance
import com.geowoof.core.model.LiveVisibility
import com.geowoof.core.model.SignInMethod
import com.geowoof.core.model.ThemeMode
import com.geowoof.core.model.User
import com.geowoof.core.ui.userMessage
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
    val context = LocalContext.current
    var areNotificationsOn by remember { mutableStateOf(true) }
    // It can change in the settings of the phone, and that is where this screen sends the person.
    LifecycleResumeEffect(context) {
        areNotificationsOn = NotificationManagerCompat.from(context).areNotificationsEnabled()
        onPauseOrDispose {}
    }
    SettingsContent(
        uiState = uiState,
        onBack = onBack,
        onVerifyEmail = onVerifyEmail,
        onChangePassword = onChangePassword,
        onOpenBlockedUsers = onOpenBlockedUsers,
        onPrivateChange = viewModel::onPrivateChange,
        onLiveVisibilityChange = viewModel::onLiveVisibilityChange,
        onHideStartChange = viewModel::onHideStartChange,
        onThemeModeChange = viewModel::onThemeModeChange,
        onWallpaperColorsChange = viewModel::onWallpaperColorsChange,
        onDismissError = viewModel::onErrorShown,
        onSignOut = viewModel::signOut,
        onConfirmSignOut = viewModel::confirmSignOut,
        onDismissSignOut = viewModel::dismissSignOut,
        onDeleteAccount = onDeleteAccount,
        modifier = modifier,
        areNotificationsOn = areNotificationsOn,
        onOpenNotificationSettings = {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
            )
        },
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
    onPrivateChange: (Boolean) -> Unit,
    onLiveVisibilityChange: (LiveVisibility) -> Unit,
    onHideStartChange: (Int) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onWallpaperColorsChange: (Boolean) -> Unit,
    onDismissError: () -> Unit,
    onSignOut: () -> Unit,
    onConfirmSignOut: () -> Unit,
    onDismissSignOut: () -> Unit,
    onDeleteAccount: () -> Unit,
    modifier: Modifier = Modifier,
    areNotificationsOn: Boolean = true,
    onOpenNotificationSettings: () -> Unit = {},
) {
    val snackbars = remember { SnackbarHostState() }
    val errorMessage = uiState.error?.userMessage()
    val currentOnDismissError by rememberUpdatedState(onDismissError)
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            snackbars.showSnackbar(errorMessage)
            currentOnDismissError()
        }
    }
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
        snackbarHost = { SnackbarHost(snackbars) },
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
                .verticalScroll(rememberScrollState())
                .padding(bottom = GeoWoofTheme.spacing.huge),
        ) {
            val user = uiState.user
            if (user != null) {
                AccountSection(user, onVerifyEmail, onChangePassword)
            }
            AppearanceSection(uiState.appearance, onThemeModeChange, onWallpaperColorsChange)
            NotificationsSection(areNotificationsOn, onOpenNotificationSettings)
            PrivacySection(
                uiState = uiState,
                onPrivateChange = onPrivateChange,
                onLiveVisibilityChange = onLiveVisibilityChange,
                onHideStartChange = onHideStartChange,
                onOpenBlockedUsers = onOpenBlockedUsers,
            )
            Spacer(Modifier.height(GeoWoofTheme.spacing.extraLarge))
            ListGroup {
                ListGroupItem(
                    headline = stringResource(R.string.feature_settings_sign_out),
                    icon = GeoWoofIcons.Logout,
                    enabled = !uiState.isSigningOut,
                    onClick = onSignOut,
                )
                ListGroupDivider()
                ListGroupItem(
                    headline = stringResource(R.string.feature_settings_delete_account),
                    icon = GeoWoofIcons.Delete,
                    isDestructive = true,
                    onClick = onDeleteAccount,
                )
            }
        }
    }
}

@Composable
private fun AccountSection(
    user: User,
    onVerifyEmail: () -> Unit,
    onChangePassword: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        SectionHeader(stringResource(R.string.feature_settings_account))
        ListGroup {
            ListGroupItem(
                headline = user.displayName,
                supporting = user.username?.let { stringResource(R.string.feature_settings_username, it) },
                icon = GeoWoofIcons.Person,
            )
            ListGroupDivider()
            ListGroupItem(
                headline = user.email,
                supporting = stringResource(
                    if (user.emailVerified) {
                        R.string.feature_settings_email_verified
                    } else {
                        R.string.feature_settings_email_unverified
                    },
                ),
                icon = GeoWoofIcons.Mail,
                trailing = if (user.emailVerified) {
                    null
                } else {
                    { TextButton(onClick = onVerifyEmail) { Text(stringResource(R.string.feature_settings_verify)) } }
                },
            )
            ListGroupDivider()
            ListGroupItem(
                headline = stringResource(
                    if (user.hasPassword) {
                        R.string.feature_settings_change_password
                    } else {
                        R.string.feature_settings_create_password
                    },
                ),
                supporting = if (user.hasPassword) {
                    null
                } else {
                    stringResource(R.string.feature_settings_create_password_summary)
                },
                icon = GeoWoofIcons.Lock,
                onClick = onChangePassword,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppearanceSection(
    appearance: Appearance,
    onThemeModeChange: (ThemeMode) -> Unit,
    onWallpaperColorsChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        SectionHeader(stringResource(R.string.feature_settings_appearance))
        ListGroup {
            Column(modifier = Modifier.padding(GeoWoofTheme.spacing.large)) {
                Text(
                    text = stringResource(R.string.feature_settings_theme),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(bottom = GeoWoofTheme.spacing.medium),
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    ThemeMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = appearance.themeMode == mode,
                            onClick = { onThemeModeChange(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
                        ) {
                            FittedText(stringResource(mode.labelRes()))
                        }
                    }
                }
            }
            // The system only has the colors of the wallpaper from Android 12 on.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ListGroupDivider()
                ListGroupItem(
                    headline = stringResource(R.string.feature_settings_wallpaper_colors),
                    supporting = stringResource(R.string.feature_settings_wallpaper_colors_summary),
                    icon = GeoWoofIcons.Palette,
                    trailing = { Switch(checked = appearance.useWallpaperColors, onCheckedChange = null) },
                    modifier = Modifier.toggleable(
                        value = appearance.useWallpaperColors,
                        role = Role.Switch,
                        onValueChange = onWallpaperColorsChange,
                    ),
                )
            }
        }
    }
}

private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.System -> R.string.feature_settings_theme_system
    ThemeMode.Light -> R.string.feature_settings_theme_light
    ThemeMode.Dark -> R.string.feature_settings_theme_dark
}

/** The system decides, and the app only asks once: this is the way back for whoever answered "Not now". */
@Composable
private fun NotificationsSection(isOn: Boolean, onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        SectionHeader(stringResource(R.string.feature_settings_notifications))
        ListGroup {
            ListGroupItem(
                headline = stringResource(R.string.feature_settings_allow_notifications),
                supporting = stringResource(
                    if (isOn) {
                        R.string.feature_settings_notifications_on
                    } else {
                        R.string.feature_settings_notifications_off
                    },
                ),
                icon = GeoWoofIcons.Notifications,
                onClick = onOpenSettings,
                trailing = { Icon(painterResource(GeoWoofIcons.ChevronRight), contentDescription = null) },
            )
        }
    }
}

@Composable
private fun PrivacySection(
    uiState: SettingsUiState,
    onPrivateChange: (Boolean) -> Unit,
    onLiveVisibilityChange: (LiveVisibility) -> Unit,
    onHideStartChange: (Int) -> Unit,
    onOpenBlockedUsers: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        SectionHeader(stringResource(R.string.feature_settings_privacy))
        ListGroup {
            uiState.user?.let { user ->
                ListGroupItem(
                    headline = stringResource(R.string.feature_settings_private_profile),
                    supporting = stringResource(
                        if (user.isPrivate) {
                            R.string.feature_settings_private_on
                        } else {
                            R.string.feature_settings_private_off
                        },
                    ),
                    icon = GeoWoofIcons.Lock,
                    // The whole row is the switch, for TalkBack too.
                    trailing = { Switch(checked = user.isPrivate, onCheckedChange = null) },
                    modifier = Modifier.toggleable(
                        value = user.isPrivate,
                        enabled = !uiState.isChangingPrivacy,
                        role = Role.Switch,
                        onValueChange = onPrivateChange,
                    ),
                )
                ListGroupDivider()
            }
            LiveSettings(
                settings = uiState.settings,
                onVisibilityChange = onLiveVisibilityChange,
                onHideStartChange = onHideStartChange,
            )
            ListGroupDivider()
            ListGroupItem(
                headline = stringResource(R.string.feature_settings_blocked_users),
                icon = GeoWoofIcons.Block,
                onClick = onOpenBlockedUsers,
                trailing = { Icon(painterResource(GeoWoofIcons.ChevronRight), contentDescription = null) },
            )
        }
    }
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
            onPrivateChange = {},
            onLiveVisibilityChange = {},
            onHideStartChange = {},
            onThemeModeChange = {},
            onWallpaperColorsChange = {},
            onDismissError = {},
            onSignOut = {},
            onConfirmSignOut = {},
            onDismissSignOut = {},
            onDeleteAccount = {},
        )
    }
}
