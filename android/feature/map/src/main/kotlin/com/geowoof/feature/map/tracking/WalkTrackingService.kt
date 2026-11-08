package com.geowoof.feature.map.tracking

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.geowoof.core.data.repository.ActiveWalkRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Keeps recording with the screen off, in the foreground and with a persistent notification. It does
 * not ask for background location. If the system kills the process, `START_STICKY` brings it back
 * and it continues from what is in Room.
 */
@AndroidEntryPoint
class WalkTrackingService : Service() {
    @Inject
    lateinit var session: WalkTrackingSession

    @Inject
    lateinit var activeWalks: ActiveWalkRepository

    @Inject
    lateinit var controller: ServiceWalkTrackingController

    @Inject
    lateinit var notifications: WalkNotifications

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var recording: Job? = null
    private var notificationUpdates: Job? = null
    private var isInForeground = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        controller.isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!enterForeground()) {
            scope.launch {
                activeWalks.interrupt()
                stop(interrupted = true)
            }
            return START_NOT_STICKY
        }

        notifications.cancelInterrupted()
        when (intent?.action) {
            ACTION_PAUSE -> scope.launch { activeWalks.pause() }
            ACTION_RESUME -> scope.launch { activeWalks.resume() }
            ACTION_FINISH -> scope.launch { activeWalks.finish() }
        }
        if (recording == null) {
            // Without an intent, it is the system bringing the service back.
            recording = scope.launch { record(restoring = intent == null) }
            notificationUpdates = scope.launch { session.notificationStates.collect(notifications::show) }
        }
        return START_STICKY
    }

    private suspend fun record(restoring: Boolean) {
        session.run(restoring)
        stop(interrupted = session.isInterrupted())
    }

    private fun stop(interrupted: Boolean) {
        notificationUpdates?.cancel()
        if (interrupted) notifications.showInterrupted()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /**
     * The system refuses when the app is in the background or has lost the permission. The type
     * of the service is a constant copied at build time, and ServiceCompat ignores it before Android 10.
     */
    @SuppressLint("InlinedApi")
    private fun enterForeground(): Boolean = try {
        // Once is enough: doing it again would replace what the notification is showing.
        if (!isInForeground) {
            ServiceCompat.startForeground(
                this,
                WalkNotifications.TRACKING_ID,
                notifications.initial(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
            isInForeground = true
        }
        true
    } catch (@Suppress("SwallowedException") exception: IllegalStateException) {
        false
    } catch (@Suppress("SwallowedException") exception: SecurityException) {
        false
    }

    override fun onDestroy() {
        controller.isRunning = false
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_PAUSE = "com.geowoof.feature.map.action.PAUSE"
        const val ACTION_RESUME = "com.geowoof.feature.map.action.RESUME"
        const val ACTION_FINISH = "com.geowoof.feature.map.action.FINISH"
    }
}
