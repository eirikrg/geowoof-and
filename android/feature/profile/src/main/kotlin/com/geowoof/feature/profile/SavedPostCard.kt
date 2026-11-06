package com.geowoof.feature.profile

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewFontScale
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.geowoof.core.designsystem.icon.GeoWoofIcons
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.core.model.Post
import com.geowoof.core.model.PostVisibility
import com.geowoof.core.model.RemoteImage
import com.geowoof.core.model.TaggedPet
import com.geowoof.core.model.UserSummary
import com.geowoof.core.ui.ImageVariant
import com.geowoof.core.ui.PostImage
import com.geowoof.core.ui.RemoteAvatar
import com.geowoof.core.ui.relativeToNow
import java.time.Instant

/**
 * A post that was saved to find it again: the photo to recognise it, and next to it who published it, when, what it
 * says and who is in it. The whole card opens the post, where it can also be removed from the saved ones.
 */
@Composable
internal fun SavedPostCard(post: Post, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val spacing = GeoWoofTheme.spacing
    // With nothing but the author, centred next to the photo instead of leaving the rest of the card empty.
    val hasOnlyAuthor = post.text == null && post.pets.isEmpty() && post.likeCount == 0 && post.commentCount == 0
    SavedPostContainer(modifier = modifier, onClick = onClick) {
        SavedPostPhoto(post.media)
        Column(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = SavedPostPhotoSize)
                .padding(end = spacing.extraSmall),
            verticalArrangement = if (hasOnlyAuthor) Arrangement.Center else Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.extraSmall)) {
                SavedPostAuthor(post)
                post.text?.let { text ->
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = CAPTION_MAX_LINES,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            SavedPostFooter(post, Modifier.padding(top = spacing.extraSmall))
        }
    }
}

/** The margins of a card of the profile, as those of the walks and the pets. */
@Composable
internal fun SavedPostContainer(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val spacing = GeoWoofTheme.spacing
    val openPost = stringResource(R.string.feature_profile_open_post)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.large, vertical = spacing.extraSmall)
            .clip(MaterialTheme.shapes.large)
            .then(if (onClick != null) Modifier.clickable(onClickLabel = openPost, onClick = onClick) else Modifier),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(spacing.small),
            horizontalArrangement = Arrangement.spacedBy(spacing.medium),
            content = content,
        )
    }
}

@Composable
private fun SavedPostPhoto(media: List<RemoteImage>, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(SavedPostPhotoSize)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        media.firstOrNull()?.let {
            PostImage(
                image = it,
                contentDescription = null,
                variant = ImageVariant.Thumb,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (media.size > 1) PhotoCount(media.size, Modifier.align(Alignment.TopEnd))
    }
}

/** On a dark background, because it can fall on any photo. */
@Composable
private fun PhotoCount(count: Int, modifier: Modifier = Modifier) {
    val spacing = GeoWoofTheme.spacing
    val description = pluralStringResource(R.plurals.feature_profile_post_photos, count, count)
    Row(
        modifier = modifier
            .padding(spacing.extraSmall)
            .background(Color.Black.copy(alpha = SCRIM_ALPHA), CircleShape)
            .padding(horizontal = spacing.small, vertical = 2.dp)
            .clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.extraSmall),
    ) {
        Icon(
            painter = painterResource(GeoWoofIcons.PhotoLibrary),
            contentDescription = null,
            modifier = Modifier.size(PhotoCountIconSize),
            tint = Color.White,
        )
        Text(text = count.toString(), style = MaterialTheme.typography.labelSmall, color = Color.White)
    }
}

@Composable
private fun SavedPostAuthor(post: Post, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GeoWoofTheme.spacing.small),
    ) {
        RemoteAvatar(
            image = post.author.avatar,
            fallbackIcon = GeoWoofIcons.Person,
            contentDescription = null,
            size = SavedPostAvatarSize,
        )
        Column {
            Text(
                text = post.author.displayName,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = post.createdAt.relativeToNow(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** It wraps instead of cutting anything, for when the font is very large. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SavedPostFooter(post: Post, modifier: Modifier = Modifier) {
    val spacing = GeoWoofTheme.spacing
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing.medium),
        verticalArrangement = Arrangement.spacedBy(spacing.extraSmall),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        post.pets.firstOrNull()?.let { PetTag(it, others = post.pets.size - 1) }
        if (post.likeCount > 0) {
            PostCount(
                icon = if (post.likedByMe) GeoWoofIcons.FavoriteFilled else GeoWoofIcons.Favorite,
                count = post.likeCount,
                description = pluralStringResource(
                    R.plurals.feature_profile_post_likes,
                    post.likeCount,
                    post.likeCount,
                ),
                tint = if (post.likedByMe) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        if (post.commentCount > 0) {
            PostCount(
                icon = GeoWoofIcons.ChatBubble,
                count = post.commentCount,
                description = pluralStringResource(
                    R.plurals.feature_profile_post_comments,
                    post.commentCount,
                    post.commentCount,
                ),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The pet of the post, the same tag as in the feed, smaller. There is only room for one name and how many more. */
@Composable
private fun PetTag(pet: TaggedPet, others: Int, modifier: Modifier = Modifier) {
    val spacing = GeoWoofTheme.spacing
    Row(
        modifier = modifier
            .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
            .padding(start = 2.dp, end = spacing.small, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.extraSmall),
    ) {
        RemoteAvatar(
            image = pet.avatar,
            fallbackIcon = GeoWoofIcons.Pets,
            contentDescription = null,
            size = PetAvatarSize,
        )
        Text(
            text = if (others > 0) {
                stringResource(R.string.feature_profile_post_pet_and_others, pet.name, others)
            } else {
                pet.name
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PostCount(
    @DrawableRes icon: Int,
    count: Int,
    description: String,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GeoWoofTheme.spacing.extraSmall),
    ) {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(CountIconSize), tint = tint)
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

internal val SavedPostPhotoSize = 112.dp
private val PhotoCountIconSize = 12.dp
internal val SavedPostAvatarSize = 36.dp
private val PetAvatarSize = 20.dp
private val CountIconSize = 16.dp
private const val CAPTION_MAX_LINES = 2
private const val SCRIM_ALPHA = 0.55f

@PreviewLightDark
@PreviewFontScale
@Composable
private fun SavedPostCardPreview() {
    val image = RemoteImage("1", null, null)
    GeoWoofTheme(dynamicColor = false) {
        SavedPostCard(
            post = Post(
                id = "1",
                author = UserSummary("1", "bea", "Bea", "Madrid", null),
                text = "Una mañana estupenda junto al río. Luna no quería volver a casa por nada del mundo.",
                visibility = PostVisibility.Everyone,
                media = listOf(image, image, image),
                pets = listOf(TaggedPet("1", "Luna", null), TaggedPet("2", "Toby", null)),
                mentions = emptyList(),
                likeCount = 12,
                commentCount = 3,
                repostCount = 0,
                likedByMe = true,
                savedByMe = true,
                repostedByMe = false,
                canRepost = true,
                createdAt = Instant.parse("2026-11-05T10:00:00Z"),
            ),
            onClick = {},
        )
    }
}
