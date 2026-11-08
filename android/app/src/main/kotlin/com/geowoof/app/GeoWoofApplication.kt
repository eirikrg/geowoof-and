package com.geowoof.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.geowoof.core.common.ApplicationScope
import com.geowoof.core.notifications.HealthReminders
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject

@HiltAndroidApp
class GeoWoofApplication :
    Application(),
    Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var healthReminders: HealthReminders

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    /** The reminders of the health of the pets follow what Room has for as long as the process lives. */
    override fun onCreate() {
        super.onCreate()
        healthReminders.start(applicationScope)
    }
}
