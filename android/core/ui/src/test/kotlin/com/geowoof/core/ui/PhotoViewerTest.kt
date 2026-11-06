package com.geowoof.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.core.model.RemoteImage
import com.geowoof.core.model.WalkPhoto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhotoViewerTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun photo(id: String) = ViewerPhoto(RemoteImage(id, "https://thumb/$id", "https://medium/$id"))

    private fun show(photos: List<ViewerPhoto>, initialPage: Int = 0, onDismiss: (Int) -> Unit = {}) {
        composeRule.setContent {
            GeoWoofTheme(dynamicColor = false) {
                PhotoViewer(photos, initialPage, contentDescription = "Photo by Bea", onDismiss = onDismiss)
            }
        }
    }

    @Test
    fun it_says_which_photo_it_is_in_words_for_talkback() {
        show(listOf(photo("1"), photo("2"), photo("3")), initialPage = 1)

        composeRule.onNodeWithContentDescription("Photo 2 of 3").assertIsDisplayed()
    }

    @Test
    fun a_single_photo_does_not_say_how_many_there_are() {
        show(listOf(photo("1")))

        composeRule.onNodeWithText("1/1").assertDoesNotExist()
    }

    @Test
    fun closing_it_says_which_photo_it_was_on() {
        var closedOn: Int? = null
        show(listOf(photo("1"), photo("2")), initialPage = 1, onDismiss = { closedOn = it })

        composeRule.onNodeWithContentDescription("Close").performClick()

        assertEquals(1, closedOn)
    }

    @Test
    fun a_photo_that_is_still_being_processed_cannot_be_opened() {
        assertFalse(ViewerPhoto(RemoteImage("1", null, null)).canOpen)
        assertFalse(ViewerPhoto(null).canOpen)
        assertTrue(ViewerPhoto(RemoteImage("1", "https://thumb", null)).canOpen)
        assertTrue(ViewerPhoto(null, localPath = "/data/photo.jpg").canOpen)
    }

    @Test
    fun touching_a_photo_of_a_walk_enlarges_it_on_that_one() {
        val photos =
            listOf(WalkPhoto(RemoteImage("1", "https://t/1", "https://m/1"), null), WalkPhoto(null, "/data/2.jpg"))
        composeRule.setContent {
            GeoWoofTheme(dynamicColor = false) {
                WalkPhotosRow(photos, photoSize = 120.dp)
            }
        }

        composeRule.onAllNodesWithContentDescription("Photo of the walk")[1].performClick()

        composeRule.onNodeWithContentDescription("Photo 2 of 2").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Close").performClick()
        composeRule.onNodeWithContentDescription("Close").assertDoesNotExist()
    }

    @Test
    fun a_double_tap_goes_closer_and_another_goes_back() {
        val zoom = PhotoZoom()
        showGestures(zoom)

        composeRule.onNodeWithTag("page").performTouchInput { doubleClick() }
        composeRule.waitForIdle()
        assertEquals(2.5f, zoom.scale)

        composeRule.onNodeWithTag("page").performTouchInput { doubleClick() }
        composeRule.waitForIdle()
        assertEquals(1f, zoom.scale)
    }

    /** The gestures on a real pager, as in the viewer, without its dialog. */
    private fun showGestures(zoom: PhotoZoom): () -> PagerState {
        lateinit var pager: PagerState
        composeRule.setContent {
            pager = rememberPagerState { 2 }
            HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = !zoom.isZoomed,
            ) {
                Box(
                    Modifier
                        .testTag("page")
                        .fillMaxSize()
                        .photoZoomGestures(zoom),
                )
            }
        }
        return { pager }
    }

    private fun pinchOut() = composeRule.onNodeWithTag("page").performTouchInput {
        pinch(
            start0 = center + Offset(-20f, 0f),
            end0 = center + Offset(-150f, 0f),
            start1 = center + Offset(20f, 0f),
            end1 = center + Offset(150f, 0f),
        )
    }

    @Test
    fun two_fingers_enlarge_the_photo_and_do_not_go_to_the_next_one() {
        val zoom = PhotoZoom()
        val pager = showGestures(zoom)

        pinchOut()
        composeRule.waitForIdle()

        assertTrue("The scale is ${zoom.scale}", zoom.scale > 1f)
        assertEquals(0, pager().currentPage)
    }

    @Test
    fun one_finger_goes_to_the_next_photo_when_it_is_not_enlarged() {
        val zoom = PhotoZoom()
        val pager = showGestures(zoom)

        composeRule.onNodeWithTag("page").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()

        assertEquals(1, pager().currentPage)
        assertFalse(zoom.isZoomed)
    }

    @Test
    fun one_finger_moves_an_enlarged_photo_and_stays_on_it() {
        val zoom = PhotoZoom()
        val pager = showGestures(zoom)
        pinchOut()
        composeRule.waitForIdle()
        val before = zoom.offset

        composeRule.onNodeWithTag("page").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()

        assertEquals(0, pager().currentPage)
        assertTrue("It moved from $before to ${zoom.offset}", zoom.offset.x < before.x)
    }
}
