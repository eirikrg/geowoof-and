package com.geowoof.feature.social.post

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.geowoof.core.designsystem.component.EmptyState
import com.geowoof.core.designsystem.icon.GeoWoofIcons
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.core.model.AppError
import com.geowoof.core.model.Comment
import com.geowoof.core.model.Post
import com.geowoof.core.model.ReportReason
import com.geowoof.core.model.UserSummary
import com.geowoof.core.ui.MentionText
import com.geowoof.core.ui.RemoteAvatar
import com.geowoof.core.ui.UserRow
import com.geowoof.core.ui.userMessage
import com.geowoof.feature.social.R
import com.geowoof.feature.social.rememberPostMessages
import kotlinx.coroutines.flow.flowOf

@Composable
internal fun PostDetailScreen(
    onBack: () -> Unit,
    onOpenUser: (userId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PostDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val interactions = viewModel.interactions
    val interactionState by interactions.state.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggester.suggestions.collectAsStateWithLifecycle()
    val commentFailure by viewModel.commentFailure.collectAsStateWithLifecycle()
    val currentOnBack by rememberUpdatedState(onBack)
    LaunchedEffect(uiState.isFinished) {
        if (uiState.isFinished) currentOnBack()
    }
    PostDetailContent(
        uiState = uiState,
        comments = viewModel.comments.collectAsLazyPagingItems(),
        myUserId = viewModel.myUserId,
        interactionState = interactionState,
        reportDetails = interactions.reportDetails,
        commentText = viewModel.commentText,
        suggestions = suggestions,
        commentFailure = commentFailure,
        actions = PostDetailActions(
            onBack = onBack,
            onRefresh = viewModel::refresh,
            onSendComment = viewModel::onSendComment,
            onChooseMention = viewModel.suggester::onChoose,
            onSelectReportReason = interactions::onSelectReportReason,
            onConfirmDialog = interactions::onConfirmDialog,
            onDismissDialog = interactions::onDismissDialog,
            onDismissMessage = interactions::onMessageShown,
            onDismissCommentFailure = viewModel::onCommentFailureShown,
            card = PostCardActions(
                onOpenUser = onOpenUser,
                onToggleLike = interactions::onToggleLike,
                onToggleRepost = interactions::onToggleRepost,
                onToggleSave = interactions::onToggleSave,
                onShowDialog = interactions::onShowDialog,
            ),
        ),
        modifier = modifier,
    )
}

@Suppress("LongParameterList")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PostDetailContent(
    uiState: PostDetailUiState,
    comments: LazyPagingItems<Comment>,
    myUserId: String?,
    interactionState: PostInteractionState,
    reportDetails: TextFieldState,
    commentText: TextFieldState,
    suggestions: List<UserSummary>,
    commentFailure: AppError?,
    actions: PostDetailActions,
    modifier: Modifier = Modifier,
) {
    val snackbars = rememberPostMessages(interactionState, actions.onDismissMessage)
    ShowCommentFailure(commentFailure, snackbars, actions.onDismissCommentFailure)
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbars) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feature_social_post_title)) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(
                            painter = painterResource(GeoWoofIcons.ArrowBack),
                            contentDescription = stringResource(R.string.feature_social_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
        val post = uiState.post
        when {
            uiState.isUnavailable -> EmptyState(
                icon = GeoWoofIcons.PhotoLibrary,
                title = stringResource(R.string.feature_social_post_unavailable_title),
                message = stringResource(R.string.feature_social_post_unavailable_message),
                modifier = contentModifier,
            )

            post == null && uiState.loadError != null -> Column(
                modifier = contentModifier.padding(GeoWoofTheme.spacing.large),
                verticalArrangement = Arrangement.spacedBy(GeoWoofTheme.spacing.medium, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(uiState.loadError.userMessage())
                OutlinedButton(onClick = actions.onRefresh) { Text(stringResource(R.string.feature_social_retry)) }
            }

            post == null -> Box(contentModifier, contentAlignment = Alignment.Center) { CircularProgressIndicator() }

            else -> Column(modifier = contentModifier.imePadding()) {
                PostAndComments(
                    post = post,
                    comments = comments,
                    myUserId = myUserId,
                    actions = actions.card,
                    modifier = Modifier.weight(1f),
                )
                HorizontalDivider()
                CommentField(commentText, suggestions, uiState.isSendingComment, actions)
            }
        }
    }
    PostDialogs(
        state = interactionState,
        reportDetails = reportDetails,
        onSelectReason = actions.onSelectReportReason,
        onConfirm = actions.onConfirmDialog,
        onDismiss = actions.onDismissDialog,
    )
}

@Composable
private fun ShowCommentFailure(error: AppError?, snackbars: SnackbarHostState, onDismiss: () -> Unit) {
    val message = error?.userMessage()
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(message) {
        if (message != null) {
            snackbars.showSnackbar(message)
            currentOnDismiss()
        }
    }
}

@Composable
private fun PostAndComments(
    post: Post,
    comments: LazyPagingItems<Comment>,
    myUserId: String?,
    actions: PostCardActions,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxSize()) {
        item(key = "post") { PostCard(post, repostedBy = null, myUserId = myUserId, actions = actions) }
        item(key = "divider") { HorizontalDivider() }
        if (comments.itemCount == 0 && comments.loadState.refresh !is LoadState.Loading) {
            item(key = "empty") {
                Text(
                    text = stringResource(
                        if (comments.loadState.refresh is LoadState.Error) {
                            R.string.feature_social_comments_failed
                        } else {
                            R.string.feature_social_no_comments
                        },
                    ),
                    modifier = Modifier.padding(GeoWoofTheme.spacing.large),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(comments.itemCount, key = comments.itemKey(Comment::id)) { index ->
            comments[index]?.let { comment -> CommentRow(comment, myUserId, actions) }
        }
    }
}

@Composable
private fun CommentRow(comment: Comment, myUserId: String?, actions: PostCardActions, modifier: Modifier = Modifier) {
    ListItem(
        modifier = modifier,
        headlineContent = {
            Text(
                text = comment.author.displayName,
                modifier = Modifier.clickable { actions.onOpenUser(comment.author.id) },
                style = MaterialTheme.typography.titleSmall,
            )
        },
        supportingContent = {
            MentionText(text = comment.text, mentions = comment.mentions, onOpenUser = actions.onOpenUser)
        },
        leadingContent = {
            RemoteAvatar(
                image = comment.author.avatar,
                fallbackIcon = GeoWoofIcons.Person,
                contentDescription = null,
                size = CommentAvatarSize,
            )
        },
        trailingContent = {
            if (comment.canDelete) {
                TextButton(onClick = { actions.onShowDialog(PostDialog.ConfirmDeleteComment(comment)) }) {
                    Text(stringResource(R.string.feature_social_delete))
                }
            } else if (comment.author.id != myUserId) {
                TextButton(onClick = { actions.onShowDialog(PostDialog.ReportComment(comment)) }) {
                    Text(stringResource(R.string.feature_social_report))
                }
            }
        },
    )
}

@Composable
private fun CommentField(
    text: TextFieldState,
    suggestions: List<UserSummary>,
    isSending: Boolean,
    actions: PostDetailActions,
    modifier: Modifier = Modifier,
) {
    val spacing = GeoWoofTheme.spacing
    Column(modifier = modifier) {
        suggestions.forEach { user ->
            UserRow(user = user, onClick = { actions.onChooseMention(user) })
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.medium, vertical = spacing.small),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.small),
        ) {
            OutlinedTextField(
                state = text,
                modifier = Modifier.weight(1f),
                label = { Text(stringResource(R.string.feature_social_comment_label)) },
                inputTransformation = InputTransformation.maxLength(MAX_COMMENT_LENGTH),
                lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = MAX_COMMENT_LINES),
            )
            IconButton(onClick = actions.onSendComment, enabled = !isSending && text.text.isNotBlank()) {
                Icon(
                    painter = painterResource(GeoWoofIcons.Send),
                    contentDescription = stringResource(R.string.feature_social_send_comment),
                )
            }
        }
    }
}

private const val MAX_COMMENT_LENGTH = 1000
private const val MAX_COMMENT_LINES = 4
private val CommentAvatarSize = 36.dp

@PreviewLightDark
@Composable
private fun PostUnavailablePreview() {
    val finished = LoadStates(LoadState.NotLoading(true), LoadState.NotLoading(true), LoadState.NotLoading(true))
    GeoWoofTheme(dynamicColor = false) {
        PostDetailContent(
            uiState = PostDetailUiState(isLoading = false, isUnavailable = true),
            comments = flowOf(PagingData.empty<Comment>(finished)).collectAsLazyPagingItems(),
            myUserId = null,
            interactionState = PostInteractionState(),
            reportDetails = remember { TextFieldState() },
            commentText = remember { TextFieldState() },
            suggestions = emptyList(),
            commentFailure = null,
            actions = PostDetailActions(),
        )
    }
}
