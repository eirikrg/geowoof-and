package com.geowoof.feature.social

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.SavedStateHandle
import com.geowoof.core.data.repository.ContentChanges
import com.geowoof.core.model.AppError
import com.geowoof.core.model.CommentReplies
import com.geowoof.core.model.Outcome
import com.geowoof.core.model.SessionState
import com.geowoof.core.testing.FakeAuthRepository
import com.geowoof.core.testing.FakePeopleRepository
import com.geowoof.core.testing.FakePostsRepository
import com.geowoof.core.testing.MainDispatcherRule
import com.geowoof.core.testing.TestPosts
import com.geowoof.core.testing.TestSocial
import com.geowoof.core.testing.TestUsers
import com.geowoof.feature.social.post.PostDetailViewModel
import com.geowoof.feature.social.post.PostDialog
import com.geowoof.feature.social.post.ReplyTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Answering comments in threads of one level, from the screen of a post. */
class PostRepliesViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val auth = FakeAuthRepository(SessionState.SignedIn(TestUsers.user()))
    private val posts = FakePostsRepository()

    private val bea = TestSocial.user("bea-id", "bea", "Bea")
    private val carlos = TestSocial.user("carlos-id", "carlos", "Carlos")
    private val ofBea = TestPosts.comment("c1", bea, "Qué bonito")
    private val ofCarlos = TestPosts.comment("r1", carlos, "@bea sí", parentId = "c1")

    private fun viewModel() = PostDetailViewModel(
        SavedStateHandle(mapOf("postId" to "post-1")),
        auth,
        posts,
        FakePeopleRepository(),
        ContentChanges(),
    )

    private fun page(vararg ids: String, next: String? = null) = Outcome.Success(
        CommentReplies(
            ids.map {
                TestPosts.comment(it, carlos, "Respuesta $it", parentId = "c1")
            },
            next,
        ),
    )

    @Test
    fun answering_starts_with_the_mention_of_that_person_and_goes_to_their_comment() {
        val viewModel = viewModel()

        viewModel.onReply(ofBea)

        assertEquals(ReplyTarget("c1", "bea"), viewModel.uiState.value.replyingTo)
        assertEquals("@bea ", viewModel.commentText.text.toString())
    }

    @Test
    fun answering_a_reply_goes_to_the_same_thread_with_the_mention_of_who_wrote_the_reply() {
        val viewModel = viewModel()

        viewModel.onReply(ofCarlos)

        assertEquals(ReplyTarget("c1", "carlos"), viewModel.uiState.value.replyingTo)
        assertEquals("@carlos ", viewModel.commentText.text.toString())
    }

    @Test
    fun answering_someone_else_changes_the_mention_but_keeps_what_was_written() {
        val viewModel = viewModel()
        viewModel.commentText.setTextAndPlaceCursorAtEnd("Me encanta")

        viewModel.onReply(ofBea)
        assertEquals("@bea Me encanta", viewModel.commentText.text.toString())
        viewModel.onReply(ofCarlos)
        assertEquals("@carlos Me encanta", viewModel.commentText.text.toString())

        viewModel.onCancelReply()
        assertNull(viewModel.uiState.value.replyingTo)
        assertEquals("Me encanta", viewModel.commentText.text.toString())
    }

    @Test
    fun a_deleted_comment_cannot_be_answered() {
        val viewModel = viewModel()

        viewModel.onReply(TestPosts.comment("c1", bea, "", isDeleted = true))

        assertNull(viewModel.uiState.value.replyingTo)
        assertEquals("", viewModel.commentText.text.toString())
    }

    @Test
    fun a_reply_is_sent_to_its_thread_which_opens_with_it() {
        val reply = TestPosts.comment("r9", TestSocial.user(), "@bea yo también", parentId = "c1")
        posts.addCommentResult = Outcome.Success(reply)
        posts.repliesResults = mapOf(null to page("r1"))
        val viewModel = viewModel()
        viewModel.onReply(ofBea)
        viewModel.commentText.setTextAndPlaceCursorAtEnd("@bea yo también")

        viewModel.onSendComment()

        assertEquals(listOf("reply:c1:@bea yo también", "replies:c1:null"), posts.calls)
        val state = viewModel.uiState.value
        assertNull(state.replyingTo)
        assertEquals("", viewModel.commentText.text.toString())
        assertEquals(1, state.post!!.commentCount)
        assertEquals(listOf("r1", "r9"), state.openThreads.getValue("c1").replies.map { it.id })
    }

    @Test
    fun a_reply_that_fails_keeps_what_was_written_and_whom_it_answers() {
        posts.addCommentResult = Outcome.Failure(AppError.Network)
        val viewModel = viewModel()
        viewModel.onReply(ofBea)

        viewModel.onSendComment()

        assertEquals(ReplyTarget("c1", "bea"), viewModel.uiState.value.replyingTo)
        assertEquals("@bea ", viewModel.commentText.text.toString())
        assertTrue(viewModel.uiState.value.openThreads.isEmpty())
    }

    @Test
    fun the_replies_of_a_thread_come_page_by_page_and_can_be_hidden() {
        posts.repliesResults = mapOf(null to page("r1", "r2", next = "next"), "next" to page("r3"))
        val viewModel = viewModel()

        viewModel.onOpenReplies("c1")
        assertEquals("next", viewModel.uiState.value.openThreads.getValue("c1").nextCursor)
        viewModel.onMoreReplies("c1")

        val thread = viewModel.uiState.value.openThreads.getValue("c1")
        assertEquals(listOf("r1", "r2", "r3"), thread.replies.map { it.id })
        assertNull(thread.nextCursor)
        assertFalse(thread.isLoading)
        viewModel.onHideReplies("c1")
        assertTrue(viewModel.uiState.value.openThreads.isEmpty())
    }

    @Test
    fun replies_that_could_not_be_loaded_can_be_asked_for_again() {
        posts.repliesResults = mapOf(null to Outcome.Failure(AppError.Network))
        val viewModel = viewModel()

        viewModel.onOpenReplies("c1")
        assertTrue(viewModel.uiState.value.openThreads.getValue("c1").failed)
        posts.repliesResults = mapOf(null to page("r1"))
        viewModel.onMoreReplies("c1")

        val thread = viewModel.uiState.value.openThreads.getValue("c1")
        assertFalse(thread.failed)
        assertEquals(listOf("r1"), thread.replies.map { it.id })
        assertEquals(listOf("replies:c1:null", "replies:c1:null"), posts.calls)
    }

    @Test
    fun a_deleted_reply_leaves_its_open_thread() {
        posts.postResult = Outcome.Success(TestPosts.post(commentCount = 3))
        posts.repliesResults = mapOf(null to page("r1", "r2"))
        val viewModel = viewModel()
        viewModel.onOpenReplies("c1")
        val first = viewModel.uiState.value.openThreads.getValue("c1").replies.first()

        viewModel.interactions.onShowDialog(PostDialog.ConfirmDeleteComment(first))
        viewModel.interactions.onConfirmDialog()

        assertEquals(listOf("r2"), viewModel.uiState.value.openThreads.getValue("c1").replies.map { it.id })
        assertEquals(2, viewModel.uiState.value.post!!.commentCount)
    }
}
