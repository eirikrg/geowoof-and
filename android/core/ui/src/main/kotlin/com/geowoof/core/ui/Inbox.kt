package com.geowoof.core.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.geowoof.core.designsystem.component.GeoWoofIllustrations
import com.geowoof.core.designsystem.component.IllustratedDialog
import com.geowoof.core.designsystem.icon.GeoWoofIcons
import com.geowoof.core.designsystem.theme.GeoWoofTheme

/**
 * What is waiting for this person, and how to reach it. Messages and notifications are other
 * features, and a feature cannot depend on another: the app provides it.
 */
data class Inbox(
    val unreadMessages: Int,
    val unreadNotifications: Int,
    val onOpenMessages: () -> Unit,
    val onOpenNotifications: () -> Unit,
    /** This device has not been asked yet whether it wants notifications. */
    val asksForNotifications: Boolean = false,
    val onAskedForNotifications: () -> Unit = {},
)

/** Without one, as in the tests of a screen, nothing is shown. */
val LocalInbox = compositionLocalOf<Inbox?> { null }

/** The actions of a top app bar. */
@Composable
fun InboxActions(modifier: Modifier = Modifier) {
    val inbox = LocalInbox.current ?: return
    Row(modifier = modifier) {
        IconButton(onClick = inbox.onOpenNotifications) { NotificationsIcon(inbox.unreadNotifications) }
        IconButton(onClick = inbox.onOpenMessages) { MessagesIcon(inbox.unreadMessages) }
    }
}

/** The same over the map, which has no top app bar: together, on a surface that stands out from it. */
@Composable
fun FloatingInboxActions(modifier: Modifier = Modifier) {
    val inbox = LocalInbox.current ?: return
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = FloatingElevation,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(GeoWoofTheme.spacing.extraSmall)) {
            IconButton(onClick = inbox.onOpenNotifications) { NotificationsIcon(inbox.unreadNotifications) }
            IconButton(onClick = inbox.onOpenMessages) { MessagesIcon(inbox.unreadMessages) }
        }
    }
}

@Composable
private fun NotificationsIcon(count: Int) {
    CountedIcon(
        icon = GeoWoofIcons.Notifications,
        count = count,
        description = if (count > 0) {
            pluralStringResource(R.plurals.core_ui_unread_notifications, count, count)
        } else {
            stringResource(R.string.core_ui_notifications)
        },
    )
}

@Composable
private fun MessagesIcon(count: Int) {
    CountedIcon(
        icon = GeoWoofIcons.ChatBubble,
        count = count,
        description = if (count > 0) {
            pluralStringResource(R.plurals.core_ui_unread_messages, count, count)
        } else {
            stringResource(R.string.core_ui_messages)
        },
    )
}

@Composable
private fun CountedIcon(icon: Int, count: Int, description: String, modifier: Modifier = Modifier) {
    BadgedBox(
        modifier = modifier,
        badge = {
            if (count > 0) {
                val isTooMany = count > MAX_COUNT
                Badge(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary,
                ) {
                    Text(if (isTooMany) stringResource(R.string.core_ui_many_unread, MAX_COUNT) else count.toString())
                }
            }
        },
    ) {
        Icon(painter = painterResource(icon), contentDescription = description)
    }
}

/**
 * Says what notifications are for the first time they are useful, and only then the system asks. From
 * Android 13 on, without the permission there are no pushes. It asks once, whatever the answer: someone
 * who says no is not asked again on each visit.
 */
@Composable
fun RequestNotificationPermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val inbox = LocalInbox.current ?: return
    // Before returning, so that it stays registered while the system is asking.
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val permission = Manifest.permission.POST_NOTIFICATIONS
    val state = ContextCompat.checkSelfPermission(LocalContext.current, permission)
    if (inbox.asksForNotifications && state != PackageManager.PERMISSION_GRANTED) {
        IllustratedDialog(
            illustration = GeoWoofIllustrations.PermissionNotifications,
            title = stringResource(R.string.core_ui_notifications_title),
            message = stringResource(R.string.core_ui_notifications_message),
            confirmLabel = stringResource(R.string.core_ui_notifications_turn_on),
            onConfirm = {
                inbox.onAskedForNotifications()
                launcher.launch(permission)
            },
            dismissLabel = stringResource(R.string.core_ui_notifications_not_now),
            onDismiss = inbox.onAskedForNotifications,
        )
    }
}

private const val MAX_COUNT = 99
private val FloatingElevation = 3.dp
