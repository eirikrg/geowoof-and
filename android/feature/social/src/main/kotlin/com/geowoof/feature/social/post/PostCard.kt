package com.geowoof.feature.social.post

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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
import com.geowoof.core.ui.PhotoViewer
import com.geowoof.core.ui.PostImage
import com.geowoof.core.ui.RemoteAvatar
import com.geowoof.core.ui.ViewerPhoto
import com.geowoof.core.ui.relativeToNow
import com.geowoof.feature.social.R
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * A post, with who published it and what can be done with it. A repost always shows its original author.
 *
 * @param enlargesPhotos In the post itself, touching a photo enlarges it. In a list, it opens the post.
 */
@Composable
internal fun PostCard(
    post: Post,
    repostedBy: UserSummary?,
    myUserId: String?,
    actions: PostCardActions,
    modifier: Modifier = Modifier,
    enlargesPhotos: Boolean = false,
) {
    val spacing = GeoWoofTheme.spacing
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.medium, vertical = spacing.small),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(vertical = spacing.small)) {
            repostedBy?.let { RepostLine(it, actions.onOpenUser) }
            PostHeader(post, post.author.id == myUserId, actions)
            PostImages(post, onOpenPost = if (enlargesPhotos) null else ({ actions.onOpenPost(post) }))
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
                    text = post.createdAt.relativeToNow(),
                    style = MaterialTheme.typography.bodySmall,
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
private fun PostImages(post: Post, onOpenPost: (() -> Unit)?, modifier: Modifier = Modifier) {
    val pager = rememberPagerState { post.media.size }
    val scope = rememberCoroutineScope()
    var isViewerOpen by rememberSaveable { mutableStateOf(false) }
    val description = stringResource(R.string.feature_social_photo_of, post.author.displayName)
    val clickLabel = stringResource(
        if (onOpenPost != null) {
            com.geowoof.core.ui.R.string.core_ui_open_post
        } else {
            com.geowoof.core.ui.R.string.core_ui_enlarge_photo
        },
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = GeoWoofTheme.spacing.small)
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.medium),
    ) {
        HorizontalPager(state = pager) { page ->
            PostImage(
                image = post.media[page],
                contentDescription = description,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clickable(
                        enabled = onOpenPost != null || ViewerPhoto(post.media[page]).canOpen,
                        onClickLabel = clickLabel,
                    ) {
                        if (onOpenPost != null) onOpenPost() else isViewerOpen = true
                    },
            )
        }
        if (post.media.size > 1) {
            Text(
                text = stringResource(R.string.feature_social_photo_position, pager.currentPage + 1, post.media.size),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(GeoWoofTheme.spacing.small)
                    .background(Color.Black.copy(alpha = SCRIM_ALPHA), CircleShape)
                    .padding(horizontal = GeoWoofTheme.spacing.small, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
            )
        }
    }
    if (isViewerOpen) {
        PhotoViewer(
            photos = remember(post.media) { post.media.map { ViewerPhoto(it) } },
            initialPage = pager.currentPage,
            contentDescription = description,
            onDismiss = { lastPage ->
                isViewerOpen = false
                scope.launch { pager.scrollToPage(lastPage) }
            },
        )
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
        val haptics = LocalHapticFeedback.current
        CountedButton(
            icon = if (post.likedByMe) GeoWoofIcons.FavoriteFilled else GeoWoofIcons.Favorite,
            description = stringResource(
                if (post.likedByMe) R.string.feature_social_unlike else R.string.feature_social_like,
            ),
            count = post.likeCount,
            isActive = post.likedByMe,
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                actions.onToggleLike(post)
            },
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
                tint = if (post.savedByMe) MaterialTheme.colorScheme.tertiary else LocalContentColor.current,
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
    // A small jump when it is turned on, so that it answers to the finger.
    val scale by animateFloatAsState(
        targetValue = if (isActive) ACTIVE_SCALE else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "button scale",
    )
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClick) {
            Icon(
                painter = painterResource(icon),
                contentDescription = description,
                modifier = Modifier.scale(scale),
                tint = if (isActive) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
            )
        }
        if (count > 0) {
            AnimatedContent(targetState = count, label = "button count") { current ->
                Text(current.toString(), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PetChips(post: Post, modifier: Modifier = Modifier) {
    if (post.pets.isEmpty()) return
    val spacing = GeoWoofTheme.spacing
    FlowRow(
        modifier = modifier.padding(horizontal = spacing.medium, vertical = spacing.extraSmall),
        horizontalArrangement = Arrangement.spacedBy(spacing.small),
        verticalArrangement = Arrangement.spacedBy(spacing.extraSmall),
    ) {
        post.pets.forEach { pet ->
            Row(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                    .padding(
                        start = spacing.extraSmall,
                        end = spacing.medium,
                        top = spacing.extraSmall,
                        bottom = spacing.extraSmall,
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.small),
            ) {
                RemoteAvatar(
                    image = pet.avatar,
                    fallbackIcon = GeoWoofIcons.Pets,
                    contentDescription = null,
                    size = PetTagAvatar,
                )
                Text(
                    text = pet.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

private val AvatarSize = 44.dp
private val SmallIconSize = 18.dp
private val PetTagAvatar = 24.dp
private const val SCRIM_ALPHA = 0.55f
private const val ACTIVE_SCALE = 1.15f

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
