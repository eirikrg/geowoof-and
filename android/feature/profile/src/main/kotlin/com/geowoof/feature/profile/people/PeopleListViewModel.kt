package com.geowoof.feature.profile.people

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.geowoof.core.data.repository.PeopleRepository
import com.geowoof.core.model.PeopleListKind
import com.geowoof.core.model.Person
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** The arguments of the route are read by the name of its properties. */
@HiltViewModel
class PeopleListViewModel @Inject constructor(savedStateHandle: SavedStateHandle, repository: PeopleRepository) :
    ViewModel() {
    val kind: PeopleListKind = PeopleListKind.valueOf(checkNotNull(savedStateHandle["kind"]))

    val people: Flow<PagingData<Person>> =
        repository.people(checkNotNull(savedStateHandle["userId"]), kind).cachedIn(viewModelScope)
}
