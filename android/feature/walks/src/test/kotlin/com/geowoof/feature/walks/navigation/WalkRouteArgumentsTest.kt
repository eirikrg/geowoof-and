package com.geowoof.feature.walks.navigation

import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.geowoof.core.common.AppConfig
import com.geowoof.core.testing.FakePetsRepository
import com.geowoof.core.testing.FakeWalksRepository
import com.geowoof.feature.walks.WalksViewModel
import com.geowoof.feature.walks.detail.WalkDetailViewModel
import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The ViewModels read the route arguments by property name: this checks them with real navigation. */
@RunWith(AndroidJUnit4::class)
class WalkRouteArgumentsTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val walks = FakeWalksRepository()
    private val pets = FakePetsRepository()

    @Serializable
    data object StartRoute

    private fun navigateTo(route: Any) {
        composeRule.setContent {
            val navController = rememberNavController()
            NavHost(navController = navController, startDestination = StartRoute) {
                composable<StartRoute> {
                    LaunchedEffect(Unit) { navController.navigate(route) }
                }
                composable<WalksRoute> {
                    viewModel { WalksViewModel(createSavedStateHandle(), walks, pets) }
                    Text("list")
                }
                composable<WalkDetailRoute> {
                    viewModel {
                        WalkDetailViewModel(
                            createSavedStateHandle(),
                            walks,
                            pets,
                            AppConfig("http://localhost/", null, false),
                        )
                    }
                    Text("detail")
                }
            }
        }
    }

    @Test
    fun the_list_receives_the_pet() {
        navigateTo(WalksRoute("luna"))

        composeRule.onNodeWithText("list").assertIsDisplayed()
        assertEquals(listOf<String?>("luna"), walks.refreshedTotals)
    }

    @Test
    fun without_a_pet_it_is_the_list_of_the_person() {
        navigateTo(WalksRoute())

        composeRule.onNodeWithText("list").assertIsDisplayed()
        assertEquals(listOf<String?>(null), walks.refreshedTotals)
    }

    @Test
    fun the_detail_receives_the_walk() {
        navigateTo(WalkDetailRoute("walk-1"))

        composeRule.onNodeWithText("detail").assertIsDisplayed()
        assertEquals(listOf("walk-1"), walks.refreshedWalks)
    }
}
