package com.geowoof.feature.social.post

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import com.geowoof.core.data.repository.PostsRepository
import com.geowoof.core.model.AppError
import com.geowoof.core.model.Comment
import com.geowoof.core.model.Outcome
import com.geowoof.core.model.Post
import com.geowoof.core.model.ReportReason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What is being asked about a post or a comment. */
sealed interface PostDialog {
    data class ConfirmDeletePost(val post: Post) : PostDialog

    data class ConfirmDeleteComment(val comment: Comment) : PostDialog

    data class ReportPost(val post: Post) : PostDialog

    data class ReportComment(val comment: Comment) : PostDialog
}

data class PostInteractionState(
    val dialog: PostDialog? = null,
    val reportReason: ReportReason? = null,
    val wasReported: Boolean = false,
    val error: AppError? = null,
)

/**
 * What people can do with a post, for the feed and for the screen of a post. The feed reads Room, which the
 * repository keeps up to date. The screen of a post has its own copy, so it hears about the changes with [onChange].
 */
@Suppress("TooManyFunctions")
internal class PostInteractions(
    private val posts: PostsRepository,
    private val scope: CoroutineScope,
    private val onChange: (Post) -> Unit = {},
    private val onDeleted: (Post) -> Unit = {},
    private val onCommentDeleted: (Comment) -> Unit = {},
) {
    private val mutableState = MutableStateFlow(PostInteractionState())

    val state: StateFlow<PostInteractionState> = mutableState.asStateFlow()

    val reportDetails = TextFieldState()

    /** Likes are shown at once, and go back if the server says no. */
    fun onToggleLike(post: Post) {
        val liked = !post.likedByMe
        val change = if (liked) 1 else -1
        onChange(post.copy(likedByMe = liked, likeCount = post.likeCount + change))
        scope.launch {
            when (val result = posts.setLiked(post.id, liked)) {
                is Outcome.Success -> onChange(post.copy(likedByMe = liked, likeCount = result.value.count))

                is Outcome.Failure -> {
                    onChange(post)
                    showError(result.error)
                }
            }
        }
    }

    fun onToggleRepost(post: Post) {
        val reposted = !post.repostedByMe
        scope.launch {
            when (val result = posts.setReposted(post.id, reposted)) {
                is Outcome.Success -> onChange(post.copy(repostedByMe = reposted, repostCount = result.value.count))
                is Outcome.Failure -> showError(result.error)
            }
        }
    }

    fun onToggleSave(post: Post) {
        val saved = !post.savedByMe
        scope.launch {
            when (val result = posts.setSaved(post.id, saved)) {
                is Outcome.Success -> onChange(post.copy(savedByMe = saved))
                is Outcome.Failure -> showError(result.error)
            }
        }
    }

    fun onShowDialog(dialog: PostDialog) = mutableState.update { it.copy(dialog = dialog) }

    fun onDismissDialog() {
        reportDetails.clearText()
        mutableState.update { it.copy(dialog = null, reportReason = null) }
    }

    fun onSelectReportReason(reason: ReportReason) = mutableState.update { it.copy(reportReason = reason) }

    fun onConfirmDialog() {
        when (val dialog = mutableState.value.dialog) {
            is PostDialog.ConfirmDeletePost -> deletePost(dialog.post)
            is PostDialog.ConfirmDeleteComment -> deleteComment(dialog.comment)
            is PostDialog.ReportPost, is PostDialog.ReportComment -> report(dialog)
            null -> Unit
        }
    }

    fun onMessageShown() = mutableState.update { it.copy(error = null, wasReported = false) }

    private fun deletePost(post: Post) {
        onDismissDialog()
        scope.launch {
            when (val result = posts.delete(post.id)) {
                is Outcome.Success -> onDeleted(post)
                is Outcome.Failure -> showError(result.error)
            }
        }
    }

    private fun deleteComment(comment: Comment) {
        onDismissDialog()
        scope.launch {
            when (val result = posts.deleteComment(comment.postId, comment.id)) {
                is Outcome.Success -> onCommentDeleted(comment)
                is Outcome.Failure -> showError(result.error)
            }
        }
    }

    private fun report(dialog: PostDialog) {
        val reason = mutableState.value.reportReason ?: return
        val details = reportDetails.text.toString()
        onDismissDialog()
        scope.launch {
            val result = when (dialog) {
                is PostDialog.ReportPost -> posts.reportPost(dialog.post.id, reason, details)
                is PostDialog.ReportComment -> posts.reportComment(dialog.comment.id, reason, details)
                else -> return@launch
            }
            when (result) {
                is Outcome.Success -> mutableState.update { it.copy(wasReported = true) }
                is Outcome.Failure -> showError(result.error)
            }
        }
    }

    private fun showError(error: AppError) = mutableState.update { it.copy(error = error) }
}
