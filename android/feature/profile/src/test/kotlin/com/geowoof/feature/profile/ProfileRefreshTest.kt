package com.geowoof.feature.profile

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.core.model.AppError
import com.geowoof.core.model.Relationship
import com.geowoof.core.model.RelationshipStatus
import com.geowoof.core.model.Walk
import com.geowoof.core.testing.DrainMainLooperRule
import com.geowoof.core.testing.TestPets
import com.geowoof.core.testing.TestPosts
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Each time a list is reloaded, its pager asks for a new source: that is what these tests count. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w412dp-h915dp")
class ProfileRefreshTest {
    @get:Rule(order = -2)
    val drainMainLooper = DrainMainLooperRule()

    @get:Rule
    val composeRule = createComposeRule()

    private val calls = mutableListOf<String>()
    private var postSources = 0
    private var savedSources = 0

    private val posts = Pager(PagingConfig(pageSize = PAGE)) {
        postSources++
        OnePage(List(PAGE) { TestPosts.item(TestPosts.post("post-$it")) })
    }.flow
    private val saved = Pager(PagingConfig(pageSize = PAGE)) {
        savedSources++
        OnePage(listOf(TestPosts.post("saved")))
    }.flow
    private val walks = Pager(PagingConfig(pageSize = PAGE)) { OnePage(emptyList<Walk>()) }.flow

    private val ana = ProfileUiState(
        isLoading = false,
        profile = TestPets.profile(displayName = "Ana", username = "ana"),
        relationship = Relationship(RelationshipStatus.Self),
    )

    private fun show(uiState: ProfileUiState) {
        composeRule.setContent {
            GeoWoofTheme(dynamicColor = false) {
                ProfileContent(
                    uiState = uiState,
                    lists = ProfileLists(
                        posts = posts.collectAsLazyPagingItems(),
                        walks = walks.collectAsLazyPagingItems(),
                        saved = saved.collectAsLazyPagingItems(),
                    ),
                    reportDetails = TextFieldState(),
                    actions = ProfileActions(onRefresh = { calls += "refresh" }),
                )
            }
        }
        // The pages come by a coroutine that the clock of the test does not wait for.
        composeRule.waitUntil(WAIT_MILLIS) { postSources == 1 && savedSources == 1 }
    }

    private fun assertEverythingWasReloaded() {
        composeRule.waitUntil(WAIT_MILLIS) { postSources == 2 && savedSources == 2 }
        assertEquals(listOf("refresh"), calls)
    }

    /** Before, it only reloaded the profile: new photos did not appear until coming back to it. */
    @Test
    fun pulling_the_page_down_also_reloads_the_photos_and_the_saved_posts() {
        show(ana)

        composeRule.onAllNodes(hasScrollToNodeAction()).onFirst().performTouchInput { swipeDown() }

        assertEverythingWasReloaded()
    }

    @Test
    fun retrying_after_a_failure_also_reloads_the_photos_and_the_saved_posts() {
        show(ProfileUiState(isLoading = false, loadError = AppError.Network))

        composeRule.onNodeWithText("Try again").performClick()

        assertEverythingWasReloaded()
    }

    private class OnePage<T : Any>(private val items: List<T>) : PagingSource<Int, T>() {
        override fun getRefreshKey(state: PagingState<Int, T>): Int? = null

        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, T> =
            LoadResult.Page(items, prevKey = null, nextKey = null)
    }

    private companion object {
        const val PAGE = 12
        const val WAIT_MILLIS = 5_000L
    }
}
