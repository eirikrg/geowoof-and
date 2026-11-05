package com.geowoof.feature.profile

import com.geowoof.core.model.PeopleListKind
import com.geowoof.core.model.ReportReason

/**
 * What the profile can do, so that its composables do not take a long list of callbacks. Your own profile
 * and that of someone else use different parts of it, and what does not apply stays empty.
 */
@Suppress("LongParameterList")
internal class ProfileActions(
    val onBack: (() -> Unit)? = null,
    val onRefresh: () -> Unit = {},
    val onOpenSettings: (() -> Unit)? = null,
    val onEditProfile: () -> Unit = {},
    val onOpenPeople: (PeopleListKind) -> Unit = {},
    val onOpenRequests: () -> Unit = {},
    val onOpenWalk: (walkId: String) -> Unit = {},
    val onOpenPet: ((petId: String) -> Unit)? = null,
    val onAddPet: (() -> Unit)? = null,
    val onFollow: () -> Unit = {},
    val onUnfollow: () -> Unit = {},
    val onAcceptRequest: () -> Unit = {},
    val onRejectRequest: () -> Unit = {},
    val onShowDialog: (ProfileDialog) -> Unit = {},
    val onDismissDialog: () -> Unit = {},
    val onConfirmFollowBack: () -> Unit = {},
    val onConfirmUnfollow: () -> Unit = {},
    val onConfirmRemoveFollower: () -> Unit = {},
    val onConfirmBlock: () -> Unit = {},
    val onSelectReportReason: (ReportReason) -> Unit = {},
    val onConfirmReport: () -> Unit = {},
    val onVerifyEmail: () -> Unit = {},
    val onMessageShown: () -> Unit = {},
)
