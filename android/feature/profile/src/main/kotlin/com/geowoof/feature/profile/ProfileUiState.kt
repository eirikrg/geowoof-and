package com.geowoof.feature.profile

import com.geowoof.core.model.AppError
import com.geowoof.core.model.Pet
import com.geowoof.core.model.Profile
import com.geowoof.core.model.ProfileCounts
import com.geowoof.core.model.Relationship
import com.geowoof.core.model.RelationshipStatus
import com.geowoof.core.model.ReportReason
import com.geowoof.core.model.WalkTotals

enum class ProfileDialog {
    ConfirmFollowBack,
    ConfirmUnfollow,
    ConfirmRemoveFollower,
    ConfirmBlock,
    Report,
    VerifyEmail,
}

/** What the profile shows, yours or someone else's: they are the same screen. */
data class ProfileUiState(
    val isLoading: Boolean = true,
    val profile: Profile? = null,
    val relationship: Relationship? = null,
    val counts: ProfileCounts? = null,
    val totals: WalkTotals? = null,
    val pets: List<Pet> = emptyList(),
    /** In your own profile: the people waiting for an answer, and the requests that you have sent. */
    val receivedRequests: Int = 0,
    val sentRequests: Int = 0,
    /** It does not exist, or there is a block in some direction: the server does not say which. */
    val isUnavailable: Boolean = false,
    /** It could not be loaded, and retrying may help. */
    val loadError: AppError? = null,
    /** An action is waiting for the server. */
    val isActing: Boolean = false,
    val actionError: AppError? = null,
    val dialog: ProfileDialog? = null,
    val reportReason: ReportReason? = null,
    val wasReported: Boolean = false,
    /** Blocked: there is nothing left to show here. */
    val isFinished: Boolean = false,
) {
    val isOwn: Boolean get() = relationship?.status == RelationshipStatus.Self

    /** A private profile that you do not follow: only its @username, photo, bio and numbers. */
    val isRestricted: Boolean
        get() = profile?.isPrivate == true && relationship?.status !in STATUSES_WITH_ACCESS

    val canSeeContent: Boolean get() = profile != null && !isRestricted

    /** Anyone sees the numbers, but the lists are only for that person and their friends. */
    val canOpenLists: Boolean get() = relationship?.status in STATUSES_WITH_LISTS

    private companion object {
        val STATUSES_WITH_ACCESS = setOf(
            RelationshipStatus.Self,
            RelationshipStatus.Following,
            RelationshipStatus.Friends,
        )
        val STATUSES_WITH_LISTS = setOf(RelationshipStatus.Self, RelationshipStatus.Friends)
    }
}
