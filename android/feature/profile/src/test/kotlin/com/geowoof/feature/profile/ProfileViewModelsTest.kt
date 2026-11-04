package com.geowoof.feature.profile

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.geowoof.core.domain.ValidateDisplayNameUseCase
import com.geowoof.core.domain.ValidateProfileUseCase
import com.geowoof.core.model.AppError
import com.geowoof.core.model.DisplayNameIssue
import com.geowoof.core.model.MediaPurpose
import com.geowoof.core.model.Outcome
import com.geowoof.core.model.RemoteImage
import com.geowoof.core.model.TextIssue
import com.geowoof.core.testing.FakeMediaRepository
import com.geowoof.core.testing.FakePetsRepository
import com.geowoof.core.testing.FakeProfileRepository
import com.geowoof.core.testing.MainDispatcherRule
import com.geowoof.core.testing.TestPets
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ProfileViewModelsTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val photo = RemoteImage("old-photo", "thumb", "medium")
    private val profiles =
        FakeProfileRepository(TestPets.profile(displayName = "Ana", bio = "Hola", city = "Madrid", avatar = photo))
    private val pets = FakePetsRepository(listOf(TestPets.pet()))
    private val media = FakeMediaRepository()

    private fun editor() = EditProfileViewModel(profiles, media, ValidateProfileUseCase(ValidateDisplayNameUseCase()))

    @Test
    fun the_profile_screen_refreshes_and_shows_what_room_has() = runTest {
        val viewModel = ProfileViewModel(profiles, pets)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        assertEquals("Ana", viewModel.uiState.value.profile?.displayName)
        assertEquals(listOf("Luna"), viewModel.uiState.value.pets.map { it.name })
        assertEquals(1, profiles.refreshes)
        assertEquals(listOf("refreshMyPets"), pets.calls)
        assertFalse(viewModel.uiState.value.isRefreshing)
    }

    @Test
    fun a_failed_refresh_keeps_the_saved_data_and_explains_the_problem() = runTest {
        pets.refreshResult = Outcome.Failure(AppError.Network)

        val viewModel = ProfileViewModel(profiles, pets)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        assertEquals(AppError.Network, viewModel.uiState.value.error)
        assertEquals("Luna", viewModel.uiState.value.pets.single().name)

        viewModel.onErrorShown()
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun the_editor_starts_with_the_current_values() {
        val viewModel = editor()

        assertEquals("Ana", viewModel.displayName.text.toString())
        assertEquals("Hola", viewModel.bio.text.toString())
        assertEquals("Madrid", viewModel.city.text.toString())
        assertEquals(photo, viewModel.uiState.value.avatar)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun saving_without_a_new_photo_keeps_the_current_one() {
        val viewModel = editor()
        viewModel.displayName.setTextAndPlaceCursorAtEnd("Ana López")

        viewModel.save()

        assertEquals("old-photo", profiles.saved.single().avatarAssetId)
        assertEquals("Ana López", profiles.saved.single().displayName)
        assertTrue(media.uploads.isEmpty())
        assertTrue(viewModel.uiState.value.isSaved)
    }

    @Test
    fun a_new_photo_is_uploaded_before_saving() {
        val viewModel = editor()
        media.result = Outcome.Success("new-photo")

        viewModel.onImagePicked("content://photo")
        viewModel.save()

        assertEquals(listOf("content://photo" to MediaPurpose.UserAvatar), media.uploads)
        assertEquals("new-photo", profiles.saved.single().avatarAssetId)
    }

    @Test
    fun the_photo_can_be_removed() {
        val viewModel = editor()

        viewModel.removePhoto()
        viewModel.save()

        assertFalse(viewModel.uiState.value.hasPhoto)
        assertNull(profiles.saved.single().avatarAssetId)
    }

    @Test
    fun a_photo_that_cannot_be_uploaded_stops_the_saving() {
        val viewModel = editor()
        val error = AppError.Api(status = 422, code = "media.invalid_image")
        media.result = Outcome.Failure(error)

        viewModel.onImagePicked("content://not-a-photo")
        viewModel.save()

        assertEquals(error, viewModel.uiState.value.error)
        assertTrue(profiles.saved.isEmpty())
        assertFalse(viewModel.uiState.value.isSaving)
    }

    @Test
    fun invalid_fields_are_reported_without_saving() {
        val viewModel = editor()
        viewModel.displayName.setTextAndPlaceCursorAtEnd(" ")
        viewModel.bio.setTextAndPlaceCursorAtEnd("x".repeat(301))

        viewModel.save()

        assertEquals(DisplayNameIssue.Empty, viewModel.uiState.value.issues.displayName)
        assertEquals(TextIssue.TooLong, viewModel.uiState.value.issues.bio)
        assertTrue(profiles.saved.isEmpty())
    }

    @Test
    fun without_a_saved_profile_a_failed_download_is_explained() {
        val empty = FakeProfileRepository().apply { refreshResult = Outcome.Failure(AppError.Network) }

        val viewModel = EditProfileViewModel(empty, media, ValidateProfileUseCase(ValidateDisplayNameUseCase()))

        assertEquals(AppError.Network, viewModel.uiState.value.error)
    }
}
