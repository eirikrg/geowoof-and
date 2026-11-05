package com.geowoof.feature.pets

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.core.model.WeightEntry
import com.geowoof.core.testing.TestPets
import com.geowoof.feature.pets.detail.PetDetailActions
import com.geowoof.feature.pets.detail.PetDetailContent
import com.geowoof.feature.pets.detail.PetDetailUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class PetDetailScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val actions = mutableListOf<String>()

    private fun show(uiState: PetDetailUiState) {
        composeRule.setContent {
            GeoWoofTheme(dynamicColor = false) {
                PetDetailContent(
                    uiState = uiState,
                    weightText = TextFieldState(),
                    actions = PetDetailActions(
                        onRefresh = { actions += "refresh" },
                        onErrorShown = { actions += "errorShown" },
                        onAddWeight = { actions += "addWeight" },
                        onEditWeight = { actions += "editWeight:${it.id}" },
                        onWeightDateChange = {},
                        onSaveWeight = {},
                        onDeleteWeight = {},
                        onDismissWeight = {},
                        onRequestDelete = { actions += "delete" },
                        onConfirmDelete = { actions += "confirmDelete" },
                        onDismissDelete = { actions += "dismissDelete" },
                    ),
                    onBack = { actions += "back" },
                    onEdit = { actions += "edit:$it" },
                    onOpenWalks = { actions += "walks" },
                )
            }
        }
    }

    private fun scrollTo(text: String) {
        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text))
    }

    private fun weight(id: String, day: Int, kg: Double) = WeightEntry(id, "luna", LocalDate.of(2026, 1, day), kg)

    @Test
    fun shows_the_data_of_the_pet() {
        show(PetDetailUiState(pet = TestPets.pet()))

        composeRule.onNodeWithText("Border collie").assertIsDisplayed()
        composeRule.onNodeWithText("Le encanta el agua").assertIsDisplayed()
        scrollTo("941000024567890")
        composeRule.onNodeWithText("941000024567890").assertIsDisplayed()
        composeRule.onNodeWithText("Only you can see this").assertIsDisplayed()
    }

    @Test
    fun other_people_do_not_get_a_microchip_row() {
        show(PetDetailUiState(pet = TestPets.pet(microchip = null)))

        composeRule.onNodeWithText("Microchip").assertDoesNotExist()
    }

    @Test
    fun two_measurements_draw_the_chart_and_each_one_can_be_edited() {
        show(PetDetailUiState(pet = TestPets.pet(), weights = listOf(weight("a", 1, 18.0), weight("b", 2, 18.5))))

        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(
            hasContentDescription("Weight chart: from 18.0 kg to 18.5 kg"),
        )
        composeRule.onNodeWithContentDescription(
            "Weight chart: from 18.0 kg to 18.5 kg",
        ).assertExists()

        scrollTo("18.5 kg")
        composeRule.onNodeWithText("18.5 kg").performClick()
        assertEquals(listOf("editWeight:b"), actions)
    }

    @Test
    fun one_measurement_is_not_enough_for_a_chart() {
        show(PetDetailUiState(pet = TestPets.pet(), weights = listOf(weight("a", 1, 18.0))))

        scrollTo("18.0 kg")
        composeRule.onNodeWithText("18.0 kg").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Weight chart", substring = true).assertDoesNotExist()
    }

    @Test
    fun invites_to_record_the_first_weight() {
        show(PetDetailUiState(pet = TestPets.pet()))

        scrollTo("Add weight")
        composeRule.onNodeWithText("Add weight").performClick()

        assertEquals(listOf("addWeight"), actions)
    }

    @Test
    fun deleting_asks_for_confirmation() {
        show(PetDetailUiState(pet = TestPets.pet(), isConfirmingDelete = true))

        composeRule.onNodeWithText("Delete Luna?").assertIsDisplayed()
        composeRule.onNodeWithText("Delete").performClick()

        assertEquals(listOf("confirmDelete"), actions)
    }

    @Test
    fun can_be_edited_and_deleted_from_the_top_bar() {
        show(PetDetailUiState(pet = TestPets.pet()))

        composeRule.onNodeWithContentDescription("Edit pet").performClick()
        composeRule.onNodeWithContentDescription("Delete").performClick()

        assertEquals(listOf("edit:luna", "delete"), actions)
    }
}
