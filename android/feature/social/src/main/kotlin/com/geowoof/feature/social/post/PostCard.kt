package com.geowoof.feature.social.post

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewFontScale
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.geowoof.core.designsystem.icon.GeoWoofIcons
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.core.model.Mention
import com.geowoof.core.model.Post
import com.geowoof.core.model.PostVisibility
import com.geowoof.core.model.RemoteImage
import com.geowoof.core.model.UserSummary
import com.geowoof.core.ui.MentionText
import com.geowoof.core.ui.PostImage
import com.geowoof.core.ui.RemoteAvatar
import com.geowoof.core.ui.formattedIn
import com.geowoof.feature.social.R
import java.time.Instant
import java.time.ZoneId

/** A post, with who published it and what can be done with it. A repost always shows its original author. */
@Composable
internal fun PostCard(
    post: Post,
    repostedBy: UserSummary?,
    myUserId: String?,
    actions: PostCardActions,
    modifier: Modifier = Modifier,
) {
    val spacing = GeoWoofTheme.spacing
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = spacing.medium),
    ) {
        repostedBy?.let { RepostLine(it, actions.onOpenUser) }
        PostHeader(post, post.author.id == myUserId, actions)
        PostImages(post, onClick = { actions.onOpenPost(post) })
        PostButtons(post, actions)
        post.text?.let { text ->
            MentionText(
                text = text,
                mentions = post.mentions,
                onOpenUser = actions.onOpenUser,
                modifier = Modifier.padding(horizontal = spacing.medium, vertical = spacing.extraSmall),
            )
        }
        PetChips(post)
    }
}

@Composable
private fun RepostLine(user: UserSummary, onOpenUser: (userId: String) -> Unit, modifier: Modifier = Modifier) {
    val spacing = GeoWoofTheme.spacing
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onOpenUser(user.id) }
            .padding(horizontal = spacing.medium, vertical = spacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.small),
    ) {
        Icon(
            painter = painterResource(GeoWoofIcons.Repeat),
            contentDescription = null,
            modifier = Modifier.size(SmallIconSize),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.feature_social_reposted_by, user.displayName),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PostHeader(post: Post, isMine: Boolean, actions: PostCardActions, modifier: Modifier = Modifier) {
    val spacing = GeoWoofTheme.spacing
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = spacing.medium, top = spacing.small, bottom = spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable { actions.onOpenUser(post.author.id) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.small),
        ) {
            RemoteAvatar(
                image = post.author.avatar,
                fallbackIcon = GeoWoofIcons.Person,
                contentDescription = null,
                size = AvatarSize,
            )
            Column {
                Text(post.author.displayName, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = post.createdAt.formattedIn(ZoneId.systemDefault().id),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (post.visibility == PostVisibility.Friends) {
            Icon(
                painter = painterResource(GeoWoofIcons.Groups),
                contentDescription = stringResource(R.string.feature_social_visibility_friends),
                modifier = Modifier.size(SmallIconSize),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        PostMenu(post, isMine, actions.onShowDialog)
    }
}

@Composable
private fun PostMenu(post: Post, isMine: Boolean, onShowDialog: (PostDialog) -> Unit, modifier: Modifier = Modifier) {
    var isOpen by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        IconButton(onClick = { isOpen = true }) {
            Icon(
                painter = painterResource(GeoWoofIcons.MoreVert),
                contentDescription = stringResource(R.string.feature_social_more_options),
            )
        }
        DropdownMenu(expanded = isOpen, onDismissRequest = { isOpen = false }) {
            if (isMine) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.feature_social_delete)) },
                    leadingIcon = { Icon(painterResource(GeoWoofIcons.Delete), contentDescription = null) },
                    onClick = {
                        isOpen = false
                        onShowDialog(PostDialog.ConfirmDeletePost(post))
                    },
                )
            } else {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.feature_social_report)) },
                    leadingIcon = { Icon(painterResource(GeoWoofIcons.Flag), contentDescription = null) },
                    onClick = {
                        isOpen = false
                        onShowDialog(PostDialog.ReportPost(post))
                    },
                )
            }
        }
    }
}

