package com.geowoof.feature.profile

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geowoof.core.data.repository.MediaRepository
import com.geowoof.core.data.repository.ProfileRepository
import com.geowoof.core.domain.ValidateProfileUseCase
import com.geowoof.core.model.AppError
import com.geowoof.core.model.MediaPurpose
import com.geowoof.core.model.Outcome
import com.geowoof.core.model.ProfileDraft
import com.geowoof.core.model.ProfileIssues
import com.geowoof.core.model.RemoteImage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EditProfileUiState(
    val isLoading: Boolean = true,
    val avatar: RemoteImage? = null,
    val pickedImageUri: String? = null,
    val issues: ProfileIssues = ProfileIssues(),
    val isSaving: Boolean = false,
    val error: AppError? = null,
    /** The profile was saved; the screen then goes back. */
    val isSaved: Boolean = false,
) {
    val hasPhoto: Boolean get() = avatar != null || pickedImageUri != null
}

@HiltViewModel
class EditProfileViewModel @Inject constructor(
    private val profiles: ProfileRepository,
    private val media: MediaRepository,
    private val validate: ValidateProfileUseCase,
) : ViewModel() {
    val displayName = TextFieldState()
    val bio = TextFieldState()
    val city = TextFieldState()

    private val _uiState = MutableStateFlow(EditProfileUiState())
    val uiState: StateFlow<EditProfileUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { load() }
    }

    fun onImagePicked(contentUri: String) = _uiState.update { it.copy(pickedImageUri = contentUri, error = null) }

    fun removePhoto() = _uiState.update { it.copy(avatar = null, pickedImageUri = null) }

    fun save() {
        val state = _uiState.value
        if (state.isSaving || state.isLoading) return
        val issues = validate(draft(state.avatar?.assetId))
        _uiState.update { it.copy(issues = issues, error = null, isSaving = issues.isValid) }
        if (!issues.isValid) return

        viewModelScope.launch {
            val avatarId = when (val upload = uploadPicked(state.pickedImageUri)) {
                is Outcome.Success -> upload.value ?: state.avatar?.assetId
                is Outcome.Failure -> return@launch fail(upload.error)
            }
            when (val saved = profiles.save(draft(avatarId))) {
                is Outcome.Success -> _uiState.update { it.copy(isSaving = false, isSaved = true) }
                is Outcome.Failure -> fail(saved.error)
            }
        }
    }

    private suspend fun load() {
        val refreshed = profiles.refresh()
        val profile = profiles.myProfile.first()
        if (profile != null) {
            displayName.setTextAndPlaceCursorAtEnd(profile.displayName)
            bio.setTextAndPlaceCursorAtEnd(profile.bio.orEmpty())
            city.setTextAndPlaceCursorAtEnd(profile.city.orEmpty())
        }

        // Nothing to edit without a profile. With a saved copy, the form works even if the refresh failed.
        _uiState.update {
            it.copy(
                isLoading = false,
                avatar = profile?.avatar,
                error = (refreshed as? Outcome.Failure)?.error.takeIf { profile == null },
            )
        }
    }

    private suspend fun uploadPicked(uri: String?): Outcome<String?> {
        if (uri == null) return Outcome.Success(null)
        return when (val uploaded = media.uploadImage(uri, MediaPurpose.UserAvatar)) {
            is Outcome.Success -> Outcome.Success(uploaded.value)
            is Outcome.Failure -> uploaded
        }
    }

    private fun draft(avatarId: String?) = ProfileDraft(
        displayName = displayName.text.toString(),
        bio = bio.text.toString(),
        city = city.text.toString(),
        avatarAssetId = avatarId,
    )

    private fun fail(error: AppError) = _uiState.update { it.copy(isSaving = false, error = error) }
}
