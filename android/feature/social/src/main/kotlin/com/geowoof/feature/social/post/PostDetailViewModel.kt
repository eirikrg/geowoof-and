package com.geowoof.feature.social.post

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.geowoof.core.data.repository.AuthRepository
import com.geowoof.core.data.repository.PeopleRepository
import com.geowoof.core.data.repository.PostsRepository
import com.geowoof.core.model.AppError
import com.geowoof.core.model.Comment
import com.geowoof.core.model.Outcome
import com.geowoof.core.model.Post
import com.geowoof.core.model.SessionState
import com.geowoof.feature.social.MentionSuggester
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PostDetailUiState(
    val isLoading: Boolean = true,
    val post: Post? = null,
    /** It does not exist, or this person cannot see it: the server does not say which. */
    val isUnavailable: Boolean = false,
    val loadError: AppError? = null,
    val isSendingComment: Boolean = false,
    /** Deleted: there is nothing left to show here. */
    val isFinished: Boolean = false,
)

/** A post with its comments. It comes from the server: it can come from anywhere, not only from the feed. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PostDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    auth: AuthRepository,
    private val posts: PostsRepository,
    people: PeopleRepository,
) : ViewModel() {
    private val postId: String = checkNotNull(savedStateHandle["postId"])
    private val state = MutableStateFlow(PostDetailUiState())
    private val commentReloads = MutableStateFlow(0)

    val myUserId: String? = (auth.sessionState.value as? SessionState.SignedIn)?.user?.id

    val uiState: StateFlow<PostDetailUiState> = state.asStateFlow()

    val commentText = TextFieldState()

    internal val suggester = MentionSuggester(commentText, people, viewModelScope)

    /** Adding or deleting one reloads the list, from the first page. */
    val comments: Flow<PagingData<Comment>> =
        commentReloads.flatMapLatest { posts.comments(postId) }.cachedIn(viewModelScope)

    internal val interactions = PostInteractions(
        posts = posts,
        scope = viewModelScope,
        onChange = { post -> state.update { it.copy(post = post) } },
        onDeleted = { state.update { it.copy(isFinished = true) } },
        onCommentDeleted = {
            state.update { it.copy(post = it.post?.withCommentCountChangedBy(-1)) }
            commentReloads.update { it + 1 }
        },
    )

    private val commentError = MutableStateFlow<AppError?>(null)

    /** Whether a comment could not be sent, which keeps what was written in the field. */
    val commentFailure: StateFlow<AppError?> = commentError.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        state.update { it.copy(isLoading = true, loadError = null) }
        viewModelScope.launch {
            when (val result = posts.post(postId)) {
                is Outcome.Success -> state.update {
                    it.copy(isLoading = false, post = result.value, isUnavailable = false)
                }

                is Outcome.Failure -> state.update {
                    val isGone = result.error.isNotFound()
                    it.copy(
                        isLoading = false,
                        isUnavailable = isGone,
                        loadError = result.error.takeUnless { isGone },
                    )
                }
            }
        }
    }

    fun onSendComment() {
        val text = commentText.text.toString()
        if (text.isBlank() || state.value.isSendingComment) return
        state.update { it.copy(isSendingComment = true) }
        viewModelScope.launch {
            when (val result = posts.addComment(postId, text)) {
                is Outcome.Success -> {
                    commentText.clearText()
                    state.update { it.copy(post = it.post?.withCommentCountChangedBy(1)) }
                    commentReloads.update { it + 1 }
                }

                is Outcome.Failure -> commentError.value = result.error
            }
            state.update { it.copy(isSendingComment = false) }
        }
    }

    fun onCommentFailureShown() {
        commentError.value = null
    }

    private fun Post.withCommentCountChangedBy(change: Int) = copy(commentCount = commentCount + change)

    private fun AppError.isNotFound() = this is AppError.Api && status == HTTP_NOT_FOUND

    private companion object {
        const val HTTP_NOT_FOUND = 404
    }
}
