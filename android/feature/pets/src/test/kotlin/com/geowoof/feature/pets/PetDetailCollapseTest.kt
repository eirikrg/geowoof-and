package com.geowoof.feature.pets

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
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.core.model.WeightEntry
import com.geowoof.core.testing.TestPets
import com.geowoof.feature.pets.detail.PetDetailActions
import com.geowoof.feature.pets.detail.PetDetailContent
import com.geowoof.feature.pets.detail.PetDetailUiState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * The page of a pet goes up like a profile: its photo flies from the band to the bar. With native graphics, because
 * the size of the texts decides whether the second line fits in the bar.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w412dp-h915dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PetDetailCollapseTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val calls = mutableListOf<String>()
    private val list = LazyListState()

    private fun show(uiState: PetDetailUiState = PetDetailUiState(pet = TestPets.pet(), weights = weights(WEIGHTS))) {
        composeRule.setContent {
            GeoWoofTheme(dynamicColor = false) {
                PetDetailContent(
                    uiState = uiState,
                    weightText = TextFieldState(),
                    actions = PetDetailActions(
                        onRefresh = {},
                        onErrorShown = {},
                        onAddWeight = {},
                        onEditWeight = {},
                        onWeightDateChange = {},
                        onSaveWeight = {},
                        onDeleteWeight = {},
                        onDismissWeight = {},
                        onRequestDelete = {},
                        onConfirmDelete = {},
                        onDismissDelete = {},
                    ),
                    onBack = { calls += "back" },
                    onEdit = { calls += "edit" },
                    onOpenWalks = { calls += "walks" },
                    listState = list,
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun weights(count: Int) = List(count) { WeightEntry("w$it", "luna", LocalDate.of(2026, 1, it + 1), 18.0) }

    private fun goDown() {
        composeRule.runOnIdle { runBlocking { list.scrollToItem(index = 2) } }
        composeRule.waitForIdle()
    }

    private fun scrollBy(distance: Dp) {
        composeRule.runOnIdle { runBlocking { list.scrollBy(with(composeRule.density) { distance.toPx() }) } }
        composeRule.waitForIdle()
    }

    private fun photo() = composeRule.onNodeWithContentDescription("Pet photo").getBoundsInRoot()

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
        assertTrue("${photo.height} tall", photo.height >= 48.dp)
        assertEquals(barMiddle().value + 104f, ((photo.top + photo.bottom) / 2).value, 1f)
        assertEquals(206f, ((photo.left + photo.right) / 2).value, 1f)
    }

    @Test
    fun at_the_start_the_photo_is_big_in_the_band() {
        show()

        assertPhotoIsInTheBand()
    }

    @Test
    fun going_down_the_photo_and_the_name_reach_the_bar() {
        show()

        goDown()

        assertPhotoIsInTheBar()
        val photo = photo()
        val breed = composeRule.onNodeWithText("Border collie", useUnmergedTree = true).getBoundsInRoot()
        assertTrue("it ends at ${breed.bottom}", breed.bottom <= barMiddle() + 32.dp)
        assertTrue("it starts at ${breed.left}, the photo ends at ${photo.right}", breed.left >= photo.right)
    }

    @Test
    fun coming_back_to_the_start_the_photo_goes_back_to_the_band() {
        show()
        goDown()

        composeRule.runOnIdle { runBlocking { list.scrollToItem(0) } }
        composeRule.waitForIdle()

        assertPhotoIsInTheBand()
    }

    /** As in a profile, it does not stay in the air between the band and the bar. */
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

    @Test
    fun the_buttons_of_the_bar_still_work_with_the_photo_in_it() {
        show()
        goDown()

        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.onNodeWithContentDescription("Edit pet").performClick()

        assertEquals(listOf("back", "edit"), calls)
    }

    @Test
    fun while_it_loads_there_is_no_photo_to_fly() {
        show(PetDetailUiState(isRefreshing = true))

        composeRule.onNodeWithContentDescription("Pet photo").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Back").assertIsDisplayed()
    }

    private companion object {
        const val WEIGHTS = 12
    }
}
