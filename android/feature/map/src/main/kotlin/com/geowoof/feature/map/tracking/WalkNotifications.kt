package com.geowoof.feature.map.tracking

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import com.geowoof.core.designsystem.icon.GeoWoofIcons
import com.geowoof.core.model.ActiveWalkStatus
import com.geowoof.core.ui.distanceText
import com.geowoof.feature.map.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** The notifications of a walk: a persistent one while recording, and a notice if it stops by itself. */
class WalkNotifications @Inject constructor(@ApplicationContext private val context: Context) {
    private val manager: NotificationManager? get() = context.getSystemService()

    /** What the service shows the moment it starts, before reading the walk. */
    fun initial(): Notification {
        createChannel()
        return base().setContentTitle(context.getString(R.string.feature_map_notification_preparing)).build()
    }

    /** Without the permission of Android 13+ this shows nothing, and the walk is recorded anyway. */
    fun show(state: WalkNotificationState) {
        manager?.notify(TRACKING_ID, build(state))
    }

    fun showInterrupted() {
        createChannel()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(GeoWoofIcons.DirectionsWalk)
            .setContentTitle(context.getString(R.string.feature_map_interrupted_title))
            .setContentText(context.getString(R.string.feature_map_interrupted_message))
            .setContentIntent(openApp())
            .setAutoCancel(true)
            .build()
        manager?.notify(INTERRUPTED_ID, notification)
    }

    fun cancelInterrupted() {
        manager?.cancel(INTERRUPTED_ID)
    }

    private fun build(state: WalkNotificationState): Notification {
        val builder = base().setContentTitle(context.getString(state.titleRes()))
        if (state.status == ActiveWalkStatus.Preparing) return builder.build()

        builder.setContentText(context.distanceText(state.distanceM))
        if (state.status == ActiveWalkStatus.Recording) {
            builder.setUsesChronometer(true).setWhen(state.startedAtEpochMs).setShowWhen(true)
            builder.addAction(action(GeoWoofIcons.Pause, R.string.feature_map_pause, WalkTrackingService.ACTION_PAUSE))
        } else {
            builder.addAction(
                action(GeoWoofIcons.PlayArrow, R.string.feature_map_resume, WalkTrackingService.ACTION_RESUME),
            )
        }

        return builder
            .addAction(action(GeoWoofIcons.Stop, R.string.feature_map_finish, WalkTrackingService.ACTION_FINISH))
            .build()
    }

    private fun WalkNotificationState.titleRes(): Int = when (status) {
        ActiveWalkStatus.Preparing -> R.string.feature_map_notification_preparing
        ActiveWalkStatus.Paused -> R.string.feature_map_notification_paused
        else -> R.string.feature_map_notification_recording
    }

    private fun base(): NotificationCompat.Builder = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(GeoWoofIcons.DirectionsWalk)
        .setCategory(NotificationCompat.CATEGORY_WORKOUT)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        .setContentIntent(openApp())

    private fun action(icon: Int, label: Int, serviceAction: String): NotificationCompat.Action {
        val intent = Intent(context, WalkTrackingService::class.java).setAction(serviceAction)
        val pending = PendingIntent.getService(context, serviceAction.hashCode(), intent, PENDING_INTENT_FLAGS)
        return NotificationCompat.Action.Builder(icon, context.getString(label), pending).build()
    }

    private fun openApp(): PendingIntent? = context.packageManager.getLaunchIntentForPackage(context.packageName)
        ?.let { PendingIntent.getActivity(context, 0, it, PENDING_INTENT_FLAGS) }

    private fun createChannel() {
        val channel = NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
            .setName(context.getString(R.string.feature_map_notification_channel))
            .build()
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    companion object {
        const val TRACKING_ID = 1001
        private const val INTERRUPTED_ID = 1002
        private const val CHANNEL_ID = "walk_tracking"
        private const val PENDING_INTENT_FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    }
}
