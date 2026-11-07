package com.geowoof.feature.profile

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.core.model.Relationship
import com.geowoof.core.model.RelationshipStatus
import com.geowoof.core.model.Walk
import com.geowoof.core.testing.DrainMainLooperRule
import com.geowoof.core.testing.TestPets
import com.geowoof.core.testing.TestPosts
import com.geowoof.core.testing.TestSocial
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** With native graphics, because the size of the texts decides whether the name fits in the bar. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w412dp-h915dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProfileCollapseTest {
    @get:Rule(order = -2)
    val drainMainLooper = DrainMainLooperRule()

    @get:Rule
    val composeRule = createComposeRule()

    private val calls = mutableListOf<String>()
    private val list = LazyListState()

    private val bea = ProfileUiState(
        isLoading = false,
        profile = TestPets.profile(displayName = "Bea", username = "bea", city = "Madrid"),
        relationship = Relationship(RelationshipStatus.Following),
    )

    private fun show(uiState: ProfileUiState = bea, photos: Int = 45) {
        val posts = List(photos) { TestPosts.item(TestPosts.post("post-$it")) }
        composeRule.setContent {
            GeoWoofTheme(dynamicColor = false) {
                ProfileContent(
                    uiState = uiState,
                    lists = ProfileLists(
                        posts = flowOf(TestSocial.pages(posts)).collectAsLazyPagingItems(),
                        walks = flowOf(TestSocial.pages(emptyList<Walk>())).collectAsLazyPagingItems(),
                        saved = null,
                    ),
                    reportDetails = TextFieldState(),
                    actions = ProfileActions(onBack = { calls += "back" }),
                    listState = list,
                )
            }
        }
        // The pages come by a coroutine that the clock of the test does not wait for.
        composeRule.waitUntil(WAIT_MILLIS) { list.layoutInfo.totalItemsCount > FEW_ITEMS || photos == 0 }
        composeRule.waitForIdle()
    }

    private fun goToTheTabs() {
        composeRule.runOnIdle { runBlocking { list.scrollToItem(index = TABS_INDEX) } }
        composeRule.waitForIdle()
    }

    private fun scrollBy(distance: Dp) {
        composeRule.runOnIdle { runBlocking { list.scrollBy(with(composeRule.density) { distance.toPx() }) } }
        composeRule.waitForIdle()
    }

    /** The icon of a person without a photo, which is a bit more than half of it: 54 dp in the band. */
    private fun photo() = composeRule.onNodeWithContentDescription("Profile photo").getBoundsInRoot()

    private fun barMiddle(): Dp {
        val back = composeRule.onNodeWithContentDescription("Back").getBoundsInRoot()
        return (back.top + back.bottom) / 2
    }

    private fun assertPhotoIsInTheBar() {
        val photo = photo()
        assertTrue("${photo.height} tall", photo.height <= 24.dp)
        assertEquals(barMiddle().value, ((photo.top + photo.bottom) / 2).value, 1f)
        assertTrue("it ends at ${photo.right}", photo.right <= 120.dp)
    }

    /** Centered, with its center on the edge of the band: half of the bar and 72 dp under the middle of it. */
    private fun assertPhotoIsInTheBand() {
        val photo = photo()
        assertEquals(54f, photo.height.value, 1f)
        assertEquals(barMiddle().value + 104f, ((photo.top + photo.bottom) / 2).value, 1f)
        assertEquals(206f, ((photo.left + photo.right) / 2).value, 1f)
    }

    @Test
    fun at_the_start_the_bar_says_the_username_and_the_photo_is_big_in_the_band() {
        show()

        composeRule.onNodeWithText("@bea").assertIsDisplayed()
        assertPhotoIsInTheBand()
    }

    @Test
    fun going_up_the_photo_and_the_name_reach_the_bar() {
        show()

        goToTheTabs()

        assertPhotoIsInTheBar()
        val photo = photo()
        val username = composeRule.onNodeWithText("@bea", useUnmergedTree = true).getBoundsInRoot()
        assertTrue("it ends at ${username.bottom}", username.bottom <= barMiddle() + 32.dp)
        assertTrue("it starts at ${username.left}, the photo ends at ${photo.right}", username.left >= photo.right)
    }

    @Test
    fun coming_back_to_the_start_the_photo_goes_back_to_the_band() {
        show()
        goToTheTabs()
        assertPhotoIsInTheBar()

        composeRule.runOnIdle { runBlocking { list.scrollToItem(0) } }
        composeRule.waitForIdle()

        assertPhotoIsInTheBand()
    }

    /** It only moves with the scroll: without this, it could stay in the air between the band and the bar. */
    @Test
    fun letting_go_near_the_bar_the_photo_finishes_arriving() {
        show()

        scrollBy(90.dp)

        assertPhotoIsInTheBar()
    }

    @Test
    fun letting_go_near_the_start_the_photo_goes_back_to_the_band() {
        show()

        scrollBy(20.dp)

        assertEquals(0, list.firstVisibleItemScrollOffset)
        assertPhotoIsInTheBand()
    }

    /** It has nothing else to show: at the top, everything is in sight. */
    @Test
    fun a_list_that_cannot_go_all_the_way_goes_back_to_the_start() {
        show(photos = 0)

        scrollBy(90.dp)

        assertEquals(0, list.firstVisibleItemScrollOffset)
        assertPhotoIsInTheBand()
    }

    /** It goes over the bar and the page, but it does not stop the touches on them. */
    @Test
    fun the_back_button_still_works_with_the_photo_in_the_bar() {
        show()
        goToTheTabs()

        composeRule.onNodeWithContentDescription("Back").performClick()

        assertEquals(listOf("back"), calls)
    }

    @Test
    fun what_is_in_the_page_can_be_touched_with_the_photo_over_it() {
        show()

        composeRule.onNodeWithText("Followers").performClick()
        composeRule.onNodeWithText("@bea").assertIsDisplayed()
    }

    @Test
    fun an_unavailable_profile_has_no_photo() {
        show(bea.copy(isUnavailable = true), photos = 0)

        composeRule.onNodeWithContentDescription("Profile photo").assertDoesNotExist()
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L
        const val FEW_ITEMS = 10
        const val TABS_INDEX = 3
    }
}
