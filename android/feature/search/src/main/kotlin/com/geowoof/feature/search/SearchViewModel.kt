package com.geowoof.feature.search

import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.geowoof.core.data.repository.PeopleRepository
import com.geowoof.core.model.PetSearchResult
import com.geowoof.core.model.UserSummary
import com.geowoof.core.ui.textChanges
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
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
import javax.inject.Inject

enum class SearchTab { People, Pets }

data class SearchUiState(
    val tab: SearchTab = SearchTab.People,
    /** With less than this, the server does not search: the screen asks to keep writing. */
    val hasEnoughText: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(private val people: PeopleRepository) : ViewModel() {
    val query = TextFieldState()

    private val tab = MutableStateFlow(SearchTab.People)

    /** Eagerly, so that what was typed is not lost while the screen is not on top. */
    private val text: StateFlow<String> = query.textChanges()
        .map { it.trim() }
        .debounce(DEBOUNCE_MILLIS)
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val users: Flow<PagingData<UserSummary>> = results(people::searchUsers).cachedIn(viewModelScope)

    val pets: Flow<PagingData<PetSearchResult>> = results(people::searchPets).cachedIn(viewModelScope)

    val uiState: StateFlow<SearchUiState> = combine(tab, text) { selected, current ->
        SearchUiState(selected, current.length >= MIN_TEXT_LENGTH)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SearchUiState())

    private fun <T : Any> results(search: (String) -> Flow<PagingData<T>>): Flow<PagingData<T>> =
        text.flatMapLatest { current ->
            if (current.length < MIN_TEXT_LENGTH) flowOf(PagingData.empty(NOTHING_TO_LOAD)) else search(current)
        }

    fun onSelectTab(selected: SearchTab) {
        tab.value = selected
    }

    private companion object {
        const val DEBOUNCE_MILLIS = 300L
        const val MIN_TEXT_LENGTH = 2
        const val STOP_TIMEOUT_MILLIS = 5_000L

        val NOTHING_TO_LOAD = LoadStates(
            refresh = LoadState.NotLoading(endOfPaginationReached = true),
            prepend = LoadState.NotLoading(endOfPaginationReached = true),
            append = LoadState.NotLoading(endOfPaginationReached = true),
        )
    }
}
