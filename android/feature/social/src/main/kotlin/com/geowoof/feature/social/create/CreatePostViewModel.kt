package com.geowoof.feature.social.create

import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geowoof.core.data.repository.MediaRepository
import com.geowoof.core.data.repository.PeopleRepository
import com.geowoof.core.data.repository.PetsRepository
import com.geowoof.core.data.repository.PostsRepository
import com.geowoof.core.model.AppError
import com.geowoof.core.model.MediaPurpose
import com.geowoof.core.model.NewPost
import com.geowoof.core.model.Outcome
import com.geowoof.core.model.Pet
import com.geowoof.core.model.PostVisibility
import com.geowoof.feature.social.MentionSuggester
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/** A photo chosen for the post. Once it is uploaded it has an [assetId], so that retrying does not upload it again. */
data class ChosenPhoto(val contentUri: String, val assetId: String? = null)

data class CreatePostUiState(
    val photos: List<ChosenPhoto> = emptyList(),
    val visibility: PostVisibility = PostVisibility.Everyone,
    val pets: List<Pet> = emptyList(),
    val selectedPetIds: Set<String> = emptySet(),
    val isPublishing: Boolean = false,
    val error: AppError? = null,
    val isPublished: Boolean = false,
) {
    val canPublish: Boolean get() = photos.isNotEmpty() && !isPublishing
    val canAddPhotos: Boolean get() = photos.size < MAX_PHOTOS && !isPublishing

    companion object {
        const val MAX_PHOTOS = 10
    }
}

@HiltViewModel
class CreatePostViewModel @Inject constructor(
    private val posts: PostsRepository,
    private val media: MediaRepository,
    pets: PetsRepository,
    people: PeopleRepository,
) : ViewModel() {
    /** The same one in each attempt: if an answer is lost, repeating it does not publish twice. */
    private val postId = UUID.randomUUID().toString()
    private val form = MutableStateFlow(CreatePostUiState())

    val text = TextFieldState()

    internal val suggester = MentionSuggester(text, people, viewModelScope)

    val uiState: StateFlow<CreatePostUiState> = combine(form, pets.myPets) { current, myPets ->
        current.copy(pets = myPets)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), CreatePostUiState())

    fun onAddPhotos(contentUris: List<String>) = form.update { current ->
        val added = contentUris.distinct()
            .filter { uri -> current.photos.none { it.contentUri == uri } }
            .map { ChosenPhoto(it) }
        current.copy(photos = (current.photos + added).take(CreatePostUiState.MAX_PHOTOS))
    }

    fun onRemovePhoto(contentUri: String) = form.update { current ->
        current.copy(photos = current.photos.filterNot { it.contentUri == contentUri })
    }

    fun onSelectVisibility(visibility: PostVisibility) = form.update { it.copy(visibility = visibility) }

    fun onTogglePet(petId: String) = form.update { current ->
        val selected = current.selectedPetIds
        current.copy(selectedPetIds = if (petId in selected) selected - petId else selected + petId)
    }

    fun onErrorShown() = form.update { it.copy(error = null) }

    /** Uploads what is missing and publishes. If something fails, what was uploaded is kept for the next attempt. */
    fun onPublish() {
        if (!form.value.canPublish) return
        form.update { it.copy(isPublishing = true, error = null) }
        viewModelScope.launch {
            val assetIds = uploadPhotos()
            if (assetIds == null) {
                form.update { it.copy(isPublishing = false) }
                return@launch
            }
            val current = form.value
            val post =
                NewPost(postId, text.text.toString(), current.visibility, assetIds, current.selectedPetIds.toList())
            when (val result = posts.publish(post)) {
                is Outcome.Success -> form.update { it.copy(isPublishing = false, isPublished = true) }
                is Outcome.Failure -> form.update { it.copy(isPublishing = false, error = result.error) }
            }
        }
    }

    private suspend fun uploadPhotos(): List<String>? {
        form.value.photos.filter { it.assetId == null }.forEach { photo ->
            when (val result = media.uploadImage(photo.contentUri, MediaPurpose.PostImage)) {
                is Outcome.Success -> form.update { current ->
                    current.copy(
                        photos = current.photos.map {
                            if (it.contentUri == photo.contentUri) it.copy(assetId = result.value) else it
                        },
                    )
                }

                is Outcome.Failure -> {
                    form.update { it.copy(error = result.error) }
                    return null
                }
            }
        }
        return form.value.photos.mapNotNull { it.assetId }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
