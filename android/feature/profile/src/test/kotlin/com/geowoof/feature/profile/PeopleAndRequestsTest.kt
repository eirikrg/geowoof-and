package com.geowoof.feature.profile

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.geowoof.core.data.repository.RemoteLoadException
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.core.model.AppError
import com.geowoof.core.model.PeopleListKind
import com.geowoof.core.model.Person
import com.geowoof.core.model.RequestDirection
import com.geowoof.core.testing.TestSocial
import com.geowoof.feature.profile.people.PeopleListContent
import com.geowoof.feature.profile.requests.FollowRequestsContent
import com.geowoof.feature.profile.requests.FollowRequestsUiState
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PeopleAndRequestsTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val calls = mutableListOf<String>()

    private fun showPeople(kind: PeopleListKind, pages: PagingData<Person>) {
        composeRule.setContent {
            GeoWoofTheme(dynamicColor = false) {
                PeopleListContent(
                    kind = kind,
                    people = flowOf(pages).collectAsLazyPagingItems(),
                    onBack = { calls += "back" },
                    onOpenUser = { calls += "open:$it" },
                )
            }
        }
    }

    private fun showRequests(uiState: FollowRequestsUiState) {
        composeRule.setContent {
            GeoWoofTheme(dynamicColor = false) {
                FollowRequestsContent(
                    uiState = uiState,
                    onBack = {},
                    onRefresh = {},
                    onOpenUser = { calls += "open:$it" },
                    onAccept = { calls += "accept:$it" },
                    onReject = { calls += "reject:$it" },
                    onCancel = { calls += "cancel:$it" },
                    onDismissError = {},
                )
            }
        }
    }

    @Test
    fun a_person_in_the_list_opens_their_profile() {
        showPeople(PeopleListKind.Followers, TestSocial.pages(listOf(TestSocial.person())))

        composeRule.onNodeWithText("Followers").assertIsDisplayed()
        composeRule.waitUntil(WAIT_MILLIS) {
            composeRule.onAllNodes(hasText("Bea")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Bea").performClick()

        assertEquals(listOf("open:bea-id"), calls)
    }

    @Test
    fun an_empty_list_explains_what_it_is_for() {
        showPeople(PeopleListKind.Friends, TestSocial.pages(emptyList()))

        composeRule.waitUntil(WAIT_MILLIS) {
            composeRule.onAllNodes(hasText("No friends yet")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(hasText("follow each other", substring = true)).assertIsDisplayed()
    }

    @Test
    fun a_list_that_is_not_allowed_says_why_and_can_be_retried() {
        val refused = RemoteLoadException(AppError.Api(status = 403, code = "social.lists_private"))
        val states = LoadStates(
            refresh = LoadState.Error(refused),
            prepend = LoadState.NotLoading(endOfPaginationReached = true),
            append = LoadState.NotLoading(endOfPaginationReached = true),
        )
        showPeople(PeopleListKind.Followers, PagingData.empty(states))

        composeRule.waitUntil(WAIT_MILLIS) {
            composeRule.onAllNodes(hasText("Try again")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(hasText("Only this person and their friends", substring = true)).assertIsDisplayed()
    }

    @Test
    fun a_received_request_is_answered_from_the_list_with_a_label_for_talkback() {
        showRequests(FollowRequestsUiState(received = listOf(TestSocial.request("1")), isRefreshing = false))

        composeRule.onNodeWithContentDescription("Accept the request from Bea").performClick()
        composeRule.onNodeWithContentDescription("Reject the request from Bea").performClick()

        assertEquals(listOf("accept:1", "reject:1"), calls)
    }

    @Test
    fun a_sent_request_is_cancelled_from_its_tab() {
        showRequests(
            FollowRequestsUiState(
                sent = listOf(TestSocial.request("2", direction = RequestDirection.Sent)),
                isRefreshing = false,
            ),
        )

        composeRule.onNodeWithText("Sent (1)").performClick()
        composeRule.onNodeWithText("Cancel").performClick()

        assertEquals(listOf("cancel:2"), calls)
    }

    @Test
    fun without_requests_each_tab_explains_itself() {
        showRequests(FollowRequestsUiState(isRefreshing = false))

        composeRule.onNode(hasText("asks to follow your private profile", substring = true)).assertIsDisplayed()
        composeRule.onNodeWithText("Sent (0)").performClick()
        composeRule.onNode(hasText("requests you send", substring = true)).assertIsDisplayed()
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L
    }
}
