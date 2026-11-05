package com.geowoof.feature.profile

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.SavedStateHandle
import com.geowoof.core.model.AppError
import com.geowoof.core.model.Outcome
import com.geowoof.core.model.ProfileCounts
import com.geowoof.core.model.Relationship
import com.geowoof.core.model.RelationshipStatus
import com.geowoof.core.model.ReportReason
import com.geowoof.core.testing.FakePeopleRepository
import com.geowoof.core.testing.FakeSocialRepository
import com.geowoof.core.testing.MainDispatcherRule
import com.geowoof.core.testing.TestPets
import com.geowoof.core.testing.TestWalks
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class UserProfileViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val people = FakePeopleRepository()
    private val social = FakeSocialRepository()
    private val notFound = AppError.Api(status = 404, code = "identity.user_not_found")
    private val profilePrivate = AppError.Api(status = 403, code = "identity.profile_private")

    private fun viewModel() = UserProfileViewModel(SavedStateHandle(mapOf("userId" to "bea-id")), people, social)

    private fun relationship(
        status: RelationshipStatus,
        followsYou: Boolean = false,
        requestId: String? = null,
        incomingRequestId: String? = null,
    ) {
        social.relationshipResult = Outcome.Success(Relationship(status, followsYou, requestId, incomingRequestId))
    }

    private fun privateProfile(displayName: String = "bea") {
        people.profileResult = Outcome.Success(
            TestPets.profile(displayName = displayName, username = "bea", isPrivate = true),
        )
    }

    @Test
    fun it_brings_the_person_the_numbers_the_relationship_and_what_they_have() = runTest {
        people.petsResult = Outcome.Success(listOf(TestPets.pet()))
        relationship(RelationshipStatus.Friends)

        val state = viewModel().uiState.value

        assertFalse(state.isLoading)
        assertEquals("Bea", state.profile?.displayName)
        assertEquals(RelationshipStatus.Friends, state.relationship?.status)
        assertEquals(12, state.counts?.followers)
        assertEquals(TestWalks.totals(), state.totals)
        assertEquals(listOf("Luna"), state.pets.map { it.name })
        assertFalse(state.isUnavailable)
    }

    @Test
    fun a_public_profile_shows_its_content_to_anyone() = runTest {
        val state = viewModel().uiState.value

        assertFalse(state.isRestricted)
        assertTrue(state.canSeeContent)
    }

    @Test
    fun a_private_profile_that_you_do_not_follow_does_not_even_ask_for_its_content() = runTest {
        privateProfile()
        relationship(RelationshipStatus.None)

        val viewModel = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.walks.collect {} }

        assertTrue(viewModel.uiState.value.isRestricted)
        assertEquals(emptyList<String>(), people.reads)
        assertNull(viewModel.uiState.value.totals)
        assertEquals(12, viewModel.uiState.value.counts?.followers)
    }

    @Test
    fun asking_to_follow_a_private_profile_does_not_give_access_yet() = runTest {
        privateProfile()
        relationship(RelationshipStatus.None)
        social.followResult = Outcome.Success(Relationship(RelationshipStatus.Requested, requestId = "request-1"))
        val viewModel = viewModel()

        viewModel.onFollow()

        assertEquals(RelationshipStatus.Requested, viewModel.uiState.value.relationship?.status)
        assertTrue(viewModel.uiState.value.isRestricted)
        assertEquals(emptyList<String>(), people.reads)
    }

    @Test
    fun being_accepted_opens_the_content_of_a_private_profile() = runTest {
        privateProfile()
        relationship(RelationshipStatus.Requested, requestId = "request-1")
        people.petsResult = Outcome.Success(listOf(TestPets.pet()))
        val viewModel = viewModel()
        assertTrue(viewModel.uiState.value.isRestricted)

        relationship(RelationshipStatus.Following)
        privateProfile(displayName = "Bea Martín")
        viewModel.onResume()

        val state = viewModel.uiState.value
        assertFalse(state.isRestricted)
        assertEquals("Bea Martín", state.profile?.displayName)
        assertEquals(listOf("Luna"), state.pets.map { it.name })
    }

    @Test
    fun a_profile_that_has_just_become_private_is_not_an_error() = runTest {
        people.petsResult = Outcome.Failure(profilePrivate)
        people.totalsResult = Outcome.Failure(profilePrivate)

        val state = viewModel().uiState.value

        assertNull(state.loadError)
        assertFalse(state.isUnavailable)
    }

    @Test
    fun someone_who_does_not_exist_or_has_blocked_you_is_just_not_available() = runTest {
        people.profileResult = Outcome.Failure(notFound)
        social.relationshipResult = Outcome.Failure(notFound)
        people.countsResult = Outcome.Failure(notFound)

        val state = viewModel().uiState.value

        assertTrue(state.isUnavailable)
        assertNull(state.loadError)
        assertNull(state.profile)
    }

    @Test
    fun without_a_connection_the_profile_can_be_retried() = runTest {
        people.profileResult = Outcome.Failure(AppError.Network)
        val viewModel = viewModel()
        assertEquals(AppError.Network, viewModel.uiState.value.loadError)
        assertFalse(viewModel.uiState.value.isUnavailable)

        people.profileResult = Outcome.Success(TestPets.profile(displayName = "Bea"))
        viewModel.refresh()

        assertNull(viewModel.uiState.value.loadError)
        assertEquals("Bea", viewModel.uiState.value.profile?.displayName)
    }

    @Test
    fun a_failed_refresh_keeps_what_is_on_screen_and_says_it_once() = runTest {
        val viewModel = viewModel()
        people.countsResult = Outcome.Failure(AppError.Network)

        viewModel.refresh()

        assertEquals(AppError.Network, viewModel.uiState.value.loadError)
        assertEquals(12, viewModel.uiState.value.counts?.followers)
        viewModel.onMessageShown()
        assertNull(viewModel.uiState.value.loadError)
    }

    @Test
    fun following_updates_the_relationship_and_the_numbers() = runTest {
        val viewModel = viewModel()
        people.countsResult = Outcome.Success(ProfileCounts(13, 8, 5))

        viewModel.onFollow()

        assertEquals(listOf("follow:bea-id"), social.calls)
        assertEquals(RelationshipStatus.Following, viewModel.uiState.value.relationship?.status)
        assertEquals(13, viewModel.uiState.value.counts?.followers)
        assertFalse(viewModel.uiState.value.isActing)
    }

    @Test
    fun following_back_asks_first_and_then_makes_friends() = runTest {
        relationship(RelationshipStatus.None, followsYou = true)
        social.followResult = Outcome.Success(Relationship(RelationshipStatus.Friends, followsYou = true))
        val viewModel = viewModel()

        viewModel.onShowDialog(ProfileDialog.ConfirmFollowBack)
        assertEquals(ProfileDialog.ConfirmFollowBack, viewModel.uiState.value.dialog)
        assertEquals(emptyList<String>(), social.calls)

        viewModel.onConfirmFollowBack()

        assertEquals(listOf("follow:bea-id"), social.calls)
        assertNull(viewModel.uiState.value.dialog)
        assertEquals(RelationshipStatus.Friends, viewModel.uiState.value.relationship?.status)
    }

    @Test
    fun an_unverified_email_is_asked_to_verify_instead_of_an_error() = runTest {
        social.followResult = Outcome.Failure(AppError.Api(status = 403, code = "auth.forbidden"))
        val viewModel = viewModel()

        viewModel.onFollow()

        assertEquals(ProfileDialog.VerifyEmail, viewModel.uiState.value.dialog)
        assertNull(viewModel.uiState.value.actionError)
    }

    @Test
    fun any_other_failure_to_follow_is_shown() = runTest {
        social.followResult = Outcome.Failure(AppError.Network)
        val viewModel = viewModel()

        viewModel.onFollow()

        assertEquals(AppError.Network, viewModel.uiState.value.actionError)
        viewModel.onMessageShown()
        assertNull(viewModel.uiState.value.actionError)
    }

    @Test
    fun unfollowing_needs_a_confirmation() = runTest {
        relationship(RelationshipStatus.Friends, followsYou = true)
        val viewModel = viewModel()

        viewModel.onShowDialog(ProfileDialog.ConfirmUnfollow)
        assertEquals(emptyList<String>(), social.calls)

        relationship(RelationshipStatus.None, followsYou = true)
        viewModel.onConfirmUnfollow()

        assertEquals(listOf("unfollow:bea-id"), social.calls)
        assertNull(viewModel.uiState.value.dialog)
        assertEquals(RelationshipStatus.None, viewModel.uiState.value.relationship?.status)
    }

    @Test
    fun a_pending_request_is_cancelled_by_unfollowing() = runTest {
        relationship(RelationshipStatus.Requested, requestId = "request-9")
        val viewModel = viewModel()
        relationship(RelationshipStatus.None)

        viewModel.onUnfollow()

        assertEquals(listOf("unfollow:bea-id"), social.calls)
        assertEquals(RelationshipStatus.None, viewModel.uiState.value.relationship?.status)
    }

    @Test
    fun a_follower_can_be_removed_after_a_confirmation() = runTest {
        relationship(RelationshipStatus.None, followsYou = true)
        val viewModel = viewModel()
        relationship(RelationshipStatus.None)

        viewModel.onShowDialog(ProfileDialog.ConfirmRemoveFollower)
        viewModel.onConfirmRemoveFollower()

        assertEquals(listOf("removeFollower:bea-id"), social.calls)
        assertFalse(viewModel.uiState.value.relationship?.followsYou == true)
    }

    @Test
    fun an_incoming_request_can_be_accepted_and_the_relationship_comes_from_the_server() = runTest {
        relationship(RelationshipStatus.None, incomingRequestId = "request-9")
        val viewModel = viewModel()
        relationship(RelationshipStatus.None, followsYou = true)

        viewModel.onAcceptRequest()

        assertEquals(listOf("accept:request-9"), social.calls)
        assertTrue(viewModel.uiState.value.relationship?.followsYou == true)
        assertNull(viewModel.uiState.value.relationship?.incomingRequestId)
    }

    @Test
    fun an_incoming_request_can_be_rejected() = runTest {
        relationship(RelationshipStatus.None, incomingRequestId = "request-9")
        val viewModel = viewModel()
        relationship(RelationshipStatus.None)

        viewModel.onRejectRequest()

        assertEquals(listOf("reject:request-9"), social.calls)
        assertNull(viewModel.uiState.value.relationship?.incomingRequestId)
    }

    @Test
    fun an_answer_the_server_rejects_is_shown_with_the_relationship_as_it_is_now() = runTest {
        relationship(RelationshipStatus.None, incomingRequestId = "request-9")
        val viewModel = viewModel()
        val conflict = AppError.Api(status = 409, code = "social.request_not_pending")
        social.actionResult = Outcome.Failure(conflict)
        relationship(RelationshipStatus.None)

        viewModel.onAcceptRequest()

        assertEquals(conflict, viewModel.uiState.value.actionError)
        assertNull(viewModel.uiState.value.relationship?.incomingRequestId)
    }

    @Test
    fun blocking_leaves_the_profile() = runTest {
        val viewModel = viewModel()

        viewModel.onShowDialog(ProfileDialog.ConfirmBlock)
        viewModel.onConfirmBlock()

        assertEquals(listOf("block:bea-id"), social.calls)
        assertTrue(viewModel.uiState.value.isFinished)
    }

    @Test
    fun a_block_that_fails_stays_on_the_profile() = runTest {
        social.actionResult = Outcome.Failure(AppError.Network)
        val viewModel = viewModel()

        viewModel.onConfirmBlock()

        assertFalse(viewModel.uiState.value.isFinished)
        assertEquals(AppError.Network, viewModel.uiState.value.actionError)
    }

    @Test
    fun a_report_needs_a_reason_and_sends_the_details() = runTest {
        val viewModel = viewModel()
        viewModel.onShowDialog(ProfileDialog.Report)

        viewModel.onConfirmReport()
        assertEquals(emptyList<String>(), social.calls)

        viewModel.onSelectReportReason(ReportReason.Spam)
        viewModel.reportDetails.setTextAndPlaceCursorAtEnd("Anuncios")
        viewModel.onConfirmReport()

        assertEquals(listOf("report:bea-id:Spam:Anuncios"), social.calls)
        assertTrue(viewModel.uiState.value.wasReported)
        assertNull(viewModel.uiState.value.dialog)
        assertEquals("", viewModel.reportDetails.text.toString())
    }

    @Test
    fun closing_the_report_forgets_what_was_written() = runTest {
        val viewModel = viewModel()
        viewModel.onShowDialog(ProfileDialog.Report)
        viewModel.onSelectReportReason(ReportReason.Other)
        viewModel.reportDetails.setTextAndPlaceCursorAtEnd("Algo")

        viewModel.onDismissDialog()

        assertNull(viewModel.uiState.value.reportReason)
        assertEquals("", viewModel.reportDetails.text.toString())
    }

    @Test
    fun only_the_person_and_their_friends_can_open_the_lists() = runTest {
        val cases = mapOf(
            RelationshipStatus.Self to true,
            RelationshipStatus.Friends to true,
            RelationshipStatus.Following to false,
            RelationshipStatus.Requested to false,
            RelationshipStatus.None to false,
        )
        cases.forEach { (status, canOpen) ->
            relationship(status)
            assertEquals(status.name, canOpen, viewModel().uiState.value.canOpenLists)
        }
    }

    @Test
    fun coming_back_with_the_same_relationship_does_not_reload_the_walks() = runTest {
        val viewModel = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.walks.collect {} }
        val before = people.reads.count { it == "walks:bea-id" }

        viewModel.onResume()
        assertEquals(before, people.reads.count { it == "walks:bea-id" })

        relationship(RelationshipStatus.Friends)
        viewModel.onResume()
        assertEquals(before + 1, people.reads.count { it == "walks:bea-id" })
    }

    @Test
    fun pulling_to_refresh_reloads_the_walks() = runTest {
        val viewModel = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.walks.collect {} }
        val before = people.reads.count { it == "walks:bea-id" }

        viewModel.refresh()

        assertEquals(before + 1, people.reads.count { it == "walks:bea-id" })
    }
}
