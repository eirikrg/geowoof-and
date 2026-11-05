package com.geowoof.feature.social.profile

import com.geowoof.core.model.ReportReason

/** What the profile can do, so that its composables do not take a long list of callbacks. */
@Suppress("LongParameterList")
internal class UserProfileActions(
    val onBack: () -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onSendRequest: () -> Unit = {},
    val onAcceptRequest: () -> Unit = {},
    val onRejectRequest: () -> Unit = {},
    val onCancelRequest: () -> Unit = {},
    val onShowDialog: (ProfileDialog) -> Unit = {},
    val onDismissDialog: () -> Unit = {},
    val onConfirmRemoveFriend: () -> Unit = {},
    val onConfirmBlock: () -> Unit = {},
    val onSelectReportReason: (ReportReason) -> Unit = {},
    val onConfirmReport: () -> Unit = {},
    val onVerifyEmail: () -> Unit = {},
    val onOpenWalk: (walkId: String) -> Unit = {},
    val onMessageShown: () -> Unit = {},
)
