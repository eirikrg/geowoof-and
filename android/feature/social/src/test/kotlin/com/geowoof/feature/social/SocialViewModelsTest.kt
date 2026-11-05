package com.geowoof.feature.social

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.SavedStateHandle
import com.geowoof.core.model.AppError
import com.geowoof.core.model.Outcome
import com.geowoof.core.model.Relationship
import com.geowoof.core.model.RelationshipStatus
import com.geowoof.core.model.ReportReason
import com.geowoof.core.model.RequestDirection
import com.geowoof.core.testing.FakePeopleRepository
import com.geowoof.core.testing.FakeSocialRepository
import com.geowoof.core.testing.MainDispatcherRule
import com.geowoof.core.testing.TestPets
import com.geowoof.core.testing.TestSocial
import com.geowoof.feature.social.friends.FriendRequestsViewModel
import com.geowoof.feature.social.friends.FriendsViewModel
import com.geowoof.feature.social.profile.ProfileDialog
import com.geowoof.feature.social.profile.UserProfileViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SocialViewModelsTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val people = FakePeopleRepository()
    private val social = FakeSocialRepository()
    private val notFound = AppError.Api(status = 404, code = "identity.user_not_found")

    private fun profile() = UserProfileViewModel(SavedStateHandle(mapOf("userId" to "bea-id")), people, social)

    private fun relationship(status: RelationshipStatus, requestId: String? = null) {
        social.relationshipResult = Outcome.Success(Relationship(status, requestId))
    }

    @Test
    fun the_profile_brings_the_person_their_pets_and_the_relationship() = runTest {
        people.petsResult = Outcome.Success(listOf(TestPets.pet()))
        relationship(RelationshipStatus.Friends)

        val state = profile().uiState.value

        assertFalse(state.isLoading)
        assertEquals("Bea", state.profile?.displayName)
        assertEquals(RelationshipStatus.Friends, state.relationship?.status)
        assertEquals(listOf("Luna"), state.pets.map { it.name })
        assertFalse(state.isUnavailable)
    }

    @Test
    fun someone_who_does_not_exist_or_has_blocked_you_is_just_not_available() = runTest {
        people.profileResult = Outcome.Failure(notFound)
        social.relationshipResult = Outcome.Failure(notFound)
        people.petsResult = Outcome.Failure(notFound)

        val state = profile().uiState.value

        assertTrue(state.isUnavailable)
        assertNull(state.loadError)
        assertNull(state.profile)
    }

    @Test
    fun without_a_connection_the_profile_can_be_retried() = runTest {
        people.profileResult = Outcome.Failure(AppError.Network)
        val viewModel = profile()
        assertEquals(AppError.Network, viewModel.uiState.value.loadError)
        assertFalse(viewModel.uiState.value.isUnavailable)

        people.profileResult = Outcome.Success(TestPets.profile(displayName = "Bea"))
        viewModel.refresh()

        assertNull(viewModel.uiState.value.loadError)
        assertEquals("Bea", viewModel.uiState.value.profile?.displayName)
    }

    @Test
    fun sending_a_request_shows_that_it_is_sent() = runTest {
        val viewModel = profile()

        viewModel.onSendRequest()

        assertEquals(listOf("send:bea-id"), social.calls)
        assertEquals(RelationshipStatus.RequestSent, viewModel.uiState.value.relationship?.status)
        assertEquals("request-1", viewModel.uiState.value.relationship?.requestId)
        assertFalse(viewModel.uiState.value.isActing)
    }

    @Test
    fun an_unverified_email_is_asked_to_verify_instead_of_an_error() = runTest {
        social.sendResult = Outcome.Failure(AppError.Api(status = 403, code = "auth.forbidden"))
        val viewModel = profile()

        viewModel.onSendRequest()

        assertEquals(ProfileDialog.VerifyEmail, viewModel.uiState.value.dialog)
        assertNull(viewModel.uiState.value.actionError)
    }

    @Test
    fun any_other_failure_to_send_is_shown() = runTest {
        social.sendResult = Outcome.Failure(AppError.Network)
        val viewModel = profile()

        viewModel.onSendRequest()

        assertEquals(AppError.Network, viewModel.uiState.value.actionError)
        viewModel.onMessageShown()
        assertNull(viewModel.uiState.value.actionError)
    }

    @Test
    fun a_received_request_can_be_accepted_and_the_relationship_comes_from_the_server() = runTest {
        relationship(RelationshipStatus.RequestReceived, "request-9")
        val viewModel = profile()
        relationship(RelationshipStatus.Friends)

        viewModel.onAcceptRequest()

        assertEquals(listOf("accept:request-9"), social.calls)
        assertEquals(RelationshipStatus.Friends, viewModel.uiState.value.relationship?.status)
    }

    @Test
    fun a_received_request_can_be_rejected() = runTest {
        relationship(RelationshipStatus.RequestReceived, "request-9")
        val viewModel = profile()
        relationship(RelationshipStatus.None)

        viewModel.onRejectRequest()

        assertEquals(listOf("reject:request-9"), social.calls)
        assertEquals(RelationshipStatus.None, viewModel.uiState.value.relationship?.status)
    }

    @Test
    fun a_sent_request_can_be_cancelled() = runTest {
        relationship(RelationshipStatus.RequestSent, "request-9")
        val viewModel = profile()
        relationship(RelationshipStatus.None)

        viewModel.onCancelRequest()

        assertEquals(listOf("cancel:request-9"), social.calls)
    }

    @Test
    fun an_answer_the_server_rejects_is_shown_with_the_relationship_as_it_is_now() = runTest {
        relationship(RelationshipStatus.RequestReceived, "request-9")
        val viewModel = profile()
        val conflict = AppError.Api(status = 409, code = "social.request_not_pending")
        social.actionResult = Outcome.Failure(conflict)
        relationship(RelationshipStatus.None)

        viewModel.onAcceptRequest()

        assertEquals(conflict, viewModel.uiState.value.actionError)
        assertEquals(RelationshipStatus.None, viewModel.uiState.value.relationship?.status)
    }

    @Test
    fun removing_a_friend_needs_a_confirmation() = runTest {
        relationship(RelationshipStatus.Friends)
        val viewModel = profile()

        viewModel.onShowDialog(ProfileDialog.ConfirmRemoveFriend)
        assertEquals(ProfileDialog.ConfirmRemoveFriend, viewModel.uiState.value.dialog)
        assertEquals(emptyList<String>(), social.calls)

        relationship(RelationshipStatus.None)
        viewModel.onConfirmRemoveFriend()

        assertEquals(listOf("remove:bea-id"), social.calls)
        assertNull(viewModel.uiState.value.dialog)
        assertEquals(RelationshipStatus.None, viewModel.uiState.value.relationship?.status)
    }

    @Test
    fun blocking_leaves_the_profile() = runTest {
        val viewModel = profile()

        viewModel.onShowDialog(ProfileDialog.ConfirmBlock)
        viewModel.onConfirmBlock()

        assertEquals(listOf("block:bea-id"), social.calls)
        assertTrue(viewModel.uiState.value.isFinished)
    }

    @Test
    fun a_block_that_fails_stays_on_the_profile() = runTest {
        social.actionResult = Outcome.Failure(AppError.Network)
        val viewModel = profile()

        viewModel.onConfirmBlock()

        assertFalse(viewModel.uiState.value.isFinished)
        assertEquals(AppError.Network, viewModel.uiState.value.actionError)
    }

    @Test
    fun a_report_needs_a_reason_and_sends_the_details() = runTest {
        val viewModel = profile()
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
        val viewModel = profile()
        viewModel.onShowDialog(ProfileDialog.Report)
        viewModel.onSelectReportReason(ReportReason.Other)
        viewModel.reportDetails.setTextAndPlaceCursorAtEnd("Algo")

        viewModel.onDismissDialog()

        assertNull(viewModel.uiState.value.reportReason)
        assertEquals("", viewModel.reportDetails.text.toString())
    }

    @Test
    fun friends_stay_on_screen_when_the_list_cannot_refresh() = runTest {
        social.friends.value = listOf(TestSocial.friend())
        social.refreshResult = Outcome.Failure(AppError.Network)
        val viewModel = FriendsViewModel(social)
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()

        assertEquals(1, viewModel.uiState.value.friends.size)
        assertEquals(AppError.Network, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isRefreshing)

        viewModel.onErrorShown()
        runCurrent()
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun requests_can_be_answered_from_the_list() = runTest {
        social.receivedRequests.value = listOf(TestSocial.request("1"), TestSocial.request("2"))
        social.sentRequests.value = listOf(TestSocial.request("3", direction = RequestDirection.Sent))
        val viewModel = FriendRequestsViewModel(social)
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()

        viewModel.onAccept("1")
        viewModel.onReject("2")
        viewModel.onCancel("3")
        runCurrent()

        assertEquals(listOf("refreshRequests", "accept:1", "reject:2", "cancel:3"), social.calls)
        assertEquals(emptyList<Any>(), viewModel.uiState.value.received)
        assertEquals(emptyList<Any>(), viewModel.uiState.value.sent)
    }

    @Test
    fun an_answer_that_fails_is_shown_and_keeps_the_request() = runTest {
        social.receivedRequests.value = listOf(TestSocial.request("1"))
        social.actionResult = Outcome.Failure(AppError.Network)
        val viewModel = FriendRequestsViewModel(social)
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()

        viewModel.onAccept("1")
        runCurrent()

        assertEquals(AppError.Network, viewModel.uiState.value.error)
        assertEquals(1, viewModel.uiState.value.received.size)
        assertNull(viewModel.uiState.value.answeringId)
    }

    @Test
    fun the_community_counts_friends_and_received_requests_and_refreshes_them() = runTest {
        social.friends.value = listOf(TestSocial.friend())
        social.receivedRequests.value = listOf(TestSocial.request("1"), TestSocial.request("2"))
        val viewModel = CommunityViewModel(social)
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()

        viewModel.refresh()
        runCurrent()

        assertEquals(CommunityUiState(friendCount = 1, requestCount = 2), viewModel.uiState.value)
        assertEquals(listOf("refreshFriends", "refreshRequests"), social.calls)
    }
}
