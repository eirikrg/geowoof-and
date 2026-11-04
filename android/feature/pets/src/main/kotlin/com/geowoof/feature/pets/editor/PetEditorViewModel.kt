package com.geowoof.feature.pets.editor

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geowoof.core.data.repository.MediaRepository
import com.geowoof.core.data.repository.PetsRepository
import com.geowoof.core.domain.ValidatePetUseCase
import com.geowoof.core.model.AppError
import com.geowoof.core.model.MediaPurpose
import com.geowoof.core.model.Outcome
import com.geowoof.core.model.Pet
import com.geowoof.core.model.PetDraft
import com.geowoof.core.model.PetIssues
import com.geowoof.core.model.PetSex
import com.geowoof.core.model.PetSize
import com.geowoof.core.model.PetSpecies
import com.geowoof.core.model.RemoteImage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class PetEditorUiState(
    val isEditing: Boolean = false,
    val isLoading: Boolean = false,
    val species: PetSpecies = PetSpecies.Dog,
    val sex: PetSex = PetSex.Unknown,
    val size: PetSize? = null,
    val neutered: Boolean = false,
    val birthDate: LocalDate? = null,
    val avatar: RemoteImage? = null,
    val pickedImageUri: String? = null,
    val issues: PetIssues = PetIssues(),
    val isSaving: Boolean = false,
    val error: AppError? = null,
    /** The pet was saved; the screen then moves on. */
    val isDone: Boolean = false,
) {
    val hasPhoto: Boolean get() = avatar != null || pickedImageUri != null
}

@HiltViewModel
class PetEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val pets: PetsRepository,
    private val media: MediaRepository,
    private val validate: ValidatePetUseCase,
) : ViewModel() {
    private val petId: String? = savedStateHandle["petId"]

    val name = TextFieldState()
    val speciesOther = TextFieldState()
    val breed = TextFieldState()
    val color = TextFieldState()
    val microchip = TextFieldState()
    val bio = TextFieldState()

    private val _uiState = MutableStateFlow(PetEditorUiState(isEditing = petId != null, isLoading = petId != null))
    val uiState: StateFlow<PetEditorUiState> = _uiState.asStateFlow()

    init {
        petId?.let { id -> viewModelScope.launch { load(id) } }
    }

    fun onSpeciesChange(species: PetSpecies) = _uiState.update { it.copy(species = species) }

    fun onSexChange(sex: PetSex) = _uiState.update { it.copy(sex = sex) }

    /** Tapping the selected size again clears it: the field is optional. */
    fun onSizeChange(size: PetSize) = _uiState.update { it.copy(size = size.takeIf { _ -> it.size != size }) }

    fun onNeuteredChange(neutered: Boolean) = _uiState.update { it.copy(neutered = neutered) }

    fun onBirthDateChange(date: LocalDate?) = _uiState.update { it.copy(birthDate = date) }

    fun onImagePicked(contentUri: String) = _uiState.update { it.copy(pickedImageUri = contentUri, error = null) }

    fun removePhoto() = _uiState.update { it.copy(avatar = null, pickedImageUri = null) }

    fun save() {
        val state = _uiState.value
        if (state.isSaving || state.isLoading) return
        val issues = validate(draft(state, state.avatar?.assetId))
        _uiState.update { it.copy(issues = issues, error = null, isSaving = issues.isValid) }
        if (!issues.isValid) return

        viewModelScope.launch {
            val avatarId = when (val upload = uploadPicked(state.pickedImageUri)) {
                is Outcome.Success -> upload.value ?: state.avatar?.assetId
                is Outcome.Failure -> return@launch fail(upload.error)
            }
            val saved = draft(state, avatarId).let { draft ->
                if (petId == null) pets.create(draft) else pets.update(petId, draft)
            }
            when (saved) {
                is Outcome.Success -> _uiState.update { it.copy(isSaving = false, isDone = true) }
                is Outcome.Failure -> fail(saved.error)
            }
        }
    }

    private suspend fun load(id: String) {
        val pet = pets.observePet(id).first()
        if (pet == null) {
            _uiState.update {
                it.copy(isLoading = false, error = AppError.Api(status = NOT_FOUND, code = PET_NOT_FOUND))
            }
        } else {
            fill(pet)
        }
    }

    private fun fill(pet: Pet) {
        name.setTextAndPlaceCursorAtEnd(pet.name)
        speciesOther.setTextAndPlaceCursorAtEnd(pet.speciesOther.orEmpty())
        breed.setTextAndPlaceCursorAtEnd(pet.breed.orEmpty())
        color.setTextAndPlaceCursorAtEnd(pet.color.orEmpty())
        microchip.setTextAndPlaceCursorAtEnd(pet.microchip.orEmpty())
        bio.setTextAndPlaceCursorAtEnd(pet.bio.orEmpty())
        _uiState.update {
            it.copy(
                isLoading = false,
                species = pet.species,
                sex = pet.sex,
                size = pet.size,
                neutered = pet.neutered,
                birthDate = pet.birthDate,
                avatar = pet.avatar,
            )
        }
    }

    private suspend fun uploadPicked(uri: String?): Outcome<String?> {
        if (uri == null) return Outcome.Success(null)
        return when (val uploaded = media.uploadImage(uri, MediaPurpose.PetAvatar)) {
            is Outcome.Success -> Outcome.Success(uploaded.value)
            is Outcome.Failure -> uploaded
        }
    }

    private fun draft(state: PetEditorUiState, avatarId: String?) = PetDraft(
        name = name.text.toString(),
        species = state.species,
        speciesOther = speciesOther.text.toString(),
        breed = breed.text.toString(),
        birthDate = state.birthDate,
        sex = state.sex,
        size = state.size,
        color = color.text.toString(),
        neutered = state.neutered,
        microchip = microchip.text.toString(),
        avatarAssetId = avatarId,
        bio = bio.text.toString(),
    )

    private fun fail(error: AppError) = _uiState.update { it.copy(isSaving = false, error = error) }

    private companion object {
        const val NOT_FOUND = 404
        const val PET_NOT_FOUND = "pets.not_found"
    }
}
