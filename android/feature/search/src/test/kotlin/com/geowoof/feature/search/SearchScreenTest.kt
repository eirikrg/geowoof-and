package com.geowoof.feature.search

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
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
import com.geowoof.core.model.PetSearchResult
import com.geowoof.core.model.UserSummary
import com.geowoof.core.testing.TestSocial
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val opened = mutableListOf<String>()
    private val tabs = mutableListOf<SearchTab>()

    private fun show(
        uiState: SearchUiState,
        users: PagingData<UserSummary> = TestSocial.pages(emptyList()),
        pets: PagingData<PetSearchResult> = TestSocial.pages(emptyList()),
    ) {
        composeRule.setContent {
            GeoWoofTheme(dynamicColor = false) {
                SearchContent(
                    uiState = uiState,
                    query = TextFieldState(),
                    users = flowOf(users).collectAsLazyPagingItems(),
                    pets = flowOf(pets).collectAsLazyPagingItems(),
                    onSelectTab = { tabs += it },
                    onOpenUser = { opened += it },
                )
            }
        }
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(WAIT_MILLIS) { composeRule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun before_searching_it_explains_what_to_do() {
        show(SearchUiState())

        composeRule.onNodeWithText("People and pets").assertIsDisplayed()
    }

    @Test
    fun a_person_found_opens_their_profile() {
        show(SearchUiState(hasEnoughText = true), users = TestSocial.pages(listOf(TestSocial.user())))

        waitForText("Bea")
        composeRule.onNodeWithText("@bea · Madrid").assertIsDisplayed()
        composeRule.onNodeWithText("Bea").performClick()

        assertEquals(listOf("bea-id"), opened)
    }

    @Test
    fun a_pet_found_says_whose_it_is_and_opens_the_profile_of_the_owner() {
        show(
            SearchUiState(tab = SearchTab.Pets, hasEnoughText = true),
            pets = TestSocial.pages(listOf(TestSocial.petResult())),
        )

        waitForText("Luna")
        composeRule.onNodeWithText("Border collie · from @bea").assertIsDisplayed()
        composeRule.onNodeWithText("Luna").performClick()

        assertEquals(listOf("bea-id"), opened)
    }

    @Test
    fun finding_nothing_is_not_an_error() {
        show(SearchUiState(hasEnoughText = true))

        waitForText("Nothing found")
        composeRule.onNodeWithText("No one matches what you wrote.").assertIsDisplayed()
    }

    @Test
    fun a_failure_says_why_and_can_be_retried() {
        val failed = LoadStates(
            refresh = LoadState.Error(RemoteLoadException(AppError.Network)),
            prepend = LoadState.NotLoading(false),
            append = LoadState.NotLoading(false),
        )

        show(SearchUiState(hasEnoughText = true), users = PagingData.empty(failed))

        waitForText("Try again")
        composeRule.onNodeWithText("Try again").assertIsDisplayed()
    }

    @Test
    fun the_tabs_change_the_list() {
        show(SearchUiState())

        composeRule.onNodeWithText("Pets").performClick()

        assertEquals(listOf(SearchTab.Pets), tabs)
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L
    }
}
