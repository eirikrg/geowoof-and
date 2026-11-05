package com.geowoof.feature.walks.detail

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.geowoof.core.common.AppConfig
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.core.model.AppError
import com.geowoof.core.model.Outcome
import com.geowoof.core.model.UserWalk
import com.geowoof.core.testing.FakePeopleRepository
import com.geowoof.core.testing.MainDispatcherRule
import com.geowoof.core.testing.TestPets
import com.geowoof.core.testing.TestWalks
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UserWalkTest {
    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val people = FakePeopleRepository()
    private val config = AppConfig(
        apiBaseUrl = "http://localhost/",
        googleServerClientId = null,
        logHttpTraffic = false,
        isMapsConfigured = false,
    )
    private var retries = 0

    private val walk = UserWalk(TestWalks.walk(petIds = listOf("luna")), "bea-id", listOf(TestWalks.segment(3)))

    private fun viewModel() = UserWalkViewModel(SavedStateHandle(mapOf("walkId" to "walk-1")), people, config)

    private fun show(uiState: UserWalkUiState) {
        composeRule.setContent {
            GeoWoofTheme(dynamicColor = false) {
                UserWalkContent(uiState = uiState, onBack = {}, onRetry = { retries++ }) { Text("map") }
            }
        }
    }

    @Test
    fun it_brings_the_walk_with_the_names_of_the_pets() = runTest {
        people.walkResult = Outcome.Success(walk)
        people.petsResult = Outcome.Success(listOf(TestPets.pet(id = "luna", name = "Luna")))

        val state = viewModel().uiState.value

        assertEquals(walk, state.walk)
        assertEquals("Luna", state.petNames)
        assertFalse(state.isLoading)
    }

    @Test
    fun without_the_pets_the_walk_is_still_shown() = runTest {
        people.walkResult = Outcome.Success(walk)
        people.petsResult = Outcome.Failure(AppError.Network)

        val state = viewModel().uiState.value

        assertEquals(walk, state.walk)
        assertEquals("", state.petNames)
    }

    @Test
    fun a_walk_that_cannot_be_seen_is_an_error_that_can_be_retried() = runTest {
        people.walkResult = Outcome.Failure(AppError.Api(status = 404, code = "walks.not_found"))
        val viewModel = viewModel()
        assertEquals("walks.not_found", (viewModel.uiState.value.error as AppError.Api).code)

        people.walkResult = Outcome.Success(walk)
        viewModel.refresh()

        assertNull(viewModel.uiState.value.error)
        assertEquals(walk, viewModel.uiState.value.walk)
    }

    @Test
    fun a_walk_with_a_route_shows_the_map() {
        show(UserWalkUiState(isLoading = false, walk = walk, petNames = "Luna"))

        composeRule.onNodeWithText("map").assertIsDisplayed()
        composeRule.onNodeWithText("Luna").assertIsDisplayed()
    }

    @Test
    fun a_walk_without_a_visible_route_explains_why_and_keeps_the_numbers() {
        show(UserWalkUiState(isLoading = false, walk = walk.copy(route = emptyList())))

        composeRule.onNodeWithText("map").assertDoesNotExist()
        composeRule.onNodeWithText(
            "The route is hidden: the walk was close to where it started and ended.",
        ).assertIsDisplayed()
        composeRule.onNodeWithText("3.12 km").assertIsDisplayed()
    }

    @Test
    fun a_failure_can_be_retried() {
        show(UserWalkUiState(isLoading = false, error = AppError.Network))

        composeRule.onNodeWithText("Try again").performClick()

        assertEquals(1, retries)
    }
}
