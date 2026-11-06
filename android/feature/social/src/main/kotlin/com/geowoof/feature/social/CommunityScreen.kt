package com.geowoof.feature.social

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewFontScale
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.geowoof.core.data.repository.RemoteLoadException
import com.geowoof.core.designsystem.component.EmptyState
import com.geowoof.core.designsystem.icon.GeoWoofIcons
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.core.model.ReportReason
import com.geowoof.core.model.TimelineItem
import com.geowoof.core.ui.InboxActions
import com.geowoof.core.ui.userMessage
import com.geowoof.feature.social.post.PostCard
import com.geowoof.feature.social.post.PostCardActions
import com.geowoof.feature.social.post.PostDialogs
import com.geowoof.feature.social.post.PostInteractionState
import kotlinx.coroutines.flow.flowOf

/** The feed: what the person and those they follow publish and repost. */
@Composable
fun CommunityScreen(
    onCreatePost: () -> Unit,
    onOpenPost: (postId: String) -> Unit,
    onOpenUser: (userId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CommunityViewModel = hiltViewModel(),
) {
    val interactions = viewModel.interactions
    val state by interactions.state.collectAsStateWithLifecycle()
    CommunityContent(
        feed = viewModel.feed.collectAsLazyPagingItems(),
        myUserId = viewModel.myUserId,
        state = state,
        reportDetails = interactions.reportDetails,
        actions = PostCardActions(
            onOpenPost = { onOpenPost(it.id) },
            onOpenUser = onOpenUser,
            onToggleLike = interactions::onToggleLike,
            onToggleRepost = interactions::onToggleRepost,
            onToggleSave = interactions::onToggleSave,
            onShowDialog = interactions::onShowDialog,
        ),
        onCreatePost = onCreatePost,
        onSelectReportReason = interactions::onSelectReportReason,
        onConfirmDialog = interactions::onConfirmDialog,
        onDismissDialog = interactions::onDismissDialog,
        onDismissMessage = interactions::onMessageShown,
        modifier = modifier,
    )
}

@Suppress("LongParameterList")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CommunityContent(
    feed: LazyPagingItems<TimelineItem>,
    myUserId: String?,
    state: PostInteractionState,
    reportDetails: TextFieldState,
    actions: PostCardActions,
    onCreatePost: () -> Unit,
    onSelectReportReason: (ReportReason) -> Unit,
    onConfirmDialog: () -> Unit,
    onDismissDialog: () -> Unit,
    onDismissMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbars = rememberPostMessages(state, onDismissMessage)
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbars) },
        topBar = {
            TopAppBar(title = {
                Text(stringResource(R.string.feature_social_community_title))
            }, actions = { InboxActions() })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreatePost) {
                Icon(
                    painter = painterResource(GeoWoofIcons.AddAPhoto),
                    contentDescription = stringResource(R.string.feature_social_create_post),
                )
            }
        },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = feed.loadState.refresh is LoadState.Loading && feed.itemCount > 0,
            onRefresh = feed::refresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            FeedList(feed, myUserId, actions)
        }
    }
    PostDialogs(state, reportDetails, onSelectReportReason, onConfirmDialog, onDismissDialog)
}

@Composable
private fun FeedList(
    feed: LazyPagingItems<TimelineItem>,
    myUserId: String?,
    actions: PostCardActions,
    modifier: Modifier = Modifier,
) {
    val refresh = feed.loadState.refresh
    val isEmpty = feed.itemCount == 0
    // A pull needs something that scrolls, also when the list is empty.
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = GeoWoofTheme.spacing.huge),
    ) {
        when {
            isEmpty && refresh is LoadState.Loading -> item {
                Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }

            isEmpty && refresh is LoadState.Error -> item {
                Column(
                    modifier = Modifier
                        .fillParentMaxSize()
                        .padding(GeoWoofTheme.spacing.large),
                    verticalArrangement = Arrangement.spacedBy(GeoWoofTheme.spacing.medium, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    (refresh.error as? RemoteLoadException)?.error?.let { Text(it.userMessage()) }
                    OutlinedButton(onClick = feed::retry) { Text(stringResource(R.string.feature_social_retry)) }
                }
            }

            isEmpty -> item {
                EmptyState(
                    icon = GeoWoofIcons.Groups,
                    title = stringResource(R.string.feature_social_community_empty_title),
                    message = stringResource(R.string.feature_social_community_empty_message),
                    modifier = Modifier.fillParentMaxSize(),
                )
            }

            else -> items(feed.itemCount, key = feed.itemKey(TimelineItem::id)) { index ->
                feed[index]?.let { item ->
                    PostCard(
                        post = item.post,
                        repostedBy = item.repostedBy,
                        myUserId = myUserId,
                        actions = actions,
                    )
                }
            }
        }
    }
}

/** What went wrong or has just been done with a post, once. */
@Composable
internal fun rememberPostMessages(state: PostInteractionState, onDismiss: () -> Unit): SnackbarHostState {
    val snackbars = remember { SnackbarHostState() }
    val reported = stringResource(R.string.feature_social_report_sent)
    val message = if (state.wasReported) reported else state.error?.userMessage()
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(message) {
        if (message != null) {
            snackbars.showSnackbar(message)
            currentOnDismiss()
        }
    }
    return snackbars
}

@PreviewLightDark
@PreviewFontScale
@Composable
private fun CommunityPreview() {
    val finished = LoadStates(LoadState.NotLoading(true), LoadState.NotLoading(true), LoadState.NotLoading(true))
    GeoWoofTheme(dynamicColor = false) {
        CommunityContent(
            feed = flowOf(PagingData.empty<TimelineItem>(finished)).collectAsLazyPagingItems(),
            myUserId = null,
            state = PostInteractionState(),
            reportDetails = TextFieldState(),
            actions = PostCardActions(),
            onCreatePost = {},
            onSelectReportReason = {},
            onConfirmDialog = {},
            onDismissDialog = {},
            onDismissMessage = {},
        )
    }
}
