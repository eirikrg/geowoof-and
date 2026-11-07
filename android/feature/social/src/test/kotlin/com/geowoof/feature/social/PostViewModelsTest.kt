package com.geowoof.feature.social

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.SavedStateHandle
import com.geowoof.core.model.AppError
import com.geowoof.core.model.MediaPurpose
import com.geowoof.core.model.Outcome
import com.geowoof.core.model.PostVisibility
import com.geowoof.core.model.ReportReason
import com.geowoof.core.model.SessionState
import com.geowoof.core.testing.FakeAuthRepository
import com.geowoof.core.testing.FakeMediaRepository
import com.geowoof.core.testing.FakePeopleRepository
import com.geowoof.core.testing.FakePetsRepository
import com.geowoof.core.testing.FakePostsRepository
import com.geowoof.core.testing.MainDispatcherRule
import com.geowoof.core.testing.TestPets
import com.geowoof.core.testing.TestPosts
import com.geowoof.core.testing.TestSocial
import com.geowoof.core.testing.TestUsers
import com.geowoof.feature.social.create.CreatePostUiState
import com.geowoof.feature.social.create.CreatePostViewModel
import com.geowoof.feature.social.post.PostDetailViewModel
import com.geowoof.feature.social.post.PostDialog
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Types into a field and publishes the change, as a frame would in the app. */
private fun TextFieldState.enter(text: String) {
    setTextAndPlaceCursorAtEnd(text)
    Snapshot.sendApplyNotifications()
}

class MentionTokenTest {
    private fun token(text: String) = mentionTokenAt(text, text.length)

    @Test
    fun what_follows_an_at_is_a_mention_being_written() {
        assertEquals(MentionToken(5, 8, "be"), token("Hola @be"))
        assertEquals(MentionToken(0, 3, "be"), token("@be"))
        assertEquals(MentionToken(5, 6, ""), token("Hola @"))
    }

    @Test
    fun an_email_or_a_finished_mention_is_not() {
        assertNull(token("yo@bea"))
        assertNull(token("@@bea"))
        assertNull(token("Hola @bea "))
        assertNull(token("Sin nada"))
        assertNull(token(""))
    }

    @Test
    fun it_looks_at_the_cursor_and_not_at_the_end_of_the_text() {
        assertEquals(MentionToken(0, 3, "be"), mentionTokenAt("@be y más", 3))
        assertNull(mentionTokenAt("@be y más", 6))
    }

    @Test
    fun a_cursor_out_of_the_text_finds_nothing() {
        assertNull(mentionTokenAt("@be", 10))
    }
}

class MentionSuggesterTest {
    private val people = FakePeopleRepository().apply { users = listOf(TestSocial.user()) }

    @Test
    fun writing_after_an_at_offers_people_and_choosing_one_writes_it() = runTest {
        val field = TextFieldState()
        val suggester = MentionSuggester(field, people, backgroundScope)
        runCurrent()

        field.enter("Hola @be")
        runCurrent()
        assertTrue(suggester.suggestions.value.isEmpty())
        advanceTimeBy(DEBOUNCE)
        runCurrent()

        assertEquals(listOf("suggest:be"), people.searches)
        assertEquals(listOf("bea"), suggester.suggestions.value.map { it.username })

        suggester.onChoose(TestSocial.user())

        assertEquals("Hola @bea ", field.text.toString())
        assertEquals(field.text.length, field.selection.end)
        assertTrue(suggester.suggestions.value.isEmpty())
    }

    @Test
    fun with_one_letter_the_server_is_not_asked() = runTest {
        val field = TextFieldState()
        MentionSuggester(field, people, backgroundScope)
        runCurrent()

        field.enter("@b")
        advanceTimeBy(DEBOUNCE)
        runCurrent()

        assertEquals(emptyList<String>(), people.searches)
    }

    @Test
    fun going_on_writing_only_asks_for_the_last_thing() = runTest {
        val field = TextFieldState()
        MentionSuggester(field, people, backgroundScope)
        runCurrent()

        field.enter("@be")
        runCurrent()
        field.enter("@bea")
        advanceTimeBy(DEBOUNCE)
        runCurrent()

        assertEquals(listOf("suggest:bea"), people.searches)
    }

