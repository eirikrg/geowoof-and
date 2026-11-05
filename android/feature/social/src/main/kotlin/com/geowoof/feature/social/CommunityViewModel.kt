package com.geowoof.feature.social

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.geowoof.core.data.repository.AuthRepository
import com.geowoof.core.data.repository.PostsRepository
import com.geowoof.core.model.SessionState
import com.geowoof.core.model.TimelineItem
import com.geowoof.feature.social.post.PostInteractions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** The feed comes from Room, which the repository keeps up to date, so a like only has to go to the repository. */
@HiltViewModel
class CommunityViewModel @Inject constructor(auth: AuthRepository, posts: PostsRepository) : ViewModel() {
    val myUserId: String? = (auth.sessionState.value as? SessionState.SignedIn)?.user?.id

    val feed: Flow<PagingData<TimelineItem>> = posts.feed().cachedIn(viewModelScope)

    internal val interactions = PostInteractions(posts, viewModelScope)
}