@Composable
private fun PostImages(post: Post, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val pager = rememberPagerState { post.media.size }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f),
    ) {
        HorizontalPager(state = pager) { page ->
            PostImage(
                image = post.media[page],
                contentDescription = stringResource(R.string.feature_social_photo_of, post.author.displayName),
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clickable(onClick = onClick),
            )
        }
        if (post.media.size > 1) {
            Text(
                text = stringResource(R.string.feature_social_photo_position, pager.currentPage + 1, post.media.size),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(GeoWoofTheme.spacing.small)
                    .background(MaterialTheme.colorScheme.inverseSurface, RoundedCornerShape(PositionCorner))
                    .padding(horizontal = GeoWoofTheme.spacing.small),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.inverseOnSurface,
            )
        }
    }
}

@Composable
private fun PostButtons(post: Post, actions: PostCardActions, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = GeoWoofTheme.spacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CountedButton(
            icon = if (post.likedByMe) GeoWoofIcons.FavoriteFilled else GeoWoofIcons.Favorite,
            description = stringResource(
                if (post.likedByMe) R.string.feature_social_unlike else R.string.feature_social_like,
            ),
            count = post.likeCount,
            isActive = post.likedByMe,
            onClick = { actions.onToggleLike(post) },
        )
        CountedButton(
            icon = GeoWoofIcons.ChatBubble,
            description = stringResource(R.string.feature_social_comments),
            count = post.commentCount,
            onClick = { actions.onOpenPost(post) },
        )
        // What cannot be reposted does not offer it, unless it is already reposted and can be undone.
        if (post.canRepost || post.repostedByMe) {
            CountedButton(
                icon = GeoWoofIcons.Repeat,
                description = stringResource(
                    if (post.repostedByMe) R.string.feature_social_undo_repost else R.string.feature_social_repost,
                ),
                count = post.repostCount,
                isActive = post.repostedByMe,
                onClick = { actions.onToggleRepost(post) },
            )
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = { actions.onToggleSave(post) }) {
            Icon(
                painter = painterResource(if (post.savedByMe) GeoWoofIcons.BookmarkFilled else GeoWoofIcons.Bookmark),
                contentDescription = stringResource(
                    if (post.savedByMe) R.string.feature_social_unsave else R.string.feature_social_save,
                ),
            )
        }
    }
}

@Composable
private fun CountedButton(
    icon: Int,
    description: String,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClick) {
            Icon(
                painter = painterResource(icon),
                contentDescription = description,
                tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        }
        if (count > 0) Text(count.toString(), style = MaterialTheme.typography.labelMedium)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PetChips(post: Post, modifier: Modifier = Modifier) {
    if (post.pets.isEmpty()) return
    val spacing = GeoWoofTheme.spacing
    FlowRow(
        modifier = modifier.padding(horizontal = spacing.medium),
        horizontalArrangement = Arrangement.spacedBy(spacing.small),
    ) {
        post.pets.forEach { pet ->
            AssistChip(
                onClick = {},
                enabled = false,
                label = { Text(pet.name) },
                leadingIcon = {
                    RemoteAvatar(
                        image = pet.avatar,
                        fallbackIcon = GeoWoofIcons.Pets,
                        contentDescription = null,
                        size = SmallIconSize,
                    )
                },
            )
        }
    }
}

private val AvatarSize = 40.dp
private val SmallIconSize = 18.dp
private val PositionCorner = 8.dp

@PreviewLightDark
@PreviewFontScale
@Composable
private fun PostCardPreview() {
    val author = UserSummary("1", "bea", "Bea", "Madrid", null)
    GeoWoofTheme(dynamicColor = false) {
        PostCard(
            post = Post(
                id = "1",
                author = author,
                text = "En el Retiro con @ana",
                visibility = PostVisibility.Friends,
                media = listOf(RemoteImage("1", null, null), RemoteImage("2", null, null)),
                pets = emptyList(),
                mentions = listOf(Mention("ana", "2")),
                likeCount = 3,
                commentCount = 1,
                repostCount = 0,
                likedByMe = true,
                savedByMe = false,
                repostedByMe = false,
                canRepost = true,
                createdAt = Instant.parse("2026-11-05T10:00:00Z"),
            ),
            repostedBy = author,
            myUserId = "2",
            actions = PostCardActions(),
        )
    }
}
