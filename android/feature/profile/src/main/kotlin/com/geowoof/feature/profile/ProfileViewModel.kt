package com.geowoof.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.geowoof.core.data.repository.AuthRepository
import com.geowoof.core.data.repository.PeopleRepository
import com.geowoof.core.data.repository.PetsRepository
import com.geowoof.core.data.repository.PostsRepository
import com.geowoof.core.data.repository.ProfileRepository
import com.geowoof.core.data.repository.SocialRepository
import com.geowoof.core.data.repository.WalksRepository
import com.geowoof.core.model.AppError
import com.geowoof.core.model.Outcome
import com.geowoof.core.model.Post
import com.geowoof.core.model.ProfileCounts
import com.geowoof.core.model.Relationship
import com.geowoof.core.model.RelationshipStatus
import com.geowoof.core.model.SessionState
import com.geowoof.core.model.TimelineItem
import com.geowoof.core.model.Walk
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Your own profile. What is yours comes from Room, and the numbers from the server. */
@Suppress("LongParameterList")
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val profiles: ProfileRepository,
    private val pets: PetsRepository,
    private val walksRepository: WalksRepository,
    private val people: PeopleRepository,
    private val social: SocialRepository,
    postsRepository: PostsRepository,
) : ViewModel() {
    private val status = MutableStateFlow(LoadStatus(isLoading = true))
    private var isLoadingNow = false

    val walks: Flow<PagingData<Walk>> = walksRepository.walks().cachedIn(viewModelScope)

    val posts: Flow<PagingData<TimelineItem>> =
        (myId()?.let { postsRepository.userPosts(it) } ?: flowOf(PagingData.empty())).cachedIn(viewModelScope)

    val saved: Flow<PagingData<Post>> = postsRepository.savedPosts().cachedIn(viewModelScope)

    val uiState: StateFlow<ProfileUiState> = combine(
        combine(profiles.myProfile, pets.myPets, walksRepository.observeTotals(), ::Triple),
        combine(social.receivedRequests, social.sentRequests, ::Pair),
        status,
    ) { (profile, myPets, totals), (received, sent), current ->
        ProfileUiState(
            isLoading = current.isLoading,
            profile = profile,
            relationship = OWN,
            counts = current.counts,
            totals = totals,
            pets = myPets,
            receivedRequests = received.size,
            sentRequests = sent.size,
            // With nothing on screen, a failure has to stay there, with its button to retry.
            loadError = current.error ?: current.lastFailure.takeIf { profile == null },
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        ProfileUiState(relationship = OWN),
    )

    init {
        refresh()
    }

    /** What Room already has stays on screen while this runs. */
    fun refresh() {
        status.update { it.copy(isLoading = true, error = null, lastFailure = null) }
        load()
    }

    /** Back from another screen, the numbers may have changed. It says nothing if it cannot refresh. */
    fun onResume() = load()

    fun onErrorShown() = status.update { it.copy(error = null) }

    private fun load() {
        if (isLoadingNow) return
        isLoadingNow = true
        viewModelScope.launch {
            val counts = async { myId()?.let { people.counts(it) } }
            val others = listOf(
                async { profiles.refresh() },
                async { pets.refreshMyPets() },
                async { walksRepository.refreshTotals() },
                async { social.refreshRequests() },
            )
            val countsResult = counts.await()
            val failure = (others.awaitAll() + listOfNotNull(countsResult))
                .filterIsInstance<Outcome.Failure>()
                .firstOrNull()
                ?.error
            status.update { current ->
                LoadStatus(
                    isLoading = false,
                    counts = (countsResult as? Outcome.Success)?.value ?: current.counts,
                    error = failure.takeIf { current.isLoading },
                    lastFailure = failure,
                )
            }
            isLoadingNow = false
        }
    }

    private fun myId(): String? = (auth.sessionState.value as? SessionState.SignedIn)?.user?.id

    private data class LoadStatus(
        val isLoading: Boolean = false,
        val counts: ProfileCounts? = null,
        /** What the person asked for failed, and it is said once. */
        val error: AppError? = null,
        /** The last load failed, also if it was silent. */
        val lastFailure: AppError? = null,
    )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        val OWN = Relationship(RelationshipStatus.Self)
    }
}
