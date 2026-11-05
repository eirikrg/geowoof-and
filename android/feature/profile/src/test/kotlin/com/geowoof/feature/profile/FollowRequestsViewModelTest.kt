package com.geowoof.feature.profile

import com.geowoof.core.model.AppError
import com.geowoof.core.model.Outcome
import com.geowoof.core.model.RequestDirection
import com.geowoof.core.testing.FakeSocialRepository
import com.geowoof.core.testing.MainDispatcherRule
import com.geowoof.core.testing.TestSocial
import com.geowoof.feature.profile.requests.FollowRequestsViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class FollowRequestsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val social = FakeSocialRepository()

    @Test
    fun requests_can_be_answered_from_the_list() = runTest {
        social.receivedRequests.value = listOf(TestSocial.request("1"), TestSocial.request("2"))
        social.sentRequests.value = listOf(TestSocial.request("3", direction = RequestDirection.Sent))
        val viewModel = FollowRequestsViewModel(social)
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
        val viewModel = FollowRequestsViewModel(social)
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()

        viewModel.onAccept("1")
        runCurrent()

        assertEquals(AppError.Network, viewModel.uiState.value.error)
        assertEquals(1, viewModel.uiState.value.received.size)
        assertNull(viewModel.uiState.value.answeringId)
    }
}
