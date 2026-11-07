package com.geowoof.feature.social

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import com.geowoof.core.data.repository.RemoteLoadException
import com.geowoof.core.designsystem.component.EmptyState
import com.geowoof.core.designsystem.component.ErrorState
import com.geowoof.core.designsystem.component.GeoWoofIllustrations
import com.geowoof.core.designsystem.component.SkeletonBox
import com.geowoof.core.designsystem.icon.GeoWoofIcons
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.core.model.Post
import com.geowoof.core.ui.PostGridItem
import com.geowoof.core.ui.userMessage

/**
 * The most recent of what anyone can see: what the server sends here is only what people with a public profile publish
 * for everyone, and never of someone blocked. A pull needs something that scrolls, also without photos.
 */
@Composable
internal fun ExploreGrid(posts: LazyPagingItems<Post>, onOpenPost: (Post) -> Unit, modifier: Modifier = Modifier) {
    val spacing = GeoWoofTheme.spacing
    val refresh = posts.loadState.refresh
    val isEmpty = posts.itemCount == 0
    val loading = stringResource(com.geowoof.core.designsystem.R.string.core_designsystem_loading)
    LazyVerticalGrid(
        columns = GridCells.Fixed(COLUMNS),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = spacing.large,
            end = spacing.large,
            top = spacing.medium,
            bottom = BottomPadding,
        ),
        horizontalArrangement = Arrangement.spacedBy(spacing.small),
        verticalArrangement = Arrangement.spacedBy(spacing.small),
    ) {
        when {
            isEmpty && refresh is LoadState.Loading -> items(SKELETON_CELLS) {
                SkeletonBox(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clearAndSetSemantics { contentDescription = loading },
                    shape = MaterialTheme.shapes.small,
                )
            }

            isEmpty && refresh is LoadState.Error -> item(span = { GridItemSpan(maxLineSpan) }) {
                ErrorState(
                    title = stringResource(R.string.feature_social_explore_error_title),
                    message = (refresh.error as? RemoteLoadException)?.error?.userMessage().orEmpty(),
                    retryLabel = stringResource(R.string.feature_social_retry),
                    onRetry = posts::retry,
                )
            }

            isEmpty -> item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyState(
                    icon = GeoWoofIcons.PhotoLibrary,
                    title = stringResource(R.string.feature_social_explore_empty_title),
                    message = stringResource(R.string.feature_social_explore_empty_message),
                    illustration = GeoWoofIllustrations.EmptyPhotos,
                )
            }

            else -> items(posts.itemCount, key = posts.itemKey(Post::id)) { index ->
                posts[index]?.let { post ->
                    PostGridItem(
                        image = post.media.firstOrNull(),
                        isRepost = false,
                        onClick = { onOpenPost(post) },
                    )
                }
            }
        }
    }
}

private const val COLUMNS = 3
private const val SKELETON_CELLS = 12

/** Room for the button, which floats over the last row. */
private val BottomPadding = 88.dp
