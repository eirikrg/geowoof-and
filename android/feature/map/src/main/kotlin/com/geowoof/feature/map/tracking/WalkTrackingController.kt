package com.geowoof.feature.map.tracking

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/** Lets the screens start the service without knowing about it, so that they can be tested. */
interface WalkTrackingController {
    /** Whether the service is alive in this process. After the death of the process, it is not. */
    val isRunning: Boolean

    /** It reads what to do from Room. `false` if the system does not allow it right now. */
    fun start(): Boolean
}

@Singleton
class ServiceWalkTrackingController @Inject constructor(@ApplicationContext private val context: Context) :
    WalkTrackingController {
    @Volatile
    override var isRunning: Boolean = false

    override fun start(): Boolean = try {
        ContextCompat.startForegroundService(context, Intent(context, WalkTrackingService::class.java))
        isRunning = true
        true
    } catch (@Suppress("SwallowedException") exception: IllegalStateException) {
        // Android 12+ does not let an app in the background start a foreground service.
        false
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal interface WalkTrackingModule {
    @Binds
    fun bindsWalkTrackingController(controller: ServiceWalkTrackingController): WalkTrackingController
}
