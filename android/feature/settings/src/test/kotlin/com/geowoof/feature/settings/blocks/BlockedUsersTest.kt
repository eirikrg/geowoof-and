package com.geowoof.feature.settings.blocks

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.core.model.AppError
import com.geowoof.core.model.Outcome
import com.geowoof.core.testing.FakeSocialRepository
import com.geowoof.core.testing.MainDispatcherRule
import com.geowoof.core.testing.TestSocial
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BlockedUsersTest {
    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val social = FakeSocialRepository()
    private val actions = mutableListOf<String>()

    private fun show(uiState: BlockedUsersUiState) {
        composeRule.setContent {
            GeoWoofTheme(dynamicColor = false) {
                BlockedUsersContent(
                    uiState = uiState,
                    onBack = { actions += "back" },
                    onRefresh = { actions += "refresh" },
                    onUnblock = { actions += "unblock:$it" },
                    onDismissError = { actions += "shown" },
                )
            }
        }
    }

    @Test
    fun a_blocked_person_can_be_unblocked() {
        show(BlockedUsersUiState(blocked = listOf(TestSocial.blocked()), isRefreshing = false))

        composeRule.onNodeWithText("Bea").assertIsDisplayed()
        composeRule.onNodeWithText("@bea").assertIsDisplayed()
        composeRule.onNodeWithText("Unblock").performClick()

        assertEquals(listOf("unblock:bea-id"), actions)
    }

    @Test
    fun the_button_waits_for_the_server() {
        show(BlockedUsersUiState(blocked = listOf(TestSocial.blocked()), isRefreshing = false, isUnblocking = true))

        composeRule.onNodeWithText("Unblock").assertIsNotEnabled()
    }

    @Test
    fun without_blocks_it_says_how_to_block() {
        show(BlockedUsersUiState(isRefreshing = false))

        composeRule.onNodeWithText("Nobody blocked").assertIsDisplayed()
    }

    @Test
    fun the_list_refreshes_and_unblocking_removes_the_person() = runTest {
        social.blockedUsers.value = listOf(TestSocial.blocked())
        val viewModel = BlockedUsersViewModel(social)
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()

        viewModel.onUnblock("bea-id")
        runCurrent()

        assertEquals(listOf("refreshBlocked", "unblock:bea-id"), social.calls)
        assertEquals(emptyList<Any>(), viewModel.uiState.value.blocked)
        assertFalse(viewModel.uiState.value.isUnblocking)
    }

    @Test
    fun a_failure_is_shown_and_the_person_stays_in_the_list() = runTest {
        social.blockedUsers.value = listOf(TestSocial.blocked())
        social.actionResult = Outcome.Failure(AppError.Network)
        val viewModel = BlockedUsersViewModel(social)
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()

        viewModel.onUnblock("bea-id")
        runCurrent()

        assertEquals(AppError.Network, viewModel.uiState.value.error)
        assertEquals(1, viewModel.uiState.value.blocked.size)

        viewModel.onErrorShown()
        runCurrent()
        assertNull(viewModel.uiState.value.error)
    }
}
