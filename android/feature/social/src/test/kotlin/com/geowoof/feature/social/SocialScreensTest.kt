package com.geowoof.feature.social

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.core.model.AppError
import com.geowoof.core.model.Relationship
import com.geowoof.core.model.RelationshipStatus
import com.geowoof.core.model.ReportReason
import com.geowoof.core.model.RequestDirection
import com.geowoof.core.model.Walk
import com.geowoof.core.testing.FakePeopleRepository
import com.geowoof.core.testing.FakeSocialRepository
import com.geowoof.core.testing.TestPets
import com.geowoof.core.testing.TestSocial
import com.geowoof.core.testing.TestWalks
import com.geowoof.feature.social.friends.FriendRequestsContent
import com.geowoof.feature.social.friends.FriendRequestsUiState
import com.geowoof.feature.social.friends.FriendsContent
import com.geowoof.feature.social.friends.FriendsUiState
import com.geowoof.feature.social.navigation.UserProfileRoute
import com.geowoof.feature.social.profile.ProfileDialog
import com.geowoof.feature.social.profile.UserProfileActions
import com.geowoof.feature.social.profile.UserProfileContent
import com.geowoof.feature.social.profile.UserProfileUiState
import com.geowoof.feature.social.profile.UserProfileViewModel
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SocialScreensTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val actions = mutableListOf<String>()

    private val profileActions = UserProfileActions(
        onBack = { actions += "back" },
        onRefresh = { actions += "refresh" },
        onSendRequest = { actions += "send" },
        onAcceptRequest = { actions += "accept" },
        onRejectRequest = { actions += "reject" },
        onCancelRequest = { actions += "cancel" },
        onShowDialog = { actions += "dialog:$it" },
        onDismissDialog = { actions += "dismiss" },
        onConfirmRemoveFriend = { actions += "confirmRemove" },
        onConfirmBlock = { actions += "confirmBlock" },
        onSelectReportReason = { actions += "reason:$it" },
        onConfirmReport = { actions += "confirmReport" },
        onVerifyEmail = { actions += "verify" },
        onOpenWalk = { actions += "walk:$it" },
        onMessageShown = { actions += "shown" },
    )

    private fun showProfile(uiState: UserProfileUiState, walks: List<Walk> = emptyList()) {
        composeRule.setContent {
            GeoWoofTheme(dynamicColor = false) {
                UserProfileContent(
                    uiState = uiState,
                    walks = flowOf(TestSocial.pages(walks)).collectAsLazyPagingItems(),
                    reportDetails = TextFieldState(),
                    actions = profileActions,
                )
            }
        }
    }

    private fun profileWith(status: RelationshipStatus, dialog: ProfileDialog? = null) = UserProfileUiState(
        isLoading = false,
        profile = TestPets.profile(displayName = "Bea", username = "bea", city = "Madrid", bio = "Paseo a diario"),
        relationship = Relationship(status),
        dialog = dialog,
    )

    @Test
    fun the_profile_shows_who_it_is_and_what_they_have() {
        composeRule.setContent {
            GeoWoofTheme(dynamicColor = false) {
                UserProfileContent(
                    uiState = profileWith(RelationshipStatus.None).copy(pets = listOf(TestPets.pet())),
                    walks = flowOf(TestSocial.pages(listOf(TestWalks.walk(petIds = listOf("luna")))))
                        .collectAsLazyPagingItems(),
                    reportDetails = TextFieldState(),
                    actions = profileActions,
                )
            }
        }

        composeRule.onNodeWithText("@bea").assertIsDisplayed()
        composeRule.onNodeWithText("Bea").assertIsDisplayed()
        composeRule.onNodeWithText("Madrid").assertIsDisplayed()
        composeRule.onNodeWithText("Paseo a diario").assertIsDisplayed()
        composeRule.onNodeWithText("Luna").assertIsDisplayed()
    }

    @Test
    fun a_stranger_can_be_asked_to_be_a_friend() {
        showProfile(profileWith(RelationshipStatus.None))

        composeRule.onNodeWithText("Add friend").performClick()

        assertEquals(listOf("send"), actions)
    }

    @Test
    fun a_sent_request_can_be_cancelled() {
        showProfile(profileWith(RelationshipStatus.RequestSent))

        composeRule.onNodeWithText("Cancel request").performClick()

        assertEquals(listOf("cancel"), actions)
    }

    @Test
    fun a_received_request_can_be_accepted_or_rejected() {
        showProfile(profileWith(RelationshipStatus.RequestReceived))

        composeRule.onNodeWithText("Accept").performClick()
        composeRule.onNodeWithText("Reject").performClick()

        assertEquals(listOf("accept", "reject"), actions)
    }

    @Test
    fun a_friend_can_be_removed_after_a_confirmation() {
        showProfile(profileWith(RelationshipStatus.Friends))

        composeRule.onNodeWithText("Friends").performClick()

        assertEquals(listOf("dialog:ConfirmRemoveFriend"), actions)
    }

    @Test
    fun the_buttons_wait_for_the_server() {
        showProfile(profileWith(RelationshipStatus.None).copy(isActing = true))

        composeRule.onNodeWithText("Add friend").assertIsNotEnabled()
    }

    @Test
    fun the_menu_offers_to_report_and_block() {
        showProfile(profileWith(RelationshipStatus.None))

        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.onNodeWithText("Report").performClick()
        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.onNodeWithText("Block").performClick()

        assertEquals(listOf("dialog:Report", "dialog:ConfirmBlock"), actions)
    }

    @Test
    fun your_own_profile_has_no_actions() {
        showProfile(profileWith(RelationshipStatus.Self))

        composeRule.onNodeWithText("Add friend").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("More options").assertDoesNotExist()
    }

    @Test
    fun blocking_says_what_will_happen_and_asks_first() {
        showProfile(profileWith(RelationshipStatus.Friends, ProfileDialog.ConfirmBlock))

        composeRule.onNodeWithText("Block Bea?").assertIsDisplayed()
        composeRule.onNode(hasText("You will stop being friends", substring = true)).assertIsDisplayed()
        composeRule.onNodeWithText("Block").performClick()

        assertEquals(listOf("confirmBlock"), actions)
    }

    @Test
    fun the_report_needs_a_reason() {
        showProfile(profileWith(RelationshipStatus.None, ProfileDialog.Report))

        composeRule.onNodeWithText("Send").assertIsNotEnabled()
        composeRule.onNodeWithText("Harassment").performClick()

        assertEquals(listOf("reason:${ReportReason.Harassment}"), actions)
    }

    @Test
    fun with_a_reason_the_report_can_be_sent() {
        showProfile(profileWith(RelationshipStatus.None, ProfileDialog.Report).copy(reportReason = ReportReason.Spam))

        composeRule.onNodeWithText("Send").assertIsEnabled().performClick()

        assertEquals(listOf("confirmReport"), actions)
    }

    @Test
    fun an_unverified_email_is_sent_to_verify() {
        showProfile(profileWith(RelationshipStatus.None, ProfileDialog.VerifyEmail))

        composeRule.onNodeWithText("Verify").performClick()

        assertEquals(listOf("verify"), actions)
    }

    @Test
    fun someone_not_available_does_not_say_why() {
        showProfile(UserProfileUiState(isLoading = false, isUnavailable = true))

        composeRule.onNodeWithText("Profile not available").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("More options").assertDoesNotExist()
    }

    @Test
    fun a_failure_can_be_retried() {
        showProfile(UserProfileUiState(isLoading = false, loadError = AppError.Network))

        composeRule.onNodeWithText("Try again").performClick()

        assertEquals(listOf("refresh"), actions)
    }

    @Test
    fun a_walk_of_the_person_opens() {
        showProfile(profileWith(RelationshipStatus.Friends), walks = listOf(TestWalks.walk(id = "walk-9")))

        // The pages arrive through a coroutine, and the list only has what fits on the screen.
        composeRule.waitUntil(WAIT_MILLIS) {
            runCatching {
                composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("3.12 km", substring = true))
            }.isSuccess
        }
        composeRule.onNode(hasText("3.12 km", substring = true)).performClick()

        assertEquals(listOf("walk:walk-9"), actions)
    }

    @Test
    fun no_walks_to_see_says_so() {
        showProfile(profileWith(RelationshipStatus.None))

        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("No walks you can see."))
        composeRule.onNodeWithText("No walks you can see.").assertIsDisplayed()
    }

    @Test
    fun the_community_shows_the_pending_requests_and_opens_the_lists() {
        composeRule.setContent {
            GeoWoofTheme(dynamicColor = false) {
                CommunityContent(
                    uiState = CommunityUiState(friendCount = 3, requestCount = 2),
                    onOpenRequests = { actions += "requests" },
                    onOpenFriends = { actions += "friends" },
                )
            }
        }

        composeRule.onNodeWithText("2").assertIsDisplayed()
        composeRule.onNodeWithText("3 friends").assertIsDisplayed()
        composeRule.onNodeWithText("Friend requests").performClick()
        composeRule.onNodeWithText("Friends").performClick()

        assertEquals(listOf("requests", "friends"), actions)
    }

    @Test
    fun without_friends_it_says_how_to_find_them() {
        composeRule.setContent {
            GeoWoofTheme(dynamicColor = false) {
                FriendsContent(
                    uiState = FriendsUiState(isRefreshing = false),
                    onBack = {},
                    onRefresh = {},
                    onOpenUser = {},
                    onDismissError = {},
                )
            }
        }

        composeRule.onNodeWithText("No friends yet").assertIsDisplayed()
    }

    @Test
    fun a_friend_opens_their_profile() {
        composeRule.setContent {
            GeoWoofTheme(dynamicColor = false) {
                FriendsContent(
                    uiState = FriendsUiState(friends = listOf(TestSocial.friend()), isRefreshing = false),
                    onBack = {},
                    onRefresh = {},
                    onOpenUser = { actions += it },
                    onDismissError = {},
                )
            }
        }

        composeRule.onNodeWithText("Bea").performClick()

        assertEquals(listOf("bea-id"), actions)
    }

    private fun showRequests(uiState: FriendRequestsUiState) {
        composeRule.setContent {
            GeoWoofTheme(dynamicColor = false) {
                FriendRequestsContent(
                    uiState = uiState,
                    onBack = {},
                    onRefresh = {},
                    onOpenUser = { actions += "open:$it" },
                    onAccept = { actions += "accept:$it" },
                    onReject = { actions += "reject:$it" },
                    onCancel = { actions += "cancel:$it" },
                    onDismissError = {},
                )
            }
        }
    }

    @Test
    fun a_received_request_is_answered_from_the_list_with_a_label_for_talkback() {
        showRequests(FriendRequestsUiState(received = listOf(TestSocial.request("1")), isRefreshing = false))

        composeRule.onNodeWithContentDescription("Accept the request from Bea").performClick()
        composeRule.onNodeWithContentDescription("Reject the request from Bea").performClick()

        assertEquals(listOf("accept:1", "reject:1"), actions)
    }

    @Test
    fun a_sent_request_is_cancelled_from_its_tab() {
        showRequests(
            FriendRequestsUiState(
                sent = listOf(TestSocial.request("2", direction = RequestDirection.Sent)),
                isRefreshing = false,
            ),
        )

        composeRule.onNodeWithText("Sent (1)").performClick()
        composeRule.onNodeWithText("Cancel").performClick()

        assertEquals(listOf("cancel:2"), actions)
    }

    @Test
    fun without_requests_each_tab_explains_itself() {
        showRequests(FriendRequestsUiState(isRefreshing = false))

        composeRule.onNode(hasText("wants to be your friend", substring = true)).assertIsDisplayed()
        composeRule.onNodeWithText("Sent (0)").performClick()
        composeRule.onNode(hasText("requests you send", substring = true)).assertIsDisplayed()
    }

    @Serializable
    data object StartRoute

    /** The ViewModel reads the route argument by property name: this checks it with real navigation. */
    @Test
    fun the_profile_receives_the_person_from_its_route() {
        val people = FakePeopleRepository()
        val social = FakeSocialRepository()
        composeRule.setContent {
            val navController = rememberNavController()
            NavHost(navController = navController, startDestination = StartRoute) {
                composable<StartRoute> {
                    LaunchedEffect(Unit) { navController.navigate(UserProfileRoute("bea-id")) }
                }
                composable<UserProfileRoute> { entry ->
                    viewModel { UserProfileViewModel(createSavedStateHandle(), people, social) }
                    Text(entry.toRoute<UserProfileRoute>().userId)
                }
            }
        }

        composeRule.waitUntil(WAIT_MILLIS) {
            composeRule.onAllNodes(hasText("bea-id")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L
    }
}
