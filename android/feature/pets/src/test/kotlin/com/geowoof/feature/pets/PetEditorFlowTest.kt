package com.geowoof.feature.pets

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.core.domain.ValidatePetUseCase
import com.geowoof.core.model.PetSex
import com.geowoof.core.model.PetSize
import com.geowoof.core.model.PetSpecies
import com.geowoof.core.testing.FakeMediaRepository
import com.geowoof.core.testing.FakePetsRepository
import com.geowoof.feature.pets.editor.PetEditorActions
import com.geowoof.feature.pets.editor.PetEditorContent
import com.geowoof.feature.pets.editor.PetEditorFields
import com.geowoof.feature.pets.editor.PetEditorViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PetEditorFlowTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val pets = FakePetsRepository()
    private var skipped = 0

    private fun showForm(onboarding: Boolean = false) {
        val viewModel = PetEditorViewModel(SavedStateHandle(), pets, FakeMediaRepository(), ValidatePetUseCase())
        composeRule.setContent {
            GeoWoofTheme(dynamicColor = false) {
                val uiState by viewModel.uiState.collectAsState()
                PetEditorContent(
                    uiState = uiState,
                    fields = PetEditorFields(
                        viewModel.name,
                        viewModel.speciesOther,
                        viewModel.breed,
                        viewModel.color,
                        viewModel.microchip,
                        viewModel.bio,
                    ),
                    actions = PetEditorActions(
                        viewModel::onSpeciesChange,
                        viewModel::onSexChange,
                        viewModel::onSizeChange,
                        viewModel::onNeuteredChange,
                        viewModel::onBirthDateChange,
                        viewModel::removePhoto,
                        viewModel::save,
                    ),
                    onPickPhoto = {},
                    onBack = if (onboarding) null else ({}),
                    onSkip = if (onboarding) ({ skipped++ }) else null,
                )
            }
        }
    }

    private fun field(label: String) = composeRule.onNodeWithText(label).performScrollTo()

    private fun tap(text: String) =
        composeRule.onNode(hasText(text) and hasClickAction()).performScrollTo().performClick()

    @Test
    fun a_pet_is_created_from_the_form() {
        showForm()

        field("Name").performTextInput("Toby")
        tap("Male")
        tap("Small")
        field("Breed").performTextInput("Teckel")
        tap("Neutered")
        tap("Save")
        composeRule.waitForIdle()

        val draft = pets.created.single()
        assertEquals("Toby", draft.name)
        assertEquals("Teckel", draft.breed)
        assertEquals(PetSpecies.Dog, draft.species)
        assertEquals(PetSex.Male, draft.sex)
        assertEquals(PetSize.Small, draft.size)
        assertTrue(draft.neutered)
    }

    @Test
    fun a_name_is_required() {
        showForm()

        tap("Save")

        field("Enter its name").assertIsDisplayed()
        assertTrue(pets.created.isEmpty())
    }

    @Test
    fun other_species_ask_which_one() {
        showForm()
        composeRule.onNodeWithText("Which species?").assertDoesNotExist()

        tap("Other")
        field("Name").performTextInput("Coco")
        tap("Save")

        field("Say which species it is").assertIsDisplayed()
        assertTrue(pets.created.isEmpty())

        field("Which species?").performTextInput("Hurón")
        tap("Save")
        composeRule.waitForIdle()

        assertEquals("Hurón", pets.created.single().speciesOther)
    }

    @Test
    fun the_form_says_that_the_microchip_is_private() {
        showForm()

        field("Only you can see this").assertIsDisplayed()
    }

    @Test
    fun the_first_pet_step_can_be_skipped() {
        showForm(onboarding = true)

        composeRule.onNodeWithText("Add your first pet").assertIsDisplayed()
        composeRule.onNodeWithText("Not now").performClick()

        assertEquals(1, skipped)
    }
}
