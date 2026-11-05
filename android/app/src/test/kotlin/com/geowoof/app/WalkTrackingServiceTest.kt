package com.geowoof.app

import android.app.Service
import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.geowoof.feature.map.tracking.WalkTrackingService
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf

/** The real service with the real Hilt graph. WalkTrackingSessionTest covers what it does with a walk. */
@RunWith(AndroidJUnit4::class)
class WalkTrackingServiceTest {
    @Test
    fun it_enters_the_foreground_and_stops_by_itself_when_there_is_no_walk() {
        val service: Service = Robolectric.buildService(WalkTrackingService::class.java).create().get()

        val result = service.onStartCommand(null, 0, 1)

        assertTrue(result == Service.START_STICKY)
        assertNotNull(shadowOf(service).lastForegroundNotification)
        val deadline = System.currentTimeMillis() + WAIT_MILLIS
        while (!shadowOf(service).isStoppedBySelf && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(POLL_MILLIS)
        }
        assertTrue(shadowOf(service).isStoppedBySelf)
    }

    private companion object {
        const val WAIT_MILLIS = 10_000L
        const val POLL_MILLIS = 20L
    }
}