    @Test
    fun finishing_the_mention_stops_offering_people() = runTest {
        val field = TextFieldState()
        val suggester = MentionSuggester(field, people, backgroundScope)
        runCurrent()
        field.enter("@be")
        advanceTimeBy(DEBOUNCE)
        runCurrent()

        field.enter("@bea ")
        runCurrent()

        assertTrue(suggester.suggestions.value.isEmpty())
    }

    private companion object {
        const val DEBOUNCE = 300L
    }
}

class PostDetailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val auth = FakeAuthRepository(SessionState.SignedIn(TestUsers.user()))
    private val posts = FakePostsRepository()
    private val people = FakePeopleRepository()

    private fun viewModel() = PostDetailViewModel(SavedStateHandle(mapOf("postId" to "post-1")), auth, posts, people)

    @Test
    fun it_brings_the_post() {
        val state = viewModel().uiState.value

        assertFalse(state.isLoading)
        assertEquals("post-1", state.post?.id)
    }

    @Test
    fun a_post_that_is_gone_or_cannot_be_seen_is_just_not_available() {
        posts.postResult = Outcome.Failure(AppError.Api(status = 404, code = "social.post_not_found"))

        val state = viewModel().uiState.value

        assertTrue(state.isUnavailable)
        assertNull(state.loadError)
    }

    @Test
    fun without_a_connection_it_can_be_retried() {
        posts.postResult = Outcome.Failure(AppError.Network)
        val viewModel = viewModel()
        assertEquals(AppError.Network, viewModel.uiState.value.loadError)

        posts.postResult = Outcome.Success(TestPosts.post())
        viewModel.refresh()

        assertNull(viewModel.uiState.value.loadError)
        assertNotNull(viewModel.uiState.value.post)
    }

    @Test
    fun a_like_takes_the_number_of_the_server() {
        posts.postResult = Outcome.Success(TestPosts.post(likeCount = 4))
        val viewModel = viewModel()

        viewModel.interactions.onToggleLike(viewModel.uiState.value.post!!)

        assertEquals(listOf("like:post-1:true"), posts.calls)
        assertTrue(viewModel.uiState.value.post!!.likedByMe)
        assertEquals(1, viewModel.uiState.value.post!!.likeCount)
    }

    @Test
    fun a_like_that_fails_goes_back_and_says_why() {
        posts.postResult = Outcome.Success(TestPosts.post(likeCount = 4))
        posts.likeResult = Outcome.Failure(AppError.Network)
        val viewModel = viewModel()

        viewModel.interactions.onToggleLike(viewModel.uiState.value.post!!)

        assertFalse(viewModel.uiState.value.post!!.likedByMe)
        assertEquals(4, viewModel.uiState.value.post!!.likeCount)
        assertEquals(AppError.Network, viewModel.interactions.state.value.error)
    }

    @Test
    fun a_repost_can_be_done_and_undone() {
        val viewModel = viewModel()

        viewModel.interactions.onToggleRepost(viewModel.uiState.value.post!!)
        assertTrue(viewModel.uiState.value.post!!.repostedByMe)
        assertEquals(1, viewModel.uiState.value.post!!.repostCount)

        viewModel.interactions.onToggleRepost(viewModel.uiState.value.post!!)
        assertFalse(viewModel.uiState.value.post!!.repostedByMe)
        assertEquals(listOf("repost:post-1:true", "repost:post-1:false"), posts.calls)
    }

    @Test
    fun saving_changes_the_post() {
        val viewModel = viewModel()

        viewModel.interactions.onToggleSave(viewModel.uiState.value.post!!)

        assertTrue(viewModel.uiState.value.post!!.savedByMe)
        assertEquals(listOf("save:post-1:true"), posts.calls)
    }

    @Test
    fun a_comment_is_sent_and_the_field_is_empty_again() {
        val viewModel = viewModel()
        viewModel.commentText.setTextAndPlaceCursorAtEnd("Qué bonita")

        viewModel.onSendComment()

        assertEquals(listOf("comment:post-1:Qué bonita"), posts.calls)
        assertEquals("", viewModel.commentText.text.toString())
        assertEquals(1, viewModel.uiState.value.post!!.commentCount)
        assertFalse(viewModel.uiState.value.isSendingComment)
    }

    @Test
    fun a_comment_that_fails_keeps_what_was_written() {
        posts.addCommentResult = Outcome.Failure(AppError.Network)
        val viewModel = viewModel()
        viewModel.commentText.setTextAndPlaceCursorAtEnd("Qué bonita")

        viewModel.onSendComment()

        assertEquals("Qué bonita", viewModel.commentText.text.toString())
        assertEquals(AppError.Network, viewModel.commentFailure.value)
        viewModel.onCommentFailureShown()
        assertNull(viewModel.commentFailure.value)
    }

    @Test
    fun an_empty_comment_is_not_sent() {
        val viewModel = viewModel()
        viewModel.commentText.setTextAndPlaceCursorAtEnd("   ")

        viewModel.onSendComment()

        assertEquals(emptyList<String>(), posts.calls)
    }

    @Test
    fun adding_a_comment_reloads_the_list() = runTest {
        val viewModel = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.comments.collect {} }
        val before = posts.reads.count { it == "comments:post-1" }
        viewModel.commentText.setTextAndPlaceCursorAtEnd("Hola")

        viewModel.onSendComment()

        assertEquals(before + 1, posts.reads.count { it == "comments:post-1" })
    }

    @Test
    fun deleting_the_post_needs_a_confirmation_and_leaves_the_screen() {
        val viewModel = viewModel()
        val post = viewModel.uiState.value.post!!

        viewModel.interactions.onShowDialog(PostDialog.ConfirmDeletePost(post))
        assertEquals(emptyList<String>(), posts.calls)

        viewModel.interactions.onConfirmDialog()

        assertEquals(listOf("delete:post-1"), posts.calls)
        assertNull(viewModel.interactions.state.value.dialog)
        assertTrue(viewModel.uiState.value.isFinished)
    }

    @Test
    fun a_post_that_cannot_be_deleted_stays_on_the_screen() {
        posts.actionResult = Outcome.Failure(AppError.Network)
        val viewModel = viewModel()

        viewModel.interactions.onShowDialog(PostDialog.ConfirmDeletePost(viewModel.uiState.value.post!!))
        viewModel.interactions.onConfirmDialog()

        assertFalse(viewModel.uiState.value.isFinished)
        assertEquals(AppError.Network, viewModel.interactions.state.value.error)
    }

    @Test
    fun deleting_a_comment_changes_the_number() {
        posts.postResult = Outcome.Success(TestPosts.post(commentCount = 2))
        val viewModel = viewModel()

        viewModel.interactions.onShowDialog(PostDialog.ConfirmDeleteComment(TestPosts.comment()))
        viewModel.interactions.onConfirmDialog()

        assertEquals(listOf("deleteComment:post-1:comment-1"), posts.calls)
        assertEquals(1, viewModel.uiState.value.post!!.commentCount)
    }

    @Test
    fun a_report_needs_a_reason_and_says_thanks() {
        val viewModel = viewModel()
        val interactions = viewModel.interactions
        interactions.onShowDialog(PostDialog.ReportPost(viewModel.uiState.value.post!!))

        interactions.onConfirmDialog()
        assertEquals(emptyList<String>(), posts.calls)

        interactions.onSelectReportReason(ReportReason.Spam)
        interactions.reportDetails.setTextAndPlaceCursorAtEnd("Anuncios")
        interactions.onConfirmDialog()

        assertEquals(listOf("reportPost:post-1:Spam:Anuncios"), posts.calls)
        assertTrue(interactions.state.value.wasReported)
        assertEquals("", interactions.reportDetails.text.toString())
        interactions.onMessageShown()
        assertFalse(interactions.state.value.wasReported)
    }

    @Test
    fun a_comment_can_be_reported() {
        val interactions = viewModel().interactions
        interactions.onShowDialog(PostDialog.ReportComment(TestPosts.comment()))
        interactions.onSelectReportReason(ReportReason.Harassment)

        interactions.onConfirmDialog()

        assertEquals(listOf("reportComment:comment-1:Harassment:"), posts.calls)
    }

    @Test
    fun closing_a_dialog_forgets_what_was_written() {
        val interactions = viewModel().interactions
        interactions.onShowDialog(PostDialog.ReportComment(TestPosts.comment()))
        interactions.onSelectReportReason(ReportReason.Other)
        interactions.reportDetails.setTextAndPlaceCursorAtEnd("Algo")

        interactions.onDismissDialog()

        assertNull(interactions.state.value.dialog)
        assertNull(interactions.state.value.reportReason)
        assertEquals("", interactions.reportDetails.text.toString())
    }
}

class CommunityViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val posts = FakePostsRepository()

    @Test
    fun it_knows_who_is_looking_and_asks_for_the_feed_and_explore() = runTest {
        val viewModel = CommunityViewModel(FakeAuthRepository(SessionState.SignedIn(TestUsers.user())), posts)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.feed.collect {} }

        assertEquals(TestUsers.user().id, viewModel.myUserId)
        assertEquals(listOf("feed", "explore"), posts.reads)
    }

    @Test
    fun a_like_goes_to_the_repository() {
        val viewModel = CommunityViewModel(FakeAuthRepository(SessionState.SignedOut), posts)

        viewModel.interactions.onToggleLike(TestPosts.post(likedByMe = true))

        assertEquals(listOf("like:post-1:false"), posts.calls)
    }
}

class CreatePostViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val posts = FakePostsRepository()
    private val media = FakeMediaRepository()
    private val pets = FakePetsRepository(listOf(TestPets.pet()))
    private val people = FakePeopleRepository()

    private val viewModel by lazy { CreatePostViewModel(posts, media, pets, people) }

    @Test
    fun it_cannot_be_published_without_photos() {
        viewModel.onPublish()

        assertTrue(posts.published.isEmpty())
        assertFalse(viewModel.uiState.value.canPublish)
    }

    @Test
    fun the_photos_are_uploaded_and_then_it_is_published_with_what_was_chosen() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        viewModel.onAddPhotos(listOf("content://1", "content://2"))
        viewModel.text.setTextAndPlaceCursorAtEnd("En el Retiro")
        viewModel.onSelectVisibility(PostVisibility.Friends)
        viewModel.onTogglePet("luna")

        viewModel.onPublish()

        assertEquals(
            listOf("content://1" to MediaPurpose.PostImage, "content://2" to MediaPurpose.PostImage),
            media.uploads,
        )
        val post = posts.published.single()
        assertEquals("En el Retiro", post.text)
        assertEquals(PostVisibility.Friends, post.visibility)
        assertEquals(listOf("asset-1", "asset-1"), post.mediaAssetIds)
        assertEquals(listOf("luna"), post.petIds)
        assertTrue(viewModel.uiState.value.isPublished)
    }

    @Test
    fun public_is_the_starting_visibility_and_the_pets_of_the_person_are_offered() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        assertEquals(PostVisibility.Everyone, viewModel.uiState.value.visibility)
        assertEquals(listOf("Luna"), viewModel.uiState.value.pets.map { it.name })
    }

    @Test
    fun retrying_does_not_upload_again_nor_publish_another_post() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        viewModel.onAddPhotos(listOf("content://1"))
        posts.publishResult = Outcome.Failure(AppError.Network)

        viewModel.onPublish()
        assertEquals(AppError.Network, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isPublished)

        posts.publishResult = Outcome.Success(TestPosts.post())
        viewModel.onPublish()

        assertEquals(1, media.uploads.size)
        assertEquals(1, posts.published.map { it.id }.toSet().size)
        assertEquals(2, posts.published.size)
        assertTrue(viewModel.uiState.value.isPublished)
    }

    @Test
    fun a_photo_that_cannot_be_uploaded_stops_it_and_says_why() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        val rejected = AppError.Api(status = 422, code = "media.invalid_image")
        media.result = Outcome.Failure(rejected)
        viewModel.onAddPhotos(listOf("content://1"))

        viewModel.onPublish()

        assertEquals(rejected, viewModel.uiState.value.error)
        assertTrue(posts.published.isEmpty())
        assertFalse(viewModel.uiState.value.isPublishing)
        viewModel.onErrorShown()
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun the_same_photo_is_not_added_twice_and_there_is_a_limit() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        viewModel.onAddPhotos(listOf("content://1", "content://1"))
        assertEquals(1, viewModel.uiState.value.photos.size)

        viewModel.onAddPhotos((2..20).map { "content://$it" })
        assertEquals(CreatePostUiState.MAX_PHOTOS, viewModel.uiState.value.photos.size)
        assertFalse(viewModel.uiState.value.canAddPhotos)
    }

    @Test
    fun a_photo_can_be_removed_and_a_pet_unselected() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        viewModel.onAddPhotos(listOf("content://1", "content://2"))
        viewModel.onTogglePet("luna")

        viewModel.onRemovePhoto("content://1")
        viewModel.onTogglePet("luna")

        assertEquals(listOf("content://2"), viewModel.uiState.value.photos.map { it.contentUri })
        assertTrue(viewModel.uiState.value.selectedPetIds.isEmpty())
    }
}
