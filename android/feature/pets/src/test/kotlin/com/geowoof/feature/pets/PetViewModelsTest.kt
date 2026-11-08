package com.geowoof.feature.pets

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.SavedStateHandle
import com.geowoof.core.domain.ParseWeightUseCase
import com.geowoof.core.domain.ValidatePetUseCase
import com.geowoof.core.model.AppError
import com.geowoof.core.model.MediaPurpose
import com.geowoof.core.model.Outcome
import com.geowoof.core.model.PetDateIssue
import com.geowoof.core.model.PetNameIssue
import com.geowoof.core.model.PetSex
import com.geowoof.core.model.PetSize
import com.geowoof.core.model.PetSpecies
import com.geowoof.core.model.RemoteImage
import com.geowoof.core.model.SpeciesOtherIssue
import com.geowoof.core.model.WeightEntry
import com.geowoof.core.model.WeightIssue
import com.geowoof.core.testing.FakeHealthRepository
import com.geowoof.core.testing.FakeMediaRepository
import com.geowoof.core.testing.FakePetsRepository
import com.geowoof.core.testing.FakeWalksRepository
import com.geowoof.core.testing.MainDispatcherRule
import com.geowoof.core.testing.TestPets
import com.geowoof.feature.pets.detail.PetDetailViewModel
import com.geowoof.feature.pets.editor.PetEditorViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class PetViewModelsTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val photo = RemoteImage("old-photo", "thumb", "medium")
    private val luna = TestPets.pet(avatar = photo)
    private val pets = FakePetsRepository(listOf(luna))
    private val health = FakeHealthRepository()
    private val media = FakeMediaRepository()

    private fun newPet() = PetEditorViewModel(SavedStateHandle(), pets, media, ValidatePetUseCase())

    private fun editLuna() =
        PetEditorViewModel(SavedStateHandle(mapOf("petId" to "luna")), pets, media, ValidatePetUseCase())

    private fun TestScope.detail(): PetDetailViewModel {
        val viewModel =
            PetDetailViewModel(
                SavedStateHandle(mapOf("petId" to "luna")),
                pets,
                FakeWalksRepository(),
                health,
                ParseWeightUseCase(),
            )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        return viewModel
    }

    @Test
    fun a_new_pet_starts_empty_and_is_created() {
        val viewModel = newPet()
        assertFalse(viewModel.uiState.value.isEditing)
        viewModel.name.setTextAndPlaceCursorAtEnd("Toby")
        viewModel.breed.setTextAndPlaceCursorAtEnd("Teckel")
        viewModel.onSexChange(PetSex.Male)
        viewModel.onSizeChange(PetSize.Small)
        viewModel.onNeuteredChange(true)
        viewModel.onBirthDateChange(LocalDate.of(2023, 9, 1))

        viewModel.save()

        val draft = pets.created.single()
        assertEquals("Toby", draft.name)
        assertEquals("Teckel", draft.breed)
        assertEquals(PetSex.Male, draft.sex)
        assertEquals(PetSize.Small, draft.size)
        assertTrue(draft.neutered)
        assertEquals(LocalDate.of(2023, 9, 1), draft.birthDate)
        assertNull(draft.avatarAssetId)
        assertTrue(viewModel.uiState.value.isDone)
    }

    @Test
    fun tapping_the_selected_size_clears_it() {
        val viewModel = newPet()

        viewModel.onSizeChange(PetSize.Large)
        assertEquals(PetSize.Large, viewModel.uiState.value.size)

        viewModel.onSizeChange(PetSize.Large)
        assertNull(viewModel.uiState.value.size)
    }

    @Test
    fun invalid_fields_are_reported_without_saving() {
        val viewModel = newPet()
        viewModel.onSpeciesChange(PetSpecies.Other)
        viewModel.onBirthDateChange(LocalDate.now().plusDays(1))

        viewModel.save()

        val issues = viewModel.uiState.value.issues
        assertEquals(PetNameIssue.Empty, issues.name)
        assertEquals(SpeciesOtherIssue.Empty, issues.speciesOther)
        assertEquals(PetDateIssue.InTheFuture, issues.birthDate)
        assertFalse(viewModel.uiState.value.isSaving)
        assertTrue(pets.created.isEmpty())
    }

    @Test
    fun editing_starts_with_the_current_values_and_keeps_the_photo() {
        val viewModel = editLuna()

        assertTrue(viewModel.uiState.value.isEditing)
        assertEquals("Luna", viewModel.name.text.toString())
        assertEquals("941000024567890", viewModel.microchip.text.toString())
        assertEquals(LocalDate.of(2021, 4, 12), viewModel.uiState.value.birthDate)

        viewModel.name.setTextAndPlaceCursorAtEnd("Lunita")
        viewModel.save()

        val (id, draft) = pets.updated.single()
        assertEquals("luna", id)
        assertEquals("Lunita", draft.name)
        assertEquals("old-photo", draft.avatarAssetId)
        assertTrue(pets.created.isEmpty())
        assertTrue(media.uploads.isEmpty())
    }

    @Test
    fun a_picked_photo_is_uploaded_before_saving() {
        val viewModel = editLuna()
        media.result = Outcome.Success("new-photo")

        viewModel.onImagePicked("content://photo")
        viewModel.save()

        assertEquals(listOf("content://photo" to MediaPurpose.PetAvatar), media.uploads)
        assertEquals("new-photo", pets.updated.single().second.avatarAssetId)
    }

    @Test
    fun the_photo_can_be_removed() {
        val viewModel = editLuna()

        viewModel.removePhoto()
        viewModel.save()

        assertFalse(viewModel.uiState.value.hasPhoto)
        assertNull(pets.updated.single().second.avatarAssetId)
    }

    @Test
    fun a_photo_that_cannot_be_uploaded_keeps_the_form_open() {
        val viewModel = editLuna()
        val error = AppError.Api(status = 422, code = "media.invalid_image")
        media.result = Outcome.Failure(error)

        viewModel.onImagePicked("content://not-a-photo")
        viewModel.save()

        assertEquals(error, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isSaving)
        assertFalse(viewModel.uiState.value.isDone)
        assertTrue(pets.updated.isEmpty())
    }

    @Test
    fun a_rejected_save_is_explained() {
        val viewModel = newPet()
        val error = AppError.Api(status = 422, code = "pets.limit_reached")
        pets.writeFailure = error
        viewModel.name.setTextAndPlaceCursorAtEnd("Toby")

        viewModel.save()

        assertEquals(error, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isDone)
    }

    @Test
    fun editing_a_pet_that_no_longer_exists_is_explained() {
        val viewModel =
            PetEditorViewModel(SavedStateHandle(mapOf("petId" to "gone")), pets, media, ValidatePetUseCase())

        assertEquals(AppError.Api(status = 404, code = "pets.not_found"), viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun the_detail_refreshes_the_pet_and_its_weights() = runTest {
        val viewModel = detail()

        assertEquals(listOf("refreshPet:luna", "refreshWeights:luna"), pets.calls)
        assertEquals("Luna", viewModel.uiState.value.pet?.name)
        assertFalse(viewModel.uiState.value.isRefreshing)
    }

    @Test
    fun a_failed_refresh_keeps_what_room_has() = runTest {
        pets.refreshResult = Outcome.Failure(AppError.Network)

        val viewModel = detail()

        assertEquals(AppError.Network, viewModel.uiState.value.error)
        assertEquals("Luna", viewModel.uiState.value.pet?.name)
    }

    @Test
    fun a_weight_is_added_for_today_by_default() = runTest {
        val viewModel = detail()
        val today = LocalDate.of(2026, 11, 5)

        viewModel.openAddWeight(today)
        assertEquals(today, viewModel.uiState.value.weightEditor?.measuredOn)
        viewModel.weightText.setTextAndPlaceCursorAtEnd("18,4")
        viewModel.saveWeight()

        assertNull(viewModel.uiState.value.weightEditor)
        val entry = pets.observeWeights("luna").first().single()
        assertEquals(18.4, entry.weightKg, 0.0)
        assertEquals(today, entry.measuredOn)
    }

    @Test
    fun a_wrong_weight_is_explained_and_keeps_the_dialog_open() = runTest {
        val viewModel = detail()
        viewModel.openAddWeight()

        viewModel.weightText.setTextAndPlaceCursorAtEnd("mucho")
        viewModel.saveWeight()

        assertEquals(WeightIssue.NotANumber, viewModel.uiState.value.weightEditor?.weightIssue)
        assertTrue(pets.observeWeights("luna").first().isEmpty())
    }

    @Test
    fun a_second_weight_on_the_same_day_is_explained() = runTest {
        val viewModel = detail()
        val error = AppError.Api(status = 409, code = "pets.weight_date_taken")
        pets.weightFailure = error
        viewModel.openAddWeight()
        viewModel.weightText.setTextAndPlaceCursorAtEnd("18")

        viewModel.saveWeight()

        assertEquals(error, viewModel.uiState.value.weightEditor?.error)
        assertFalse(viewModel.uiState.value.weightEditor!!.isSaving)
    }

    @Test
    fun a_weight_can_be_corrected_and_deleted() = runTest {
        val date = LocalDate.of(2026, 1, 1)
        pets.setWeights("luna", listOf(WeightEntry("w", "luna", date, 18.0)))
        val viewModel = detail()
        val entry = viewModel.uiState.value.weights.single()

        viewModel.openEditWeight(entry)
        assertEquals("18.0", viewModel.weightText.text.toString())
        viewModel.weightText.setTextAndPlaceCursorAtEnd("18.5")
        viewModel.onWeightDateChange(date.plusDays(1))
        viewModel.saveWeight()

        val corrected = viewModel.uiState.value.weights.single()
        assertEquals(18.5, corrected.weightKg, 0.0)
        assertEquals(date.plusDays(1), corrected.measuredOn)

        viewModel.openEditWeight(corrected)
        viewModel.deleteWeight()

        assertTrue(viewModel.uiState.value.weights.isEmpty())
        assertNull(viewModel.uiState.value.weightEditor)
    }

    @Test
    fun deleting_a_pet_asks_first() = runTest {
        val viewModel = detail()

        viewModel.requestDelete()
        assertTrue(viewModel.uiState.value.isConfirmingDelete)
        viewModel.dismissDelete()
        assertFalse(viewModel.uiState.value.isConfirmingDelete)
        assertTrue(pets.deleted.isEmpty())

        viewModel.requestDelete()
        viewModel.confirmDelete()

        assertEquals(listOf("luna"), pets.deleted)
        assertTrue(viewModel.uiState.value.isDeleted)
    }

    @Test
    fun a_failed_deletion_keeps_the_pet_and_explains_it() = runTest {
        val viewModel = detail()
        pets.writeFailure = AppError.Network

        viewModel.requestDelete()
        viewModel.confirmDelete()

        assertEquals(AppError.Network, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isDeleted)
        assertNotNull(viewModel.uiState.value.pet)
    }
}
