package com.geowoof.feature.notifications

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
import com.geowoof.core.data.repository.ContentChanges
import com.geowoof.core.data.repository.RemoteLoadException
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.core.model.AppError
import com.geowoof.core.model.AppNotification
import com.geowoof.core.model.NotificationType
import com.geowoof.core.testing.FakeNotificationsRepository
import com.geowoof.core.testing.MainDispatcherRule
import com.geowoof.core.testing.TestChat
import com.geowoof.core.testing.TestSocial
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

class NotificationsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun opening_the_list_is_having_seen_it() {
        val notifications = FakeNotificationsRepository()
        notifications.unread.value = 3
        val viewModel = NotificationsViewModel(notifications, ContentChanges())

        viewModel.onOpened()

        assertEquals(1, notifications.markedAllRead)
        assertEquals(0, notifications.unread.value)
    }
}

@RunWith(AndroidJUnit4::class)
class NotificationsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val opened = mutableListOf<String>()
    private val actions = NotificationsActions(
        onBack = { opened += "back" },
        onOpenPost = { opened += "post:$it" },
        onOpenUser = { opened += "user:$it" },
        onOpenRequests = { opened += "requests" },
    )

    private fun show(items: PagingData<AppNotification>) {
        composeRule.setContent {
            GeoWoofTheme(dynamicColor = false) {
                NotificationsContent(items = flowOf(items).collectAsLazyPagingItems(), actions = actions)
            }
        }
    }

    private fun waitForText(text: String, substring: Boolean = false) = composeRule.waitUntil(WAIT_MILLIS) {
        composeRule.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty()
    }

    private fun tap(notification: AppNotification, text: String) {
        show(TestSocial.pages(listOf(notification)))
        waitForText(text)
        composeRule.onNodeWithText(text).performClick()
    }

    @Test
    fun what_happened_to_a_post_leads_to_the_post() {
        tap(TestChat.notification(type = NotificationType.PostLiked), "Bea liked your post")

        assertEquals(listOf("post:post-1"), opened)
    }

    @Test
    fun a_mention_without_a_post_leads_to_who_did_it() {
        tap(TestChat.notification(type = NotificationType.UserMentioned, postId = null), "Bea mentioned you")

        assertEquals(listOf("user:${TestChat.BEA}"), opened)
    }

    @Test
    fun a_request_leads_to_the_requests() {
        tap(TestChat.notification(type = NotificationType.FollowRequested, postId = null), "Bea wants to follow you")

        assertEquals(listOf("requests"), opened)
    }

    @Test
    fun a_new_follower_leads_to_their_profile() {
        tap(TestChat.notification(type = NotificationType.UserFollowed, postId = null), "Bea started following you")

        assertEquals(listOf("user:${TestChat.BEA}"), opened)
    }

    @Test
    fun a_new_friend_leads_to_their_profile() {
        tap(
            TestChat.notification(type = NotificationType.FriendshipStarted, postId = null),
            "You and Bea are now friends",
        )

        assertEquals(listOf("user:${TestChat.BEA}"), opened)
    }

    @Test
    fun an_accepted_request_leads_to_their_profile() {
        tap(
            TestChat.notification(type = NotificationType.FollowRequestAccepted, postId = null),
            "Bea accepted your request",
        )

        assertEquals(listOf("user:${TestChat.BEA}"), opened)
    }

    @Test
    fun what_was_new_when_opening_is_marked() {
        show(TestSocial.pages(listOf(TestChat.notification(isRead = false))))
        waitForText("Bea liked your post")

        composeRule.onNodeWithContentDescription("New", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun what_was_already_read_is_not() {
        show(TestSocial.pages(listOf(TestChat.notification(isRead = true))))
        waitForText("Bea liked your post")

        composeRule.onNodeWithContentDescription("New", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun with_nothing_to_show_it_says_what_will_be_here() {
        show(TestSocial.pages(emptyList()))

        waitForText("Nothing yet", substring = true)
    }

    @Test
    fun a_list_that_fails_can_be_tried_again() {
        val failed = LoadState.Error(RemoteLoadException(AppError.Network))
        val idle = LoadState.NotLoading(endOfPaginationReached = false)
        show(PagingData.empty(LoadStates(refresh = failed, prepend = idle, append = idle)))

        waitForText("Try again")
    }
}

private const val WAIT_MILLIS = 10_000L
