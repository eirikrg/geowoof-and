package com.geowoof.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.core.net.toUri
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.geowoof.app.ui.MainShell
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.feature.map.navigation.MapRoute
import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric

/** What the activity does with a link depends on what was left of it when the notification was tapped. */
@RunWith(AndroidJUnit4::class)
class NotificationLinksActivityTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun linkIntent() = Intent(context, MainActivity::class.java)
        .setAction(Intent.ACTION_VIEW)
        .setData(LINK.toUri())
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun MainActivity.pendingLink() =
        ViewModelProvider(this)[MainActivityViewModel::class.java].uiState.value.pendingLink

    @Test
    fun the_link_that_starts_the_activity_waits_and_the_navigation_does_not_see_it() {
        val activity = Robolectric.buildActivity(MainActivity::class.java, linkIntent()).setup().get()

        assertEquals(LINK, activity.pendingLink())
        assertNull(activity.intent.data)
    }

    @Test
    fun a_link_that_arrives_when_the_activity_exists_waits_too() {
        val controller = Robolectric.buildActivity(
            MainActivity::class.java,
            Intent(context, MainActivity::class.java),
        ).setup()

        controller.newIntent(linkIntent())

        assertEquals(LINK, controller.get().pendingLink())
    }

    /** Android kept the task: the activity comes back with the same intent, and that link was already opened. */
    @Test
    fun coming_back_from_its_saved_state_it_does_not_open_the_old_link_again() {
        val activity = Robolectric.buildActivity(MainActivity::class.java, linkIntent()).setup(Bundle()).get()

        assertNull(activity.pendingLink())
        assertNull(activity.intent.data)
    }

    private companion object {
        const val LINK = "geowoof://notifications"
    }
}

@RunWith(AndroidJUnit4::class)
class PendingLinkTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Serializable
    data object LinkedRoute

    private var opened = 0

    private fun show(link: String?) {
        var pendingLink by mutableStateOf(link)
        composeRule.setContent {
            GeoWoofTheme(dynamicColor = false) {
                MainShell(
                    pendingRequests = 0,
                    unreadMessages = 0,
                    unreadNotifications = 0,
                    pendingLink = pendingLink,
                    onConsumeLink = {
                        opened++
                        pendingLink = null
                    },
                ) { navController ->
                    NavHost(navController = navController, startDestination = MapRoute) {
                        composable<MapRoute> { Text("map screen") }
                        composable<LinkedRoute>(
                            deepLinks = listOf(navDeepLink<LinkedRoute>(basePath = "geowoof://linked")),
                        ) {
                            Text("linked screen")
                        }
                    }
                }
            }
        }
    }

    @Test
    fun a_link_waiting_since_before_the_first_composition_opens_once() {
        show("geowoof://linked")

        composeRule.onNodeWithText("linked screen").assertIsDisplayed()
        assertEquals(1, opened)
    }

    @Test
    fun a_link_that_leads_nowhere_is_given_up() {
        show("geowoof://nowhere")

        composeRule.onNodeWithText("map screen").assertIsDisplayed()
        assertEquals(1, opened)
    }
}
