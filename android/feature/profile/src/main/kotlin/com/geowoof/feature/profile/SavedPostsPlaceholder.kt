package com.geowoof.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import com.geowoof.core.designsystem.component.EmptyState
import com.geowoof.core.designsystem.component.ErrorState
import com.geowoof.core.designsystem.component.GeoWoofIllustrations
import com.geowoof.core.designsystem.component.SkeletonBox
import com.geowoof.core.designsystem.component.SkeletonList
import com.geowoof.core.designsystem.icon.GeoWoofIcons
import com.geowoof.core.designsystem.theme.GeoWoofTheme

/** What the tab shows while there are no cards: they are on their way, they could not be loaded or there are none. */
@Composable
internal fun SavedPostsPlaceholder(refresh: LoadState, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    when (refresh) {
        // Where the first card will be, so that nothing jumps when they come.
        is LoadState.Loading -> SkeletonList(
            modifier = modifier.padding(top = GeoWoofTheme.spacing.small),
            rows = SKELETON_ROWS,
            row = { SavedPostSkeleton() },
        )

        is LoadState.Error -> ErrorState(
            title = stringResource(R.string.feature_profile_saved_failed),
            message = "",
            retryLabel = stringResource(R.string.feature_profile_retry),
            onRetry = onRetry,
            modifier = modifier,
        )

        is LoadState.NotLoading -> EmptyState(
            icon = GeoWoofIcons.Bookmark,
            title = stringResource(R.string.feature_profile_no_saved_title),
            message = stringResource(R.string.feature_profile_no_saved),
            modifier = modifier,
            illustration = GeoWoofIllustrations.EmptySaved,
        )
    }
}

@Composable
private fun SavedPostSkeleton(modifier: Modifier = Modifier) {
    val spacing = GeoWoofTheme.spacing
    SavedPostContainer(modifier = modifier) {
        SkeletonBox(Modifier.size(SavedPostPhotoSize), MaterialTheme.shapes.medium)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.small)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.small),
            ) {
                SkeletonBox(Modifier.size(SavedPostAvatarSize), CircleShape)
                SkeletonBox(Modifier.fillMaxWidth(SKELETON_NAME_WIDTH).height(SkeletonLineHeight))
            }
            SkeletonBox(Modifier.fillMaxWidth().height(SkeletonLineHeight))
            SkeletonBox(Modifier.fillMaxWidth(SKELETON_LINE_WIDTH).height(SkeletonLineHeight))
        }
    }
}

private val SkeletonLineHeight = 12.dp
private const val SKELETON_ROWS = 3
private const val SKELETON_NAME_WIDTH = 0.5f
private const val SKELETON_LINE_WIDTH = 0.7f
