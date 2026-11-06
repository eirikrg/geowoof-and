package com.geowoof.feature.profile

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onLast
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
import com.geowoof.core.model.PeopleListKind
import com.geowoof.core.model.Post
import com.geowoof.core.model.ProfileCounts
import com.geowoof.core.model.Relationship
import com.geowoof.core.model.RelationshipStatus
import com.geowoof.core.model.ReportReason
import com.geowoof.core.model.TimelineItem
import com.geowoof.core.model.Walk
import com.geowoof.core.testing.FakePeopleRepository
import com.geowoof.core.testing.FakePostsRepository
import com.geowoof.core.testing.FakeSocialRepository
import com.geowoof.core.testing.TestPets
import com.geowoof.core.testing.TestPosts
import com.geowoof.core.testing.TestSocial
import com.geowoof.core.testing.TestWalks
import com.geowoof.feature.profile.navigation.PeopleListRoute
import com.geowoof.feature.profile.navigation.UserProfileRoute
import com.geowoof.feature.profile.people.PeopleListViewModel
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w412dp-h915dp")
class ProfileScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val calls = mutableListOf<String>()

    private val ownActions = ProfileActions(
        onRefresh = { calls += "refresh" },
        onOpenSettings = { calls += "settings" },
        onEditProfile = { calls += "edit" },
        onOpenPeople = { calls += "people:$it" },
        onOpenRequests = { calls += "requests" },
        onOpenWalk = { calls += "walk:$it" },
        onOpenPet = { calls += "pet:$it" },
        onAddPet = { calls += "add" },
        onOpenPost = { calls += "post:$it" },
        onCreatePost = { calls += "create" },
    )

    private val userActions = ProfileActions(
        onBack = { calls += "back" },
        onRefresh = { calls += "refresh" },
        onOpenPeople = { calls += "people:$it" },
        onOpenWalk = { calls += "walk:$it" },
        onOpenPost = { calls += "post:$it" },
        onFollow = { calls += "follow" },
        onUnfollow = { calls += "unfollow" },
        onAcceptRequest = { calls += "accept" },
        onRejectRequest = { calls += "reject" },
        onShowDialog = { calls += "dialog:$it" },
        onDismissDialog = { calls += "dismiss" },
        onConfirmFollowBack = { calls += "confirmFollowBack" },
        onConfirmUnfollow = { calls += "confirmUnfollow" },
        onConfirmRemoveFollower = { calls += "confirmRemoveFollower" },
        onConfirmBlock = { calls += "confirmBlock" },
        onSelectReportReason = { calls += "reason:$it" },
        onConfirmReport = { calls += "confirmReport" },
        onVerifyEmail = { calls += "verify" },
        onMessageShown = { calls += "shown" },
    )

    private fun show(
        uiState: ProfileUiState,
        actions: ProfileActions = userActions,
        walks: List<Walk> = emptyList(),
        posts: List<TimelineItem> = emptyList(),
        saved: List<Post>? = null,
    ) {
        composeRule.setContent {
            GeoWoofTheme(dynamicColor = false) {
                ProfileContent(
                    uiState = uiState,
                    lists = ProfileLists(
                        posts = flowOf(TestSocial.pages(posts)).collectAsLazyPagingItems(),
                        walks = flowOf(TestSocial.pages(walks)).collectAsLazyPagingItems(),
                        saved = saved?.let { flowOf(TestSocial.pages(it)).collectAsLazyPagingItems() },
                    ),
                    reportDetails = TextFieldState(),
                    actions = actions,
                )
            }
        }
    }

    private fun bea(
        status: RelationshipStatus,
        followsYou: Boolean = false,
        incomingRequestId: String? = null,
        dialog: ProfileDialog? = null,
    ) = ProfileUiState(
        isLoading = false,
        profile = TestPets.profile(displayName = "Bea", username = "bea", city = "Madrid", bio = "Paseo a diario"),
        relationship = Relationship(status, followsYou, incomingRequestId = incomingRequestId),
        counts = ProfileCounts(followers = 12, following = 8, friends = 5),
        dialog = dialog,
    )

    /** How a private profile arrives to someone who does not follow it: its name is its username. */
    private fun restricted(status: RelationshipStatus) = ProfileUiState(
        isLoading = false,
        profile = TestPets.profile(displayName = "dani", username = "dani", bio = "Con Bimba", isPrivate = true),
        relationship = Relationship(status),
        counts = ProfileCounts(followers = 3, following = 1, friends = 0),
    )

    private fun own() = ProfileUiState(
        isLoading = false,
        profile = TestPets.profile(displayName = "Ana López", bio = "Paseo a diario", city = "Madrid"),
        relationship = Relationship(RelationshipStatus.Self),
        counts = ProfileCounts(followers = 2, following = 3, friends = 1),
        pets = listOf(TestPets.pet(), TestPets.pet(id = "toby", name = "Toby", breed = null)),
    )

    private fun openPetsTab() = composeRule.onNodeWithText("Pets").performClick()

    private fun openWalksTab() = composeRule.onNodeWithText("Walks").performClick()

    /** The screen of these tests is small, and what is below the header has to come into view. */
    private fun scrollTo(text: String) {
        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text))
    }

    /** The pages of a list arrive through a coroutine that the clock of the test does not wait for. */
    private fun waitFor(text: String) {
        composeRule.waitUntil(WAIT_MILLIS) {
            composeRule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun your_profile_shows_who_you_are_and_your_numbers() {
        show(own(), ownActions)

        composeRule.onNodeWithText("Ana López").assertIsDisplayed()
        composeRule.onNodeWithText("@ana").assertIsDisplayed()
        composeRule.onNodeWithText("Madrid").assertIsDisplayed()
        composeRule.onNodeWithText("Paseo a diario").assertIsDisplayed()
        composeRule.onNodeWithText("2").assertIsDisplayed()
        composeRule.onNodeWithText("Followers").assertIsDisplayed()
    }

    @Test
    fun your_pets_are_in_their_tab_and_they_open() {
        show(own(), ownActions)

        openPetsTab()

        scrollTo("Border collie")
        composeRule.onNodeWithText("Border collie").assertIsDisplayed()
        // Without a breed, the species describes it.
        scrollTo("Dog")
        composeRule.onNodeWithText("Dog").assertIsDisplayed()
        composeRule.onNodeWithText("Toby").performClick()
        assertEquals(listOf("pet:toby"), calls)
    }

    @Test
    fun it_invites_to_add_the_first_pet() {
        show(own().copy(pets = emptyList()), ownActions)

        openPetsTab()

        scrollTo("No pets yet")
        composeRule.onNodeWithText("No pets yet").assertIsDisplayed()
        scrollTo("Add pet")
        composeRule.onNodeWithText("Add pet").performClick()
        assertEquals(listOf("add"), calls)
    }

    @Test
    fun it_opens_the_settings_and_the_editor() {
        show(own(), ownActions)

        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.onNodeWithText("Edit profile").performClick()

        assertEquals(listOf("settings", "edit"), calls)
    }

    @Test
    fun your_lists_open_from_your_numbers() {
        show(own(), ownActions)

        composeRule.onNodeWithText("Followers").performClick()
        composeRule.onNodeWithText("Following").performClick()
        composeRule.onNodeWithText("Friends").performClick()

        assertEquals(
            listOf(
                "people:${PeopleListKind.Followers}",
                "people:${PeopleListKind.Following}",
                "people:${PeopleListKind.Friends}",
            ),
            calls,
        )
    }

    @Test
    fun the_lists_of_someone_who_is_not_your_friend_do_not_open() {
        show(bea(RelationshipStatus.Following))

        composeRule.onNodeWithText("Followers").performClick()

        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun the_lists_of_a_friend_open() {
        show(bea(RelationshipStatus.Friends, followsYou = true))

        composeRule.onNodeWithText("Followers").performClick()

        assertEquals(listOf("people:${PeopleListKind.Followers}"), calls)
    }

    @Test
    fun the_requests_appear_when_there_are_any_with_the_people_waiting() {
        show(own().copy(receivedRequests = 7), ownActions)

        composeRule.onNodeWithText("7").assertIsDisplayed()
        composeRule.onNodeWithText("Follow requests").performClick()

        assertEquals(listOf("requests"), calls)
    }

    @Test
    fun without_requests_the_row_is_not_there() {
        show(own(), ownActions)

        composeRule.onNodeWithText("Follow requests").assertDoesNotExist()
    }

    @Test
    fun a_stranger_can_be_followed() {
        show(bea(RelationshipStatus.None))

        composeRule.onNodeWithText("Follow").performClick()

        assertEquals(listOf("follow"), calls)
        composeRule.onNodeWithText("Follows you").assertDoesNotExist()
    }

    @Test
    fun following_back_says_that_they_follow_you_and_asks_first() {
        show(bea(RelationshipStatus.None, followsYou = true))

        composeRule.onNodeWithText("Follows you").assertIsDisplayed()
        composeRule.onNodeWithText("Follow back").performClick()

        assertEquals(listOf("dialog:${ProfileDialog.ConfirmFollowBack}"), calls)
    }

    @Test
    fun the_warning_says_what_a_friend_can_see() {
        show(bea(RelationshipStatus.None, followsYou = true, dialog = ProfileDialog.ConfirmFollowBack))

        composeRule.onNodeWithText("Follow Bea back?").assertIsDisplayed()
        composeRule.onNode(hasText("they will see what you share only with friends", substring = true))
            .assertIsDisplayed()
    }

    @Test
    fun confirming_the_warning_follows_back() {
        show(bea(RelationshipStatus.None, followsYou = true, dialog = ProfileDialog.ConfirmFollowBack))

        composeRule.onAllNodes(hasText("Follow back")).onLast().performClick()

        assertEquals(listOf("confirmFollowBack"), calls)
    }

    @Test
    fun a_request_can_be_cancelled_from_its_button() {
        show(restricted(RelationshipStatus.Requested))

        composeRule.onNodeWithText("Requested").performClick()

        assertEquals(listOf("unfollow"), calls)
    }

    @Test
    fun whoever_you_follow_can_be_unfollowed_after_a_confirmation() {
        show(bea(RelationshipStatus.Following))

        composeRule.onAllNodes(hasText("Following")).onLast().performClick()

        assertEquals(listOf("dialog:${ProfileDialog.ConfirmUnfollow}"), calls)
    }

    @Test
    fun a_friend_says_so_on_the_button_and_not_that_they_follow_you() {
        show(bea(RelationshipStatus.Friends, followsYou = true))

        composeRule.onAllNodes(hasText("Friends")).onLast().performClick()

        composeRule.onNodeWithText("Follows you").assertDoesNotExist()
        assertEquals(listOf("dialog:${ProfileDialog.ConfirmUnfollow}"), calls)
    }

    @Test
    fun unfollowing_a_friend_warns_about_the_friendship() {
        show(bea(RelationshipStatus.Friends, followsYou = true, dialog = ProfileDialog.ConfirmUnfollow))

        composeRule.onNode(hasText("You will stop being friends", substring = true)).assertIsDisplayed()
        composeRule.onNodeWithText("Unfollow").performClick()

        assertEquals(listOf("confirmUnfollow"), calls)
    }

    @Test
    fun the_buttons_wait_for_the_server() {
        show(bea(RelationshipStatus.None).copy(isActing = true))

        composeRule.onNodeWithText("Follow").assertIsNotEnabled()
    }

    @Test
    fun an_incoming_request_can_be_answered_from_the_profile() {
        show(bea(RelationshipStatus.None, incomingRequestId = "request-1"))

        scrollTo("Bea wants to follow you")
        composeRule.onNodeWithText("Bea wants to follow you").assertIsDisplayed()
        scrollTo("Accept")
        composeRule.onNodeWithText("Accept").performClick()
        composeRule.onNodeWithText("Reject").performClick()

        assertEquals(listOf("accept", "reject"), calls)
    }

    @Test
    fun the_menu_offers_to_report_and_block() {
        show(bea(RelationshipStatus.None))

        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.onNodeWithText("Remove from followers").assertDoesNotExist()
        composeRule.onNodeWithText("Report").performClick()
        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.onNodeWithText("Block").performClick()

        assertEquals(listOf("dialog:${ProfileDialog.Report}", "dialog:${ProfileDialog.ConfirmBlock}"), calls)
    }

    @Test
    fun the_menu_offers_to_remove_who_follows_you() {
        show(bea(RelationshipStatus.None, followsYou = true))

        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.onNodeWithText("Remove from followers").performClick()

        assertEquals(listOf("dialog:${ProfileDialog.ConfirmRemoveFollower}"), calls)
    }

    @Test
    fun reaching_yourself_from_a_list_offers_to_edit_and_no_menu() {
        var edited = false
        show(
            bea(RelationshipStatus.Self),
            ProfileActions(onBack = {}, onEditProfile = { edited = true }),
        )

        composeRule.onNodeWithContentDescription("More options").assertDoesNotExist()
        composeRule.onNodeWithText("Follow").assertDoesNotExist()
        composeRule.onNodeWithText("Edit profile").performClick()
        assertEquals(true, edited)
    }

    @Test
    fun a_private_profile_only_shows_what_is_allowed() {
        show(restricted(RelationshipStatus.None))

        composeRule.onNodeWithText("@dani").assertIsDisplayed()
        composeRule.onNodeWithText("Con Bimba").assertIsDisplayed()
        composeRule.onNodeWithText("Private profile").assertIsDisplayed()
        composeRule.onNodeWithText("3").assertIsDisplayed()
        composeRule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("This profile is private"))
        composeRule.onNodeWithText("This profile is private").assertIsDisplayed()
        composeRule.onNodeWithText("dani").assertDoesNotExist()
        composeRule.onNodeWithText("Photos").assertDoesNotExist()
        composeRule.onNodeWithText("Walks").assertDoesNotExist()
        composeRule.onNodeWithText("Pets").assertDoesNotExist()
    }

    @Test
    fun a_private_profile_that_you_follow_shows_its_content() {
        val state = restricted(RelationshipStatus.Following).copy(
            profile = TestPets.profile(displayName = "Dani Prieto", username = "dani", isPrivate = true),
        )
        show(state)

        composeRule.onNodeWithText("Dani Prieto").assertIsDisplayed()
        composeRule.onNodeWithText("Private profile").assertIsDisplayed()
        composeRule.onNodeWithText("Walks").assertIsDisplayed()
        composeRule.onNodeWithText("This profile is private").assertDoesNotExist()
    }

    @Test
    fun your_walks_tab_invites_to_the_first_walk() {
        show(own(), ownActions)

        openWalksTab()

        waitFor("No walks yet")
        scrollTo("No walks yet")
        composeRule.onNodeWithText("No walks yet").assertIsDisplayed()
    }

    @Test
    fun the_walks_tab_of_someone_else_only_says_there_are_none() {
        show(bea(RelationshipStatus.Following))

        openWalksTab()

        waitFor("No walks you can see.")
        composeRule.onNodeWithText("No walks yet").assertDoesNotExist()
    }

    @Test
    fun the_totals_are_on_the_walks_tab() {
        show(bea(RelationshipStatus.Following).copy(totals = TestWalks.totals(walkCount = 42)))

        openWalksTab()
        scrollTo("42")
        composeRule.onNodeWithText("42").assertIsDisplayed()
    }

    @Test
    fun blocking_says_what_will_happen_and_asks_first() {
        show(bea(RelationshipStatus.Friends, followsYou = true, dialog = ProfileDialog.ConfirmBlock))

        composeRule.onNodeWithText("Block Bea?").assertIsDisplayed()
        composeRule.onNode(hasText("You will stop being friends", substring = true)).assertIsDisplayed()
        composeRule.onNodeWithText("Block").performClick()

        assertEquals(listOf("confirmBlock"), calls)
    }

    @Test
    fun the_report_needs_a_reason() {
        show(bea(RelationshipStatus.None, dialog = ProfileDialog.Report))

        composeRule.onNodeWithText("Send").assertIsNotEnabled()
        composeRule.onNodeWithText("Harassment").performClick()

        assertEquals(listOf("reason:${ReportReason.Harassment}"), calls)
    }

    @Test
    fun with_a_reason_the_report_can_be_sent() {
        show(bea(RelationshipStatus.None, dialog = ProfileDialog.Report).copy(reportReason = ReportReason.Spam))

        composeRule.onNodeWithText("Send").assertIsEnabled().performClick()

        assertEquals(listOf("confirmReport"), calls)
    }

    @Test
    fun an_unverified_email_is_sent_to_verify() {
        show(bea(RelationshipStatus.None, dialog = ProfileDialog.VerifyEmail))

        composeRule.onNodeWithText("Verify").performClick()

        assertEquals(listOf("verify"), calls)
    }

    @Test
    fun someone_not_available_does_not_say_why() {
        show(ProfileUiState(isLoading = false, isUnavailable = true))

        composeRule.onNodeWithText("Profile not available").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("More options").assertDoesNotExist()
    }

    @Test
    fun a_failure_can_be_retried() {
        show(ProfileUiState(isLoading = false, loadError = AppError.Network))

        composeRule.onNodeWithText("Try again").performClick()

        assertEquals(listOf("refresh"), calls)
    }

    @Test
    fun a_walk_of_the_person_opens() {
        show(bea(RelationshipStatus.Friends, followsYou = true), walks = listOf(TestWalks.walk(id = "walk-9")))
        openWalksTab()

        // The pages arrive through a coroutine, and the list only has what fits on the screen.
        composeRule.waitUntil(WAIT_MILLIS) {
            runCatching {
                composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("3.12 km", substring = true))
            }.isSuccess
        }
        composeRule.onNode(hasText("3.12 km", substring = true)).performClick()

        assertEquals(listOf("walk:walk-9"), calls)
    }

    @Test
    fun no_walks_to_see_says_so() {
        show(bea(RelationshipStatus.None))
        openWalksTab()

        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("No walks you can see."))
        composeRule.onNodeWithText("No walks you can see.").assertIsDisplayed()
    }

    @Test
    fun the_photos_come_first_and_a_post_opens() {
        show(bea(RelationshipStatus.Following), posts = listOf(TestPosts.item(TestPosts.post("post-9"))))

        composeRule.waitUntil(WAIT_MILLIS) {
            runCatching {
                composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasContentDescription("Open post"))
            }.isSuccess
        }
        composeRule.onNodeWithContentDescription("Open post").performClick()

        assertEquals(listOf("post:post-9"), calls)
    }

    @Test
    fun a_repost_in_the_photos_says_so() {
        val item = TestPosts.item(TestPosts.post("post-9"), repostedBy = TestSocial.user())
        show(bea(RelationshipStatus.Following), posts = listOf(item))

        composeRule.waitUntil(WAIT_MILLIS) {
            runCatching {
                composeRule.onNode(
                    hasScrollToNodeAction(),
                ).performScrollToNode(hasContentDescription("Open reposted post"))
            }.isSuccess
        }
    }

    @Test
    fun without_photos_each_profile_says_something_different() {
        show(bea(RelationshipStatus.Following))

        scrollTo("No photos you can see.")
        composeRule.onNodeWithText("No photos you can see.").assertIsDisplayed()
    }

    @Test
    fun your_photos_tab_invites_to_the_first_photo() {
        show(own(), ownActions)

        waitFor("No photos yet")
        scrollTo("No photos yet")
        composeRule.onNodeWithText("No photos yet").assertIsDisplayed()
    }

    @Test
    fun a_photo_is_added_to_your_profile_by_creating_a_post() {
        show(own(), ownActions)

        scrollTo("Add photo")
        composeRule.onNodeWithText("Add photo").performClick()

        assertEquals(listOf("create"), calls)
    }

    @Test
    fun only_you_see_what_you_saved() {
        show(own(), ownActions, saved = listOf(TestPosts.post("post-3")))

        composeRule.onNodeWithText("Saved").performClick()
        composeRule.waitUntil(WAIT_MILLIS) {
            runCatching {
                composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("En el Retiro"))
            }.isSuccess
        }
        composeRule.onNodeWithText("En el Retiro").performClick()

        assertEquals(listOf("post:post-3"), calls)
    }

    @Test
    fun someone_else_has_no_saved_tab() {
        show(bea(RelationshipStatus.Friends, followsYou = true))

        composeRule.onNodeWithText("Saved").assertDoesNotExist()
    }

    @Test
    fun without_saved_posts_it_says_that_only_you_see_them() {
        show(own(), ownActions, saved = emptyList())

        composeRule.onNodeWithText("Saved").performClick()
        composeRule.waitUntil(WAIT_MILLIS) {
            runCatching {
                composeRule.onNode(hasScrollToNodeAction())
                    .performScrollToNode(hasText("Only you can see them", substring = true))
            }.isSuccess
        }
    }

    @Serializable
    data object StartRoute

    /** The ViewModels read the route arguments by property name: this checks them with real navigation. */
    @Test
    fun the_profile_and_the_lists_receive_their_arguments_from_the_route() {
        val people = FakePeopleRepository()
        val social = FakeSocialRepository()
        composeRule.setContent {
            val navController = rememberNavController()
            NavHost(navController = navController, startDestination = StartRoute) {
                composable<StartRoute> {
                    LaunchedEffect(Unit) { navController.navigate(UserProfileRoute("bea-id")) }
                }
                composable<UserProfileRoute> { entry ->
                    viewModel { UserProfileViewModel(createSavedStateHandle(), people, social, FakePostsRepository()) }
                    LaunchedEffect(Unit) {
                        navController.navigate(PeopleListRoute("bea-id", PeopleListKind.Friends.name))
                    }
                    Text(entry.toRoute<UserProfileRoute>().userId)
                }
                composable<PeopleListRoute> { entry ->
                    val list = viewModel { PeopleListViewModel(createSavedStateHandle(), people) }
                    Text("${list.kind}:${entry.toRoute<PeopleListRoute>().userId}")
                }
            }
        }

        composeRule.waitUntil(WAIT_MILLIS) {
            composeRule.onAllNodes(hasText("Friends:bea-id")).fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue("friends:bea-id" in people.reads)
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L
    }
}
