package com.geowoof.feature.profile

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.paging.LoadState
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.core.model.Post
import com.geowoof.core.model.TaggedPet
import com.geowoof.core.testing.TestPosts
import com.geowoof.core.testing.TestSocial
import com.geowoof.core.ui.relativeToNow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w412dp-h915dp")
class SavedPostsTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val luna = TaggedPet("luna-id", "Luna", null)
    private val toby = TaggedPet("toby-id", "Toby", null)

    private fun show(post: Post, onClick: () -> Unit = {}) {
        composeRule.setContent {
            GeoWoofTheme(dynamicColor = false) { SavedPostCard(post, onClick) }
        }
    }

    private fun showPlaceholder(refresh: LoadState, onRetry: () -> Unit = {}) {
        composeRule.setContent {
            GeoWoofTheme(dynamicColor = false) { SavedPostsPlaceholder(refresh, onRetry) }
        }
    }

    @Test
    fun it_says_who_published_it_when_what_it_says_and_who_is_in_it() {
        show(TestPosts.post(text = "En el Retiro").copy(pets = listOf(luna)))

        composeRule.onNodeWithText("Bea").assertIsDisplayed()
        composeRule.onNodeWithText(TestSocial.SINCE.relativeToNow()).assertIsDisplayed()
        composeRule.onNodeWithText("En el Retiro").assertIsDisplayed()
        composeRule.onNodeWithText("Luna").assertIsDisplayed()
    }

    @Test
    fun touching_any_part_of_it_opens_the_post() {
        var opened = 0
        show(TestPosts.post(), onClick = { opened++ })

        composeRule.onNodeWithText("En el Retiro").performClick()

        assertEquals(1, opened)
    }

    @Test
    fun talkback_hears_the_counts_as_words_and_not_only_as_numbers() {
        show(TestPosts.post(likeCount = 12, commentCount = 1))

        composeRule.onNodeWithContentDescription("12 likes").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("1 comment").assertIsDisplayed()
    }

    @Test
    fun a_count_of_zero_says_nothing() {
        show(TestPosts.post(likeCount = 0, commentCount = 0))

        composeRule.onNodeWithContentDescription("0 likes").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("0 comments").assertDoesNotExist()
    }

    @Test
    fun several_photos_say_how_many_and_a_single_one_does_not() {
        show(TestPosts.post(media = List(3) { TestPosts.IMAGE }))

        composeRule.onNodeWithContentDescription("3 photos").assertIsDisplayed()
    }

    @Test
    fun a_single_photo_shows_no_count() {
        show(TestPosts.post(media = listOf(TestPosts.IMAGE)))

        composeRule.onNodeWithContentDescription("1 photo").assertDoesNotExist()
    }

    @Test
    fun with_several_pets_it_names_one_and_says_how_many_more() {
        show(TestPosts.post().copy(pets = listOf(luna, toby)))

        composeRule.onNodeWithText("Luna +1").assertIsDisplayed()
    }

    @Test
    fun without_text_it_still_says_who_published_it() {
        show(TestPosts.post(text = null))

        composeRule.onNodeWithText("Bea").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp", fontScale = 2f)
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun with_a_very_large_font_nothing_is_lost() {
        show(TestPosts.post(likeCount = 12, commentCount = 3).copy(pets = listOf(luna)))

        composeRule.onNodeWithText("Bea").assertIsDisplayed()
        composeRule.onNodeWithText("En el Retiro").assertIsDisplayed()
        composeRule.onNodeWithText("Luna").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("12 likes").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("3 comments").assertIsDisplayed()
    }

    @Test
    fun while_they_are_on_their_way_it_says_it_is_loading() {
        showPlaceholder(LoadState.Loading)

        composeRule.onNodeWithContentDescription("Loading", substring = true).assertIsDisplayed()
    }

    @Test
    fun if_they_could_not_be_loaded_it_can_try_again() {
        var retries = 0
        showPlaceholder(LoadState.Error(IllegalStateException()), onRetry = { retries++ })

        composeRule.onNodeWithText("The saved posts could not be loaded.").assertIsDisplayed()
        composeRule.onNodeWithText("Try again").performClick()

        assertEquals(1, retries)
    }

    @Test
    fun without_any_it_says_what_will_appear_here() {
        showPlaceholder(LoadState.NotLoading(endOfPaginationReached = true))

        composeRule.onNodeWithText("Nothing saved yet").assertIsDisplayed()
    }
}
