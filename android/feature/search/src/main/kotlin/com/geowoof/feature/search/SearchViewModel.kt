package com.geowoof.feature.search

import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.geowoof.core.data.repository.ContentChange
import com.geowoof.core.data.repository.ContentChanges
import com.geowoof.core.data.repository.PeopleRepository
import com.geowoof.core.data.repository.RecentPeopleRepository
import com.geowoof.core.model.AppError
import com.geowoof.core.model.Outcome
import com.geowoof.core.model.PetSearchResult
import com.geowoof.core.model.Profile
import com.geowoof.core.model.UserSummary
import com.geowoof.core.ui.textChanges
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SearchTab { People, Pets }

data class SearchUiState(
    val tab: SearchTab = SearchTab.People,
    /** With less than this, the server does not search: the screen shows the recent people. */
    val hasEnoughText: Boolean = false,
    /** What the results are for: a new one is a new list, which loads from the start. */
    val searchedText: String = "",
    /** Those opened from here, the last one first. */
    val recent: List<UserSummary> = emptyList(),
    val isLoadingRecent: Boolean = false,
    val hasRecentFailed: Boolean = false,
)

private data class RecentPeople(
    val people: List<UserSummary> = emptyList(),
    val isLoading: Boolean = false,
    val hasFailed: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val people: PeopleRepository,
    private val recents: RecentPeopleRepository,
    contentChanges: ContentChanges,
) : ViewModel() {
    val query = TextFieldState()

    /** Who can be found, with which name, and their pets. */
    val changes: Flow<Int> = contentChanges.of(ContentChange.Relationships, ContentChange.Profile, ContentChange.Pets)

    private val tab = MutableStateFlow(SearchTab.People)
    private val reloads = MutableStateFlow(0)
    private var shownRecent = RecentPeople()
    private var loadedReload = -1

    /** Eagerly, so that what was typed is not lost while the screen is not on top. */
    private val text: StateFlow<String> = query.textChanges()
        .map { it.trim() }
        .debounce(DEBOUNCE_MILLIS)
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val users: Flow<PagingData<UserSummary>> = results(people::searchUsers).cachedIn(viewModelScope)

    val pets: Flow<PagingData<PetSearchResult>> = results(people::searchPets).cachedIn(viewModelScope)

    /** Removing someone from the list needs no server: what is already known of the others is still good. */
    private val recent: Flow<RecentPeople> = combine(recents.ids, reloads, ::Pair).transformLatest { (ids, reload) ->
        val known = ids.mapNotNull { id -> shownRecent.people.find { it.id == id } }
        val isUpToDate = reload == loadedReload && known.size == ids.size
        show(RecentPeople(known, isLoading = !isUpToDate && known.isEmpty() && ids.isNotEmpty()))
        if (!isUpToDate) loadRecent(ids, reload)
    }

    val uiState: StateFlow<SearchUiState> = combine(tab, text, recent) { selected, current, recentPeople ->
        SearchUiState(
            tab = selected,
            hasEnoughText = current.length >= MIN_TEXT_LENGTH,
            searchedText = current,
            recent = recentPeople.people,
            isLoadingRecent = recentPeople.isLoading,
            hasRecentFailed = recentPeople.hasFailed,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SearchUiState())

    private fun <T : Any> results(search: (String) -> Flow<PagingData<T>>): Flow<PagingData<T>> =
        text.flatMapLatest { current ->
            if (current.length < MIN_TEXT_LENGTH) flowOf(PagingData.empty(NOTHING_TO_LOAD)) else search(current)
        }

    /** The server decides who can still be seen: whoever it no longer knows, such as after a block, leaves the list. */
    private suspend fun FlowCollector<RecentPeople>.loadRecent(ids: List<String>, reload: Int) {
        val answers = coroutineScope { ids.map { id -> async { id to people.profile(id) } }.awaitAll() }
        val found = answers.mapNotNull { (_, answer) -> (answer as? Outcome.Success)?.value?.toSummary() }
        val gone = answers.filter { (_, answer) -> answer.isNotFound() }.map { (id, _) -> id }
        loadedReload = reload
        show(RecentPeople(found, hasFailed = found.isEmpty() && gone.size < ids.size))
        gone.forEach { recents.remove(it) }
    }

    private suspend fun FlowCollector<RecentPeople>.show(state: RecentPeople) {
        shownRecent = state
        emit(state)
    }

    private fun Outcome<Profile>.isNotFound() =
        ((this as? Outcome.Failure)?.error as? AppError.Api)?.status == HTTP_NOT_FOUND

    private fun Profile.toSummary() = UserSummary(id, username.orEmpty(), displayName, city, avatar)

    fun onSelectTab(selected: SearchTab) {
        tab.value = selected
    }

    /** Coming back to the screen, or trying again: someone may have blocked the person or changed their name. */
    fun onReloadRecent() = reloads.update { it + 1 }

    fun onPersonOpened(userId: String) {
        viewModelScope.launch { recents.add(userId) }
    }

    fun onRemoveRecent(userId: String) {
        viewModelScope.launch { recents.remove(userId) }
    }

    fun onRemoveAllRecent() {
        viewModelScope.launch { recents.removeAll() }
    }

    private companion object {
        const val DEBOUNCE_MILLIS = 300L
        const val MIN_TEXT_LENGTH = 2
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val HTTP_NOT_FOUND = 404

        val NOTHING_TO_LOAD = LoadStates(
            refresh = LoadState.NotLoading(endOfPaginationReached = true),
            prepend = LoadState.NotLoading(endOfPaginationReached = true),
            append = LoadState.NotLoading(endOfPaginationReached = true),
        )
    }
}
