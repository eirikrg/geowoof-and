package com.geowoof.core.designsystem.component

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.core.graphics.get
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.geowoof.core.designsystem.icon.GeoWoofIcons
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** With real graphics, to open the files themselves. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h915dp")
class GeoWoofIllustrationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun every_illustration_opens_with_nothing_around_its_background() {
        val resources = ApplicationProvider.getApplicationContext<Context>().resources
        listOf(
            GeoWoofIllustrations.EmptyBlocked,
            GeoWoofIllustrations.EmptyFeed,
            GeoWoofIllustrations.EmptyFriends,
            GeoWoofIllustrations.EmptyMessages,
            GeoWoofIllustrations.EmptyNotifications,
            GeoWoofIllustrations.EmptyPets,
            GeoWoofIllustrations.EmptyPhotos,
            GeoWoofIllustrations.EmptyRequests,
            GeoWoofIllustrations.EmptySaved,
            GeoWoofIllustrations.EmptySearch,
            GeoWoofIllustrations.EmptyWalks,
            GeoWoofIllustrations.ErrorConnection,
            GeoWoofIllustrations.FirstPet,
            GeoWoofIllustrations.PermissionLocation,
            GeoWoofIllustrations.PermissionNotifications,
            GeoWoofIllustrations.WalkDone,
            GeoWoofIllustrations.WelcomeHero,
        ).forEach { illustration ->
            val name = resources.getResourceEntryName(illustration)
            val bitmap = BitmapFactory.decodeResource(resources, illustration)

            assertTrue("$name is too small: ${bitmap.width}", bitmap.width >= MIN_WIDTH_PX)
            // A background that is not transparent would be seen as a box in the dark theme.
            val cornerAlpha = Color.alpha(bitmap[0, 0])
            assertTrue("$name has a background of alpha $cornerAlpha", cornerAlpha < MAX_INVISIBLE_ALPHA)
        }
    }

    @Test
    fun under_the_header_of_a_profile_it_leaves_room_for_the_text() {
        composeRule.setContent {
            GeoWoofTheme(dynamicColor = false) {
                LazyColumn {
                    item {
                        EmptyState(
                            icon = GeoWoofIcons.Pets,
                            title = "No pets yet",
                            message = "",
                            illustration = GeoWoofIllustrations.EmptyPets,
                        )
                    }
                }
            }
        }

        val top = composeRule.onNodeWithText("No pets yet").getUnclippedBoundsInRoot().top

        assertTrue("The title starts at $top", top <= 190.dp)
    }

    private companion object {
        const val MIN_WIDTH_PX = 800

        /** The originals have a haze of 1 in 255 in some corners, which nobody can see. */
        const val MAX_INVISIBLE_ALPHA = 8
    }
}
