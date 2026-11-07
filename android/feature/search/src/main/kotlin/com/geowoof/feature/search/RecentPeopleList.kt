package com.geowoof.feature.search

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.geowoof.core.designsystem.component.EmptyState
import com.geowoof.core.designsystem.component.ErrorState
import com.geowoof.core.designsystem.component.GeoWoofIllustrations
import com.geowoof.core.designsystem.component.SkeletonList
import com.geowoof.core.designsystem.icon.GeoWoofIcons
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.core.model.UserSummary
import com.geowoof.core.ui.UserRow

/** What the screen shows before searching. Without anyone yet, it explains what the field is for. */
@Composable
internal fun RecentPeopleList(
    uiState: SearchUiState,
    onOpenUser: (userId: String) -> Unit,
    onRemove: (userId: String) -> Unit,
    onRemoveAll: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        uiState.recent.isNotEmpty() -> LazyColumn(modifier = modifier.fillMaxSize()) {
            item { RecentHeader(onRemoveAll) }
            items(uiState.recent, key = UserSummary::id) { user ->
                UserRow(
                    user = user,
                    onClick = { onOpenUser(user.id) },
                    trailing = {
                        IconButton(onClick = { onRemove(user.id) }) {
                            Icon(
                                painter = painterResource(GeoWoofIcons.Close),
                                contentDescription = stringResource(
                                    R.string.feature_search_remove_recent,
                                    user.displayName,
                                ),
                            )
                        }
                    },
                )
            }
        }

        uiState.isLoadingRecent -> SkeletonList(modifier = modifier, rows = SKELETON_ROWS)

        uiState.hasRecentFailed -> ErrorState(
            title = stringResource(R.string.feature_search_recent_failed),
            message = "",
            retryLabel = stringResource(R.string.feature_search_retry),
            onRetry = onRetry,
            modifier = modifier,
        )

        else -> EmptyState(
            icon = GeoWoofIcons.PersonSearch,
            title = stringResource(R.string.feature_search_search_empty_title),
            message = stringResource(R.string.feature_search_search_empty_message),
            modifier = modifier,
            illustration = GeoWoofIllustrations.EmptySearch,
        )
    }
}

@Composable
private fun RecentHeader(onRemoveAll: () -> Unit, modifier: Modifier = Modifier) {
    val spacing = GeoWoofTheme.spacing
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = spacing.large, end = spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.feature_search_recent),
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
            style = MaterialTheme.typography.titleSmall,
        )
        TextButton(onClick = onRemoveAll) { Text(stringResource(R.string.feature_search_remove_all_recent)) }
    }
}

private const val SKELETON_ROWS = 3
