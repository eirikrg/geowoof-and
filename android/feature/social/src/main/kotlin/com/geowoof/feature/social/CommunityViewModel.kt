package com.geowoof.feature.social

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.geowoof.core.data.repository.AuthRepository
import com.geowoof.core.data.repository.ContentChange
import com.geowoof.core.data.repository.ContentChanges
import com.geowoof.core.data.repository.PostsRepository
import com.geowoof.core.model.Post
import com.geowoof.core.model.SessionState
import com.geowoof.core.model.TimelineItem
import com.geowoof.feature.social.post.PostInteractions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** The feed comes from Room, which the repository keeps up to date, so a like only has to go to the repository. */
@HiltViewModel
class CommunityViewModel @Inject constructor(
    auth: AuthRepository,
    posts: PostsRepository,
    contentChanges: ContentChanges,
) : ViewModel() {
    val myUserId: String? = (auth.sessionState.value as? SessionState.SignedIn)?.user?.id

    /** Whom the person follows or has blocked, and what they post, decide what both lists have. */
    val changes: Flow<Int> = contentChanges.of(
        ContentChange.Relationships,
        ContentChange.Posts,
        ContentChange.Profile,
        ContentChange.Pets,
    )

    val feed: Flow<PagingData<TimelineItem>> = posts.feed().cachedIn(viewModelScope)

    val explore: Flow<PagingData<Post>> = posts.explore().cachedIn(viewModelScope)

    internal val interactions = PostInteractions(posts, viewModelScope)
}
