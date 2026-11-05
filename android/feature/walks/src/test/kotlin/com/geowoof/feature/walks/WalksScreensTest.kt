package com.geowoof.feature.walks

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.geowoof.core.data.repository.WalkLoadException
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.core.model.AppError
import com.geowoof.core.model.Walk
import com.geowoof.core.model.WalkSyncStatus
import com.geowoof.core.testing.TestWalks
import com.geowoof.feature.walks.detail.WalkDetailActions
import com.geowoof.feature.walks.detail.WalkDetailContent
import com.geowoof.feature.walks.detail.WalkDetailUiState
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WalksScreensTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val actions = mutableListOf<String>()

    private fun showList(uiState: WalksUiState, page: PagingData<Walk>) {
        composeRule.setContent {
            GeoWoofTheme(dynamicColor = false) {
                WalksContent(
                    uiState = uiState,
                    walks = flowOf(page).collectAsLazyPagingItems(),
                    onBack = { actions += "back" },
                    onOpenWalk = { actions += "open:$it" },
                    onRefresh = { actions += "refresh" },
                )
            }
        }
    }

    /** The pages reach the list through their own coroutine, which the idle checks of the test do not see. */
    private fun waitForText(text: String) {
        composeRule.waitUntil(WAIT_MILLIS) { composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun refreshFailing(error: AppError) = PagingData.empty<Walk>(
        LoadStates(
            refresh = LoadState.Error(WalkLoadException(error)),
            prepend = LoadState.NotLoading(false),
            append = LoadState.NotLoading(false),
        ),
    )

    @Test
    fun the_list_shows_the_totals_and_opens_a_walk() {
        val walk = TestWalks.walk(id = "walk-1", petIds = listOf("luna"))
        showList(
            WalksUiState(
                totals = TestWalks.totals(walkCount = 42, weeklyStreak = 5),
                petNames = mapOf("luna" to "Luna"),
            ),
            PagingData.from(listOf(walk)),
        )

        waitForText("Luna")
        composeRule.onNodeWithText("42").assertIsDisplayed()
        composeRule.onNodeWithText("Luna").assertIsDisplayed()
        composeRule.onNodeWithText("3.12 km · 42:10").performClick()

        assertEquals(listOf("open:walk-1"), actions)
    }

    @Test
    fun the_list_of_a_pet_says_whose_it_is() {
        showList(WalksUiState(petName = "Luna"), PagingData.from(listOf(TestWalks.walk())))

        waitForText("Walks of Luna")
        composeRule.onNodeWithText("Walks of Luna").assertIsDisplayed()
    }

    @Test
    fun a_walk_waiting_to_be_uploaded_says_so() {
        showList(WalksUiState(), PagingData.from(listOf(TestWalks.walk(syncStatus = WalkSyncStatus.PendingSync))))

        composeRule.waitUntil(WAIT_MILLIS) {
            composeRule.onAllNodesWithContentDescription("Waiting to be uploaded").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("Waiting to be uploaded").assertIsDisplayed()
    }

    @Test
    fun without_walks_it_explains_how_to_have_one() {
        showList(WalksUiState(), PagingData.empty(FINISHED))

        waitForText("No walks yet")
        composeRule.onNodeWithText("No walks yet").assertIsDisplayed()
    }

    @Test
    fun without_a_connection_and_without_walks_it_offers_to_try_again() {
        showList(WalksUiState(), refreshFailing(AppError.Network))

        waitForText("The walks could not be loaded")
        composeRule.onNodeWithText("The walks could not be loaded").assertIsDisplayed()
        composeRule.onNodeWithText("Try again").performClick()

        assertEquals(listOf("refresh"), actions)
    }

    private fun showDetail(uiState: WalkDetailUiState) {
        composeRule.setContent {
            GeoWoofTheme(dynamicColor = false) {
                WalkDetailContent(
                    uiState = uiState,
                    actions = WalkDetailActions(
                        onBack = { actions += "back" },
                        onShare = { actions += "share" },
                        onRetryUpload = { actions += "retry" },
                        onRequestDelete = { actions += "delete" },
                        onConfirmDelete = { actions += "confirmDelete" },
                        onDismissDelete = { actions += "dismissDelete" },
                        onErrorShown = { actions += "errorShown" },
                    ),
                    map = {},
                )
            }
        }
    }

    @Test
    fun the_detail_shows_the_walk_and_can_share_or_delete_it() {
        showDetail(WalkDetailUiState(walk = TestWalks.walk(), petNames = "Luna", route = emptyList()))

        composeRule.onNodeWithText("Por el Retiro").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Luna").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Who can see it: Friends").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Share").performClick()
        composeRule.onNodeWithContentDescription("Delete").performClick()

        assertEquals(listOf("share", "delete"), actions)
    }

    @Test
    fun a_walk_rejected_by_the_server_explains_why_and_can_be_retried() {
        val walk = TestWalks.walk(syncStatus = WalkSyncStatus.Failed, syncErrorCode = "walks.invalid_track")
        showDetail(WalkDetailUiState(walk = walk, route = emptyList()))

        composeRule.onNodeWithText("This walk could not be uploaded.").assertIsDisplayed()
        composeRule.onNodeWithText("The route of this walk is not valid.").assertIsDisplayed()
        composeRule.onNodeWithText("Try again").performClick()

        assertEquals(listOf("retry"), actions)
    }

    @Test
    fun a_pending_walk_says_it_will_be_uploaded() {
        showDetail(
            WalkDetailUiState(walk = TestWalks.walk(syncStatus = WalkSyncStatus.PendingSync), route = emptyList()),
        )

        composeRule.onNodeWithText("It will be uploaded as soon as there is a connection.").assertIsDisplayed()
    }

    @Test
    fun deleting_asks_first() {
        showDetail(WalkDetailUiState(walk = TestWalks.walk(), route = emptyList(), isConfirmingDelete = true))

        composeRule.onNodeWithText("Delete this walk?").assertIsDisplayed()
        composeRule.onNodeWithText("Keep").performClick()

        assertEquals(listOf("dismissDelete"), actions)
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L

        val FINISHED = LoadStates(
            refresh = LoadState.NotLoading(endOfPaginationReached = true),
            prepend = LoadState.NotLoading(endOfPaginationReached = true),
            append = LoadState.NotLoading(endOfPaginationReached = true),
        )
    }
}
