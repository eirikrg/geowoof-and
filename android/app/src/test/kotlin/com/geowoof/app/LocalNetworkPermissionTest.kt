package com.geowoof.app

import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** An emulator with the IP of the computer would not notice if this goes missing: it is outside its network. */
@RunWith(AndroidJUnit4::class)
class LocalNetworkPermissionTest {
    @Test
    fun a_debug_build_can_ask_to_reach_the_computer_on_the_local_network() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)

        assertTrue("android.permission.ACCESS_LOCAL_NETWORK" in info.requestedPermissions.orEmpty())
    }
}
