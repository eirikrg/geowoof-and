package com.geowoof.feature.pets.navigation

import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.geowoof.core.domain.ParseWeightUseCase
import com.geowoof.core.domain.ValidatePetUseCase
import com.geowoof.core.testing.FakeMediaRepository
import com.geowoof.core.testing.FakePetsRepository
import com.geowoof.core.testing.FakeWalksRepository
import com.geowoof.core.testing.TestPets
import com.geowoof.feature.pets.detail.PetDetailViewModel
import com.geowoof.feature.pets.editor.PetEditorViewModel
import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The ViewModels read the route arguments by property name: this checks them with real navigation. */
@RunWith(AndroidJUnit4::class)
class PetRouteArgumentsTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val pets = FakePetsRepository(listOf(TestPets.pet()))

    @Serializable
    data object StartRoute

    private fun navigateTo(route: Any) {
        composeRule.setContent {
            val navController = rememberNavController()
            NavHost(navController = navController, startDestination = StartRoute) {
                composable<StartRoute> {
                    LaunchedEffect(Unit) { navController.navigate(route) }
                }
                composable<PetEditorRoute> {
                    val viewModel = viewModel {
                        PetEditorViewModel(createSavedStateHandle(), pets, FakeMediaRepository(), ValidatePetUseCase())
                    }
                    Text("editing=${viewModel.uiState.collectAsState().value.isEditing}")
                }
                composable<PetDetailRoute> {
                    viewModel {
                        PetDetailViewModel(createSavedStateHandle(), pets, FakeWalksRepository(), ParseWeightUseCase())
                    }
                    Text("detail")
                }
            }
        }
    }

    @Test
    fun the_editor_receives_the_pet_to_edit() {
        navigateTo(PetEditorRoute("luna"))

        composeRule.onNodeWithText("editing=true").assertIsDisplayed()
    }

    @Test
    fun without_a_pet_the_editor_creates_a_new_one() {
        navigateTo(PetEditorRoute())

        composeRule.onNodeWithText("editing=false").assertIsDisplayed()
    }

    @Test
    fun the_detail_receives_the_pet_to_show() {
        navigateTo(PetDetailRoute("luna"))

        composeRule.onNodeWithText("detail").assertIsDisplayed()
        assertEquals(listOf("refreshPet:luna", "refreshWeights:luna"), pets.calls)
    }
}
