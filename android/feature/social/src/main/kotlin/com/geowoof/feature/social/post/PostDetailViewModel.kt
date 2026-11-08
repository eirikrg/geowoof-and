package com.geowoof.feature.social.post

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.geowoof.core.data.repository.AuthRepository
import com.geowoof.core.data.repository.ContentChange
import com.geowoof.core.data.repository.ContentChanges
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

/** Whom the field answers: the reply goes to the thread of [threadId], and starts with the mention of [username]. */
data class ReplyTarget(val threadId: String, val username: String) {
    val mention: String get() = "@$username "
}

/** The replies of a comment that were opened, page by page: [nextCursor] says that there are more. */
data class OpenThread(
    val replies: List<Comment> = emptyList(),
    val nextCursor: String? = null,
    val isLoading: Boolean = false,
    val failed: Boolean = false,
)

data class PostDetailUiState(
    val isLoading: Boolean = true,
    val post: Post? = null,
    /** It does not exist, or this person cannot see it: the server does not say which. */
    val isUnavailable: Boolean = false,
    val loadError: AppError? = null,
    val isSendingComment: Boolean = false,
    /** Deleted: there is nothing left to show here. */
    val isFinished: Boolean = false,
    val replyingTo: ReplyTarget? = null,
    /** By the comment of the post whose replies they are. The others show the first ones it brings. */
    val openThreads: Map<String, OpenThread> = emptyMap(),
)

/** A post with its comments. It comes from the server: it can come from anywhere, not only from the feed. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PostDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    auth: AuthRepository,
    private val posts: PostsRepository,
    people: PeopleRepository,
    contentChanges: ContentChanges,
) : ViewModel() {
    private val postId: String = checkNotNull(savedStateHandle["postId"])
    private val state = MutableStateFlow(PostDetailUiState())
    private val commentReloads = MutableStateFlow(0)

    val myUserId: String? = (auth.sessionState.value as? SessionState.SignedIn)?.user?.id

    val uiState: StateFlow<PostDetailUiState> = state.asStateFlow()

    /** A block hides the post or a comment, and the names and the pets are shown in both. */
    val changes: Flow<Int> = contentChanges.of(ContentChange.Relationships, ContentChange.Profile, ContentChange.Pets)

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
        onCommentDeleted = { comment ->
            state.update { current ->
                current.copy(
                    post = current.post?.withCommentCountChangedBy(-1),
                    openThreads = current.openThreads.changing(comment.threadId) { thread ->
                        thread.copy(replies = thread.replies.filterNot { it.id == comment.id })
                    },
                )
            }
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
        val threadId = state.value.replyingTo?.threadId
        state.update { it.copy(isSendingComment = true) }
        viewModelScope.launch {
            when (val result = posts.addComment(postId, text, threadId)) {
                is Outcome.Success -> {
                    commentText.clearText()
                    state.update { it.copy(post = it.post?.withCommentCountChangedBy(1), replyingTo = null) }
                    if (threadId != null) showReply(threadId, result.value)
                    commentReloads.update { it + 1 }
                }

                is Outcome.Failure -> commentError.value = result.error
            }
            state.update { it.copy(isSendingComment = false) }
        }
    }

    /**
     * The field answers [comment], in its thread, and starts with its mention, which is what tells that person. A
     * mention left by the previous answer goes, but nothing that was written.
     */
    fun onReply(comment: Comment) {
        if (comment.isDeleted) return
        val target = ReplyTarget(comment.threadId, comment.author.username)
        val written = commentText.text.toString().removePrefix(state.value.replyingTo?.mention.orEmpty())
        commentText.setTextAndPlaceCursorAtEnd(target.mention + written.removePrefix(target.mention))
        state.update { it.copy(replyingTo = target) }
    }

    fun onCancelReply() {
        val mention = state.value.replyingTo?.mention ?: return
        commentText.setTextAndPlaceCursorAtEnd(commentText.text.toString().removePrefix(mention))
        state.update { it.copy(replyingTo = null) }
    }

    fun onOpenReplies(commentId: String) {
        if (state.value.openThreads[commentId] != null) return
        state.update { it.copy(openThreads = it.openThreads + (commentId to OpenThread(isLoading = true))) }
        loadReplies(commentId, cursor = null)
    }

    /** The next page, or the first again if it could not be loaded. */
    fun onMoreReplies(commentId: String) {
        val thread = state.value.openThreads[commentId] ?: return
        if (thread.isLoading) return
        state.update { current ->
            current.copy(
                openThreads = current.openThreads.changing(commentId) {
                    it.copy(isLoading = true, failed = false)
                },
            )
        }
        loadReplies(commentId, cursor = thread.nextCursor.takeUnless { thread.failed && thread.replies.isEmpty() })
    }

    fun onHideReplies(commentId: String) {
        state.update { it.copy(openThreads = it.openThreads - commentId) }
    }

    private fun loadReplies(commentId: String, cursor: String?) {
        viewModelScope.launch {
            val result = posts.replies(postId, commentId, cursor)
            state.update { current ->
                current.copy(
                    openThreads = current.openThreads.changing(commentId) { thread ->
                        when (result) {
                            is Outcome.Success -> thread.copy(
                                replies = (thread.replies + result.value.items).inOrder(),
                                nextCursor = result.value.nextCursor,
                                isLoading = false,
                                failed = false,
                            )

                            is Outcome.Failure -> thread.copy(isLoading = false, failed = true)
                        }
                    },
                )
            }
        }
    }

    /**
     * A reply that was just sent is seen at once: its thread opens, with the reply at its place, and the first page
     * comes as well if it was not open.
     */
    private fun showReply(threadId: String, reply: Comment) {
        val wasOpen = state.value.openThreads[threadId] != null
        state.update { current ->
            val thread = current.openThreads[threadId] ?: OpenThread(isLoading = true)
            current.copy(
                openThreads =
                    current.openThreads + (threadId to thread.copy(replies = (thread.replies + reply).inOrder())),
            )
        }
        if (!wasOpen) loadReplies(threadId, cursor = null)
    }

    fun onCommentFailureShown() {
        commentError.value = null
    }

    private fun Post.withCommentCountChangedBy(change: Int) = copy(commentCount = commentCount + change)

    private fun Map<String, OpenThread>.changing(commentId: String, change: (OpenThread) -> OpenThread) =
        get(commentId)?.let { this + (commentId to change(it)) } ?: this

    /** Pages and the replies sent from here can bring the same one twice. */
    private fun List<Comment>.inOrder() = distinctBy { it.id }.sortedWith(compareBy({ it.createdAt }, { it.id }))

    private fun AppError.isNotFound() = this is AppError.Api && status == HTTP_NOT_FOUND

    private companion object {
        const val HTTP_NOT_FOUND = 404
    }
}
