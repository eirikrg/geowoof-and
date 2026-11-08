package com.geowoof.feature.social

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.core.model.Comment
import com.geowoof.core.testing.DrainMainLooperRule
import com.geowoof.core.testing.TestPosts
import com.geowoof.core.testing.TestSocial
import com.geowoof.feature.social.post.OpenThread
import com.geowoof.feature.social.post.PostCardActions
import com.geowoof.feature.social.post.PostDetailActions
import com.geowoof.feature.social.post.PostDetailContent
import com.geowoof.feature.social.post.PostDetailUiState
import com.geowoof.feature.social.post.PostDialog
import com.geowoof.feature.social.post.PostDialogs
import com.geowoof.feature.social.post.PostInteractionState
import com.geowoof.feature.social.post.ReplyTarget
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.Instant

/** The comments of a post with their replies under them. Ana is the one looking. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w412dp-h915dp")
class CommentThreadsScreenTest {
    @get:Rule(order = -2)
    val drainMainLooper = DrainMainLooperRule()

    @get:Rule
    val composeRule = createComposeRule()

    private val calls = mutableListOf<String>()

    private val bea = TestSocial.user("bea-id", "bea", "Bea")
    private val carlos = TestSocial.user("carlos-id", "carlos", "Carlos")

    private fun reply(id: String, text: String = "Respuesta $id") = TestPosts.comment(id, carlos, text, parentId = "c1")

    private fun show(
        comments: List<Comment>,
        openThreads: Map<String, OpenThread> = emptyMap(),
        replyingTo: ReplyTarget? = null,
    ) {
        composeRule.setContent {
            GeoWoofTheme(dynamicColor = false) {
                PostDetailContent(
                    uiState = PostDetailUiState(
                        isLoading = false,
                        post = TestPosts.post(),
                        replyingTo = replyingTo,
                        openThreads = openThreads,
                    ),
                    comments = flowOf(TestSocial.pages(comments)).collectAsLazyPagingItems(),
                    myUserId = "ana-id",
                    interactionState = PostInteractionState(),
                    reportDetails = TextFieldState(),
                    commentText = TextFieldState(),
                    suggestions = emptyList(),
                    commentFailure = null,
                    actions = PostDetailActions(
                        onReply = { calls += "reply:${it.id}" },
                        onCancelReply = { calls += "cancel" },
                        onOpenReplies = { calls += "open:$it" },
                        onMoreReplies = { calls += "more:$it" },
                        onHideReplies = { calls += "hide:$it" },
                        card = PostCardActions(onShowDialog = { calls += it::class.simpleName.orEmpty() }),
                    ),
                )
            }
        }
    }

    /** The pages of the list come through a coroutine that the clock of the test does not wait for. */
    private fun scrollTo(text: String) {
        composeRule.waitUntil(WAIT_MILLIS) {
            runCatching {
                composeRule.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText(text))
            }.isSuccess
        }
    }

    @Test
    fun one_or_two_replies_are_under_their_comment_and_further_in() {
        show(listOf(TestPosts.comment("c1", bea, "Qué bonito", replies = listOf(reply("r1"), reply("r2")))))

        scrollTo("Respuesta r2")
        val comment = composeRule.onNodeWithText("Qué bonito").getBoundsInRoot()
        val first = composeRule.onNodeWithText("Respuesta r1").assertIsDisplayed().getBoundsInRoot()
        assertTrue(first.top >= comment.bottom)
        assertTrue("the reply starts at ${first.left}, the comment at ${comment.left}", first.left > comment.left)
        composeRule.onNodeWithText("View 2 replies").assertDoesNotExist()
        composeRule.onNodeWithText("Hide replies").assertDoesNotExist()
    }

    /** Short beside the name, and whole for TalkBack. */
    @Test
    fun each_comment_says_how_long_ago_it_was_written() {
        val written = Instant.now().minus(Duration.ofDays(2))
        val dani = TestSocial.user("dani-id", "dani", "Dani")
        show(listOf(TestPosts.comment("c1", dani, "Qué bonito").copy(createdAt = written)))

        scrollTo("Qué bonito")
        val time = composeRule.onNodeWithText("2d").assertIsDisplayed().getBoundsInRoot()
        composeRule.onNodeWithContentDescription("2 days ago").assertExists()
        assertTrue(time.left > composeRule.onNodeWithText("Dani").getBoundsInRoot().right)
    }

    /** Without a gap between them: the line of the thread joins them. */
    @Test
    fun the_first_reply_is_right_under_its_comment() {
        show(listOf(TestPosts.comment("c1", bea, "Qué bonito", replies = listOf(reply("r1")))))

        scrollTo("Respuesta r1")
        val answer = composeRule.onAllNodesWithText("Reply")[0].getBoundsInRoot()
        val author = composeRule.onNodeWithText("Carlos").getBoundsInRoot()
        assertTrue("${author.top - answer.bottom} between them", author.top - answer.bottom <= 16.dp)
    }

    @Test
    fun more_replies_are_folded_until_they_are_opened() {
        val folded = TestPosts.comment(
            "c1",
            bea,
            "Qué bonito",
            replies = listOf(reply("r1"), reply("r2")),
            replyCount = 5,
        )
        show(listOf(folded))

        scrollTo("View 5 replies")
        composeRule.onNodeWithText("Respuesta r1").assertDoesNotExist()
        composeRule.onNodeWithText("View 5 replies").performClick()

        assertEquals(listOf("open:c1"), calls)
    }

    @Test
    fun an_open_thread_shows_its_replies_the_way_to_the_next_ones_and_to_hide_them() {
        val folded = TestPosts.comment(
            "c1",
            bea,
            "Qué bonito",
            replies = listOf(reply("r1"), reply("r2")),
            replyCount = 5,
        )
        show(
            listOf(folded),
            mapOf("c1" to OpenThread(listOf(reply("r1"), reply("r2"), reply("r3")), nextCursor = "next")),
        )

        scrollTo("Hide replies")
        composeRule.onNodeWithText("Respuesta r3").assertIsDisplayed()
        composeRule.onNodeWithText("View 5 replies").assertDoesNotExist()
        composeRule.onNodeWithText("View more replies").performClick()
        composeRule.onNodeWithText("Hide replies").performClick()

        assertEquals(listOf("more:c1", "hide:c1"), calls)
    }

    @Test
    fun replies_that_could_not_be_loaded_can_be_asked_for_again() {
        val folded = TestPosts.comment("c1", bea, "Qué bonito", replyCount = 5)
        show(listOf(folded), mapOf("c1" to OpenThread(failed = true)))

        scrollTo("The replies could not be loaded. Try again")
        composeRule.onNodeWithText("The replies could not be loaded. Try again").performClick()

        assertEquals(listOf("more:c1"), calls)
    }

    @Test
    fun comments_and_replies_can_be_answered() {
        show(listOf(TestPosts.comment("c1", bea, "Qué bonito", replies = listOf(reply("r1")))))

        scrollTo("Respuesta r1")
        composeRule.onAllNodesWithText("Reply")[0].performScrollTo().performClick()
        composeRule.onAllNodesWithText("Reply")[1].performScrollTo().performClick()

        assertEquals(listOf("reply:c1", "reply:r1"), calls)
    }

    /** It keeps the place of its replies, without who wrote it, what it said or anything to do with it. */
    @Test
    fun a_deleted_comment_keeps_its_replies_and_nothing_else() {
        val dani = TestSocial.user("dani-id", "dani", "Dani")
        val deleted = TestPosts.comment("c1", dani, "", isDeleted = true, replies = listOf(reply("r1")))
        show(listOf(deleted))

        scrollTo("Respuesta r1")
        composeRule.onNodeWithText("Comment deleted").assertIsDisplayed()
        composeRule.onNodeWithText("Dani").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Options for the comment by Dani").assertDoesNotExist()
        composeRule.onAllNodesWithText("Reply").fetchSemanticsNodes().size.let { assertEquals(1, it) }
    }

    @Test
    fun the_field_says_whom_it_answers_and_it_can_be_cancelled() {
        show(listOf(TestPosts.comment("c1", bea, "Qué bonito")), replyingTo = ReplyTarget("c1", "bea"))

        composeRule.onNodeWithText("Replying to @bea").assertIsDisplayed()
        composeRule.onNodeWithText("Add a reply").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Cancel reply").performClick()

        assertEquals(listOf("cancel"), calls)
    }

    @Test
    fun deleting_a_comment_with_replies_says_that_they_stay() {
        val comment = TestPosts.comment("c1", bea, "Qué bonito", canDelete = true, replies = listOf(reply("r1")))
        composeRule.setContent {
            GeoWoofTheme(dynamicColor = false) {
                PostDialogs(
                    state = PostInteractionState(dialog = PostDialog.ConfirmDeleteComment(comment)),
                    reportDetails = TextFieldState(),
                    onSelectReason = {},
                    onConfirm = { calls += "confirm" },
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithText(
            "Its replies will stay, under “Comment deleted”. This cannot be undone.",
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Delete").performClick()
        assertEquals(listOf("confirm"), calls)
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L
    }
}
