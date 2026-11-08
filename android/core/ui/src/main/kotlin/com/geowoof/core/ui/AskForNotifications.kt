package com.geowoof.core.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.geowoof.core.designsystem.component.GeoWoofIllustrations
import com.geowoof.core.designsystem.component.IllustratedDialog

/**
 * Asks for the notifications right after something that needs them was made, such as a reminder, saying what they
 * are for. As [RequestNotificationPermission], only once per device: someone who said no is not asked again. [onDone]
 * runs once when it is answered, or at once if there is nothing to ask: they are allowed, it was asked before, or the
 * system is older than Android 13, which has no such permission.
 */
@Composable
fun AskForNotifications(title: String, message: String, onDone: () -> Unit) {
    val currentOnDone by rememberUpdatedState(onDone)
    var isFinished by remember { mutableStateOf(false) }
    var isWaitingForSystem by remember { mutableStateOf(false) }
    val finish = {
        if (!isFinished) {
            isFinished = true
            currentOnDone()
        }
    }
    // Before returning, so that it stays registered while the system is asking.
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { finish() }
    if (isFinished || isWaitingForSystem) return
    val inbox = LocalInbox.current?.takeIf { it.asksForNotifications }
    val context = LocalContext.current
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && inbox != null && !context.mayPostNotifications()) {
        IllustratedDialog(
            illustration = GeoWoofIllustrations.PermissionNotifications,
            title = title,
            message = message,
            confirmLabel = stringResource(R.string.core_ui_notifications_turn_on),
            onConfirm = {
                isWaitingForSystem = true
                inbox.onAskedForNotifications()
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            },
            dismissLabel = stringResource(R.string.core_ui_notifications_not_now),
            onDismiss = {
                inbox.onAskedForNotifications()
                finish()
            },
        )
    } else {
        LaunchedEffect(Unit) { finish() }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun Context.mayPostNotifications(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

/** Whether the notifications of the app are on, again each time the screen comes back: they change in the settings. */
@Composable
fun rememberNotificationsEnabled(): Boolean {
    val context = LocalContext.current
    var isEnabled by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    LifecycleResumeEffect(context) {
        isEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        onPauseOrDispose {}
    }
    return isEnabled
}

/** The settings of the notifications of the app, where someone who said no can turn them on. */
fun Context.openNotificationSettings() {
    startActivity(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}
