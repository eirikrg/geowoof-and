package com.geowoof.feature.social.post

import com.geowoof.core.model.ReportReason
import com.geowoof.core.model.UserSummary

/** What the screen of a post can do, so that its composables do not take a long list of callbacks. */
@Suppress("LongParameterList")
internal class PostDetailActions(
    val onBack: () -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onSendComment: () -> Unit = {},
    val onChooseMention: (UserSummary) -> Unit = {},
    val onSelectReportReason: (ReportReason) -> Unit = {},
    val onConfirmDialog: () -> Unit = {},
    val onDismissDialog: () -> Unit = {},
    val onDismissMessage: () -> Unit = {},
    val onDismissCommentFailure: () -> Unit = {},
    val card: PostCardActions = PostCardActions(),
)
