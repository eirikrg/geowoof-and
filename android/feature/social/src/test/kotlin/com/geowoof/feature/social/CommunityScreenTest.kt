package com.geowoof.feature.social

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.geowoof.core.data.repository.ContentChanges
import com.geowoof.core.data.repository.RemoteLoadException
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.core.model.AppError
import com.geowoof.core.model.Comment
import com.geowoof.core.model.Mention
import com.geowoof.core.model.Post
import com.geowoof.core.model.PostVisibility
import com.geowoof.core.model.RemoteImage
import com.geowoof.core.model.ReportReason
import com.geowoof.core.model.SessionState
import com.geowoof.core.model.TimelineItem
import com.geowoof.core.model.UserSummary
import com.geowoof.core.testing.FakeAuthRepository
import com.geowoof.core.testing.FakePeopleRepository
import com.geowoof.core.testing.FakePostsRepository
import com.geowoof.core.testing.TestPets
import com.geowoof.core.testing.TestPosts
import com.geowoof.core.testing.TestSocial
import com.geowoof.core.testing.TestUsers
import com.geowoof.feature.social.create.ChosenPhoto
import com.geowoof.feature.social.create.CreatePostActions
import com.geowoof.feature.social.create.CreatePostContent
import com.geowoof.feature.social.create.CreatePostUiState
import com.geowoof.feature.social.navigation.PostRoute
import com.geowoof.feature.social.post.PostCardActions
import com.geowoof.feature.social.post.PostDetailActions
import com.geowoof.feature.social.post.PostDetailContent
import com.geowoof.feature.social.post.PostDetailUiState
import com.geowoof.feature.social.post.PostDetailViewModel
import com.geowoof.feature.social.post.PostDialog
import com.geowoof.feature.social.post.PostInteractionState
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CommunityScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val calls = mutableListOf<String>()

    private val cardActions = PostCardActions(
        onOpenPost = { calls += "open:${it.id}" },
        onOpenUser = { calls += "user:$it" },
        onToggleLike = { calls += "like:${it.id}" },
        onToggleRepost = { calls += "repost:${it.id}" },
        onToggleSave = { calls += "save:${it.id}" },
        onShowDialog = { calls += it::class.simpleName.orEmpty() },
    )

    private fun showFeed(
        items: PagingData<TimelineItem>,
        state: PostInteractionState = PostInteractionState(),
        myUserId: String? = "ana-id",
        explore: PagingData<Post> = TestSocial.pages(emptyList()),
        selectedTab: CommunityTab = CommunityTab.Following,
    ) {
        composeRule.setContent {
            GeoWoofTheme(dynamicColor = false) {
                CommunityContent(
                    feed = flowOf(items).collectAsLazyPagingItems(),
                    explore = flowOf(explore).collectAsLazyPagingItems(),
                    selectedTab = selectedTab,
                    onSelectTab = { calls += "tab:$it" },
                    myUserId = myUserId,
                    state = state,
                    reportDetails = TextFieldState(),
                    actions = cardActions,
                    onCreatePost = { calls += "create" },
                    onFindPeople = { calls += "people" },
                    onSelectReportReason = { calls += "reason:$it" },
                    onConfirmDialog = { calls += "confirm" },
                    onDismissDialog = { calls += "dismiss" },
                    onDismissMessage = { calls += "shown" },
                )
            }
        }
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(WAIT_MILLIS) { composeRule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun scrollTo(description: String) {
        composeRule.waitUntil(WAIT_MILLIS) {
            runCatching {
                composeRule.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(
                    hasContentDescription(description),
                )
            }.isSuccess
        }
    }

    @Test
    fun an_empty_feed_says_how_to_fill_it() {
        showFeed(TestSocial.pages(emptyList()))

        waitForText("Your community feed")
        composeRule.onNodeWithText("Your community feed").assertIsDisplayed()
        composeRule.onNodeWithText("Find people").performClick()

        assertEquals(listOf("people"), calls)
    }

    @Test
    fun a_new_post_can_be_created_from_the_feed() {
        showFeed(TestSocial.pages(emptyList()))

        composeRule.onNodeWithContentDescription("New post").performClick()

        assertEquals(listOf("create"), calls)
    }

    @Test
    fun a_post_shows_who_published_it_and_what_they_wrote() {
        showFeed(TestSocial.pages(listOf(TestPosts.item(TestPosts.post(text = "Mañana de sol")))))

        waitForText("Bea")
        composeRule.onNodeWithText("Bea").assertIsDisplayed()
        composeRule.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText("Mañana de sol"))
        composeRule.onNodeWithText("Mañana de sol").assertIsDisplayed()
    }

    @Test
    fun a_repost_says_who_did_it_and_shows_the_original_author() {
        val item = TestPosts.item(repostedBy = TestSocial.user("ana-id", "ana", "Ana"))
        showFeed(TestSocial.pages(listOf(item)))

        waitForText("Ana reposted")
        composeRule.onNodeWithText("Ana reposted").assertIsDisplayed()
        composeRule.onNodeWithText("Bea").performClick()

        assertEquals(listOf("user:bea-id"), calls)
    }

    @Test
    fun the_author_of_a_post_opens_their_profile() {
        showFeed(TestSocial.pages(listOf(TestPosts.item())))

        waitForText("Bea")
        composeRule.onNodeWithText("Bea").performClick()

        assertEquals(listOf("user:bea-id"), calls)
    }

    @Test
    fun community_has_the_people_you_follow_and_explore() {
        showFeed(TestSocial.pages(listOf(TestPosts.item())))

        composeRule.onNodeWithText("Following").assertIsSelected()
        composeRule.onNodeWithText("Explore").assertIsNotSelected().performClick()

        assertEquals(listOf("tab:Explore"), calls)
    }

    @Test
    fun explore_shows_the_photos_of_what_people_publish_and_a_photo_opens_the_post() {
        val post = TestPosts.post("post-9")
        showFeed(
            TestSocial.pages(emptyList()),
            explore = TestSocial.pages(listOf(post)),
            selectedTab = CommunityTab.Explore,
        )

        composeRule.waitUntil(WAIT_MILLIS) {
            composeRule.onAllNodes(hasContentDescription("Open post")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("Open post").performClick()

        assertEquals(listOf("open:post-9"), calls)
    }

    @Test
    fun with_nothing_to_explore_it_says_what_will_appear() {
        showFeed(TestSocial.pages(emptyList()), selectedTab = CommunityTab.Explore)

        waitForText("Nothing to explore yet")
    }

    @Test
    fun if_explore_could_not_be_loaded_it_can_try_again() {
        val failed = LoadStates(
            refresh = LoadState.Error(RemoteLoadException(AppError.Network)),
            prepend = LoadState.NotLoading(false),
            append = LoadState.NotLoading(false),
        )

        showFeed(TestSocial.pages(emptyList()), explore = PagingData.empty(failed), selectedTab = CommunityTab.Explore)

        waitForText("Try again")
    }

    @Test
    fun in_the_feed_touching_a_photo_opens_the_post() {
        showFeed(TestSocial.pages(listOf(TestPosts.item())))

        waitForText("Bea")
        composeRule.onNodeWithContentDescription("Photo by Bea").performClick()

        assertEquals(listOf("open:post-1"), calls)
        composeRule.onNodeWithContentDescription("Close").assertDoesNotExist()
    }

    @Test
    fun in_the_feed_a_photo_that_is_still_being_processed_also_opens_the_post() {
        val processing = TestPosts.post(media = listOf(RemoteImage("asset-1", null, null)))
        showFeed(TestSocial.pages(listOf(TestPosts.item(processing))))

        waitForText("Bea")
        composeRule.onNodeWithContentDescription("Photo by Bea").performClick()

        assertEquals(listOf("open:post-1"), calls)
    }

    @Test
    fun a_post_can_be_liked_saved_reposted_and_opened() {
        showFeed(TestSocial.pages(listOf(TestPosts.item())))

        waitForText("Bea")
        scrollTo("Like")
        composeRule.onNodeWithContentDescription("Like").performClick()
        composeRule.onNodeWithContentDescription("Repost").performClick()
        // On the small screen of these tests, the floating button can be on top of it, which scrolling solves.
        composeRule.onNodeWithContentDescription("Save").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithContentDescription("Comments").performClick()

        assertEquals(listOf("like:post-1", "repost:post-1", "save:post-1", "open:post-1"), calls)
    }

    @Test
    fun what_was_done_says_so_and_can_be_undone() {
        val post = TestPosts.post(likedByMe = true, savedByMe = true, repostedByMe = true, likeCount = 7)
        showFeed(TestSocial.pages(listOf(TestPosts.item(post))))

        waitForText("Bea")
        scrollTo("Remove like")
        composeRule.onNodeWithText("7").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Undo repost").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Remove from saved").assertIsDisplayed()
    }

    @Test
    fun what_cannot_be_reposted_does_not_offer_it() {
        showFeed(TestSocial.pages(listOf(TestPosts.item(TestPosts.post(canRepost = false)))))

        waitForText("Bea")
        scrollTo("Like")
        composeRule.onNodeWithContentDescription("Repost").assertDoesNotExist()
    }

    @Test
    fun a_post_for_friends_says_so() {
        showFeed(TestSocial.pages(listOf(TestPosts.item(TestPosts.post(visibility = PostVisibility.Friends)))))

        waitForText("Bea")
        composeRule.onNodeWithContentDescription("Friends").assertIsDisplayed()
    }

    @Test
    fun your_own_post_can_be_deleted_and_that_of_someone_else_reported() {
        showFeed(TestSocial.pages(listOf(TestPosts.item())), myUserId = "bea-id")

        waitForText("Bea")
        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.onNodeWithText("Report").assertDoesNotExist()
        composeRule.onNodeWithText("Delete").performClick()

        assertEquals(listOf(PostDialog.ConfirmDeletePost::class.simpleName), calls)
    }

    @Test
    fun the_post_of_someone_else_can_be_reported() {
        showFeed(TestSocial.pages(listOf(TestPosts.item())))

        waitForText("Bea")
        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.onNodeWithText("Delete").assertDoesNotExist()
        composeRule.onNodeWithText("Report").performClick()

        assertEquals(listOf(PostDialog.ReportPost::class.simpleName), calls)
    }

    @Test
    fun several_photos_say_how_many_there_are() {
        val post = TestPosts.post(media = listOf(TestPosts.IMAGE, TestPosts.IMAGE.copy(assetId = "asset-2")))
        showFeed(TestSocial.pages(listOf(TestPosts.item(post))))

        waitForText("1/2")
        composeRule.onNodeWithText("1/2").assertIsDisplayed()
    }

    @Test
    fun a_failure_says_why_and_can_be_retried() {
        val failed = LoadStates(
            refresh = LoadState.Error(RemoteLoadException(AppError.Network)),
            prepend = LoadState.NotLoading(false),
            append = LoadState.NotLoading(false),
        )
        showFeed(PagingData.empty(failed))

        waitForText("Try again")
        composeRule.onNodeWithText("Try again").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun deleting_asks_first() {
        val dialog = PostDialog.ConfirmDeletePost(TestPosts.post())
        showFeed(TestSocial.pages(emptyList()), PostInteractionState(dialog = dialog))

        composeRule.onNodeWithText("Delete this post?").assertIsDisplayed()
        composeRule.onNodeWithText("Delete").performClick()

        assertEquals(listOf("confirm"), calls)
    }

    @Test
    fun a_report_needs_a_reason() {
        val dialog = PostDialog.ReportPost(TestPosts.post())
        showFeed(TestSocial.pages(emptyList()), PostInteractionState(dialog = dialog))

        composeRule.onNodeWithText("Report this post").assertIsDisplayed()
        composeRule.onNodeWithText("Send").assertIsNotEnabled()
        composeRule.onNodeWithText("Spam").performClick()

        assertEquals(listOf("reason:${ReportReason.Spam}"), calls)
    }

    @Test
    fun with_a_reason_the_report_can_be_sent() {
        val state =
            PostInteractionState(dialog = PostDialog.ReportPost(TestPosts.post()), reportReason = ReportReason.Spam)
        showFeed(TestSocial.pages(emptyList()), state)

        composeRule.onNodeWithText("Send").assertIsEnabled().performClick()

        assertEquals(listOf("confirm"), calls)
    }

    private fun showDetail(
        uiState: PostDetailUiState,
        comments: List<Comment> = emptyList(),
        text: TextFieldState = TextFieldState(),
        suggestions: List<UserSummary> = emptyList(),
    ) {
        composeRule.setContent {
            GeoWoofTheme(dynamicColor = false) {
                PostDetailContent(
                    uiState = uiState,
                    comments = flowOf(TestSocial.pages(comments)).collectAsLazyPagingItems(),
                    myUserId = "ana-id",
                    interactionState = PostInteractionState(),
                    reportDetails = TextFieldState(),
                    commentText = text,
                    suggestions = suggestions,
                    commentFailure = null,
                    actions = PostDetailActions(
                        onBack = { calls += "back" },
                        onRefresh = { calls += "refresh" },
                        onSendComment = { calls += "send" },
                        onChooseMention = { calls += "mention:${it.username}" },
                        card = cardActions,
                    ),
                )
            }
        }
    }

    private fun detail() = PostDetailUiState(isLoading = false, post = TestPosts.post())

    @Test
    fun in_the_post_touching_a_photo_enlarges_it() {
        showDetail(detail())

        composeRule.onNodeWithContentDescription("Photo by Bea").performClick()

        composeRule.onNodeWithContentDescription("Close").assertIsDisplayed().performClick()
        composeRule.onNodeWithContentDescription("Close").assertDoesNotExist()
        assertEquals(emptyList<String>(), calls.filter { it.startsWith("open:") })
    }

    @Test
    fun in_the_post_a_photo_that_is_still_being_processed_does_not_open() {
        val processing = TestPosts.post(media = listOf(RemoteImage("asset-1", null, null)))
        showDetail(PostDetailUiState(isLoading = false, post = processing))

        composeRule.onNodeWithContentDescription("Photo by Bea").performClick()

        composeRule.onNodeWithContentDescription("Close").assertDoesNotExist()
    }

    @Test
    fun a_post_that_is_gone_does_not_say_why() {
        showDetail(PostDetailUiState(isLoading = false, isUnavailable = true))

        composeRule.onNodeWithText("Post not available").assertIsDisplayed()
    }

    @Test
    fun a_post_that_could_not_load_can_be_retried() {
        showDetail(PostDetailUiState(isLoading = false, loadError = AppError.Network))

        composeRule.onNodeWithText("Try again").performClick()

        assertEquals(listOf("refresh"), calls)
    }

    @Test
    fun without_comments_it_says_so() {
        showDetail(detail())

        composeRule.waitUntil(WAIT_MILLIS) {
            runCatching {
                composeRule.onAllNodes(
                    hasScrollToNodeAction(),
                ).onFirst().performScrollToNode(hasText("No comments yet."))
            }.isSuccess
        }
        composeRule.onNodeWithText("No comments yet.").assertIsDisplayed()
    }

    @Test
    fun a_comment_can_only_be_sent_with_something_written() {
        showDetail(detail())

        composeRule.onNodeWithContentDescription("Send comment").assertIsNotEnabled()
    }

    @Test
    fun a_comment_is_sent_from_its_button() {
        showDetail(detail(), text = TextFieldState("Qué bonita"))

        composeRule.onNodeWithContentDescription("Send comment").assertIsEnabled().performClick()

        assertEquals(listOf("send"), calls)
    }

    @Test
    fun people_are_offered_while_writing_a_mention() {
        showDetail(detail(), text = TextFieldState("@be"), suggestions = listOf(TestSocial.user()))

        composeRule.onNodeWithText("@bea · Madrid").performClick()

        assertEquals(listOf("mention:bea"), calls)
    }

    /** Ana is the one looking. Only the dots are at hand: what they offer opens on touching them. */
    private fun showComments(vararg comments: Comment) {
        showDetail(detail(), comments.toList())
        composeRule.waitUntil(WAIT_MILLIS) {
            runCatching {
                composeRule.onAllNodes(hasScrollToNodeAction()).onFirst()
                    .performScrollToNode(hasText(comments.last().text))
            }.isSuccess
        }
    }

    private fun openMenuOf(name: String) =
        composeRule.onNodeWithContentDescription("Options for the comment by $name").performClick()

    @Test
    fun a_comment_says_who_wrote_it_and_its_options_are_behind_three_dots() {
        showComments(TestPosts.comment("1", TestSocial.user("dani-id", "dani", "Dani"), "Soy Dani"))

        composeRule.onNodeWithText("Dani").assertIsDisplayed()
        composeRule.onNodeWithText("Soy Dani").assertIsDisplayed()
        composeRule.onNodeWithText("Report").assertDoesNotExist()
        composeRule.onNodeWithText("Delete").assertDoesNotExist()
    }

    @Test
    fun your_own_comment_can_only_be_deleted_and_it_asks_first() {
        showComments(TestPosts.comment("1", TestSocial.user("ana-id", "ana", "Ana"), "Soy Ana", canDelete = true))

        openMenuOf("Ana")

        composeRule.onNodeWithText("Report").assertDoesNotExist()
        composeRule.onNodeWithText("Delete").performClick()
        assertEquals(listOf(PostDialog.ConfirmDeleteComment::class.simpleName), calls)
        composeRule.onNodeWithText("Delete").assertDoesNotExist()
    }

    @Test
    fun the_comment_of_someone_else_can_only_be_reported() {
        showComments(TestPosts.comment("1", TestSocial.user("dani-id", "dani", "Dani"), "Soy Dani"))

        openMenuOf("Dani")

        composeRule.onNodeWithText("Delete").assertDoesNotExist()
        composeRule.onNodeWithText("Report").performClick()
        assertEquals(listOf(PostDialog.ReportComment::class.simpleName), calls)
    }

    /** The server lets the author of the post delete what others write in it. */
    @Test
    fun in_your_own_post_the_comment_of_someone_else_can_be_deleted_and_reported() {
        showComments(TestPosts.comment("1", TestSocial.user("dani-id", "dani", "Dani"), "Soy Dani", canDelete = true))

        openMenuOf("Dani")

        composeRule.onNodeWithText("Report").assertIsDisplayed()
        composeRule.onNodeWithText("Delete").performClick()
        assertEquals(listOf(PostDialog.ConfirmDeleteComment::class.simpleName), calls)
    }

    @Test
    fun each_comment_has_its_own_menu() {
        showComments(
            TestPosts.comment("1", TestSocial.user("ana-id", "ana", "Ana"), "Soy Ana", canDelete = true),
            TestPosts.comment("2", TestSocial.user("dani-id", "dani", "Dani"), "Soy Dani"),
        )

        openMenuOf("Dani")
        composeRule.onNodeWithText("Report").performClick()

        assertEquals(listOf(PostDialog.ReportComment::class.simpleName), calls)
    }

    @Test
    fun a_mention_in_a_post_opens_that_profile() {
        val post = TestPosts.post(text = "@ana", mentions = listOf(Mention("ana", "ana-id")))
        showDetail(PostDetailUiState(isLoading = false, post = post))

        composeRule.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText("@ana"))
        composeRule.onNodeWithText("@ana").performClick()

        assertEquals(listOf("user:ana-id"), calls)
    }

    private val createActions = CreatePostActions(
        onPickPhotos = { calls += "pick" },
        onRemovePhoto = { calls += "remove:$it" },
        onSelectVisibility = { calls += "visibility:$it" },
        onTogglePet = { calls += "pet:$it" },
        onChooseMention = { calls += "mention:${it.username}" },
        onPublish = { calls += "publish" },
    )

    private fun showCreate(uiState: CreatePostUiState) {
        composeRule.setContent {
            GeoWoofTheme(dynamicColor = false) {
                CreatePostContent(
                    uiState = uiState,
                    text = TextFieldState(),
                    suggestions = emptyList(),
                    actions = createActions,
                )
            }
        }
    }

    @Test
    fun without_photos_it_cannot_be_published() {
        showCreate(CreatePostUiState())

        composeRule.onNodeWithText("Publish").assertIsNotEnabled()
        composeRule.onNodeWithText("Add photos").performClick()

        assertEquals(listOf("pick"), calls)
    }

    @Test
    fun with_a_photo_it_can_be_published_and_the_photo_removed() {
        showCreate(CreatePostUiState(photos = listOf(ChosenPhoto("content://1"))))

        composeRule.onNodeWithContentDescription("Remove photo").performClick()
        composeRule.onNodeWithText("Publish").assertIsEnabled().performClick()

        assertEquals(listOf("remove:content://1", "publish"), calls)
    }

    @Test
    fun it_says_who_can_see_it_and_which_pets_appear() {
        showCreate(CreatePostUiState(pets = listOf(TestPets.pet())))

        composeRule.onNodeWithText("Friends").performScrollTo().performClick()
        composeRule.onNodeWithText("Luna").performScrollTo().performClick()

        assertEquals(listOf("visibility:${PostVisibility.Friends}", "pet:luna"), calls)
    }

    @Test
    fun while_publishing_the_buttons_wait() {
        showCreate(CreatePostUiState(photos = listOf(ChosenPhoto("content://1")), isPublishing = true))

        composeRule.onNodeWithText("Publishing…").assertIsNotEnabled()
        composeRule.onNodeWithText("Add photos").assertIsNotEnabled()
    }

    @Serializable
    data object StartRoute

    /** The ViewModel reads the route argument by property name: this checks it with real navigation. */
    @Test
    fun the_post_receives_its_id_from_the_route() {
        val posts = FakePostsRepository()
        val people = FakePeopleRepository()
        val auth = FakeAuthRepository(SessionState.SignedIn(TestUsers.user()))
        composeRule.setContent {
            val navController = rememberNavController()
            NavHost(navController = navController, startDestination = StartRoute) {
                composable<StartRoute> {
                    LaunchedEffect(Unit) { navController.navigate(PostRoute("post-9")) }
                }
                composable<PostRoute> { entry ->
                    viewModel { PostDetailViewModel(createSavedStateHandle(), auth, posts, people, ContentChanges()) }
                    Text(entry.toRoute<PostRoute>().postId)
                }
            }
        }

        composeRule.waitUntil(WAIT_MILLIS) {
            composeRule.onAllNodes(hasText("post-9")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L
    }
}
