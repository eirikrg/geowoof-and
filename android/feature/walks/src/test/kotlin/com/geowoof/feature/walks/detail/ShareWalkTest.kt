package com.geowoof.feature.walks.detail

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShareWalkTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun the_image_is_written_as_a_png_and_replaces_the_previous_one() {
        writeSharedImage(context, createBitmap(8, 8))

        val file = writeSharedImage(context, createBitmap(32, 16))

        assertEquals(listOf("walk.png"), file.parentFile?.list()?.toList())
        val stored = BitmapFactory.decodeFile(file.path)
        assertEquals(32, stored.width)
        assertEquals(16, stored.height)
    }

    @Test
    fun the_app_declares_the_provider_of_the_links_without_exposing_it() {
        val provider = context.packageManager.resolveContentProvider(
            shareAuthority(context),
            PackageManager.GET_META_DATA,
        )

        assertNotNull(provider)
        assertFalse(provider!!.exported)
        assertTrue(provider.grantUriPermissions)
    }

    @Test
    fun the_share_sheet_receives_the_image_with_permission_to_read_it() {
        val intent = shareIntent("content://geowoof/walk.png".toUri())

        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("image/png", intent.type)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }
}
