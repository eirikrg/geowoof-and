package com.geowoof.feature.social.post

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.geowoof.core.designsystem.icon.GeoWoofIcons
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.core.model.Comment
import com.geowoof.core.ui.MentionText
import com.geowoof.core.ui.RemoteAvatar
import com.geowoof.feature.social.R

/**
 * A comment of the post with its replies under it, inside a line that starts at its photo. With one or two, they
 * are there already. With more, they are folded behind "View N replies", and they come page by page once opened.
 */
@Composable
internal fun CommentThread(
    comment: Comment,
    openThread: OpenThread?,
    myUserId: String?,
    actions: PostDetailActions,
    modifier: Modifier = Modifier,
) {
    val replies = openThread?.replies ?: comment.replies.takeIf { comment.replyCount < FOLDED_FROM }.orEmpty()
    Column(modifier = modifier.fillMaxWidth()) {
        if (comment.isDeleted) {
            DeletedComment()
        } else {
            CommentRow(comment, myUserId, actions, AvatarSize)
        }
        if (comment.replyCount > 0 || openThread != null) {
            val lineColor = MaterialTheme.colorScheme.outlineVariant
            Column(
                modifier = Modifier
                    .padding(start = ThreadLineStart)
                    .drawBehind {
                        drawLine(lineColor, Offset.Zero, Offset(0f, size.height), strokeWidth = ThreadLineWidth.toPx())
                    },
            ) {
                replies.forEach { reply -> CommentRow(reply, myUserId, actions, ReplyAvatarSize) }
                ThreadButtons(comment, openThread, actions)
            }
        }
    }
}

@Composable
private fun ThreadButtons(comment: Comment, openThread: OpenThread?, actions: PostDetailActions) {
    val spacing = GeoWoofTheme.spacing
    Column(modifier = Modifier.padding(start = spacing.small)) {
        when {
            openThread == null -> if (comment.replyCount >= FOLDED_FROM) {
                ThreadButton(
                    text = pluralStringResource(
                        R.plurals.feature_social_view_replies,
                        comment.replyCount,
                        comment.replyCount,
                    ),
                    onClick = { actions.onOpenReplies(comment.id) },
                )
            }

            openThread.isLoading -> CircularProgressIndicator(
                modifier = Modifier
                    .padding(horizontal = spacing.large, vertical = spacing.medium)
                    .size(LoadingSize),
                strokeWidth = ThreadLineWidth,
            )

            openThread.failed -> ThreadButton(
                text = stringResource(R.string.feature_social_replies_failed),
                onClick = { actions.onMoreReplies(comment.id) },
            )

            openThread.nextCursor != null -> ThreadButton(
                text = stringResource(R.string.feature_social_more_replies),
                onClick = { actions.onMoreReplies(comment.id) },
            )
        }
        if (openThread != null && comment.replyCount >= FOLDED_FROM) {
            ThreadButton(text = stringResource(R.string.feature_social_hide_replies), onClick = {
                actions.onHideReplies(comment.id)
            })
        }
    }
}

@Composable
private fun ThreadButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** A comment or a reply: who wrote it, what it says, the way to answer it and its menu. */
@Composable
private fun CommentRow(
    comment: Comment,
    myUserId: String?,
    actions: PostDetailActions,
    avatarSize: Dp,
    modifier: Modifier = Modifier,
) {
    ListItem(
        modifier = modifier,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = {
            Text(
                text = comment.author.displayName,
                modifier = Modifier.clickable { actions.card.onOpenUser(comment.author.id) },
                style = MaterialTheme.typography.titleSmall,
            )
        },
        supportingContent = {
            Column {
                MentionText(text = comment.text, mentions = comment.mentions, onOpenUser = actions.card.onOpenUser)
                // Its text starts where that of the comment does.
                TextButton(
                    onClick = { actions.onReply(comment) },
                    modifier = Modifier.offset(
                        x = -ButtonDefaults.TextButtonContentPadding.calculateStartPadding(
                            LocalLayoutDirection.current,
                        ),
                    ),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                ) {
                    Text(stringResource(R.string.feature_social_reply), style = MaterialTheme.typography.labelLarge)
                }
            }
        },
        leadingContent = {
            RemoteAvatar(
                image = comment.author.avatar,
                fallbackIcon = GeoWoofIcons.Person,
                contentDescription = null,
                size = avatarSize,
            )
        },
        trailingContent = { CommentMenu(comment, myUserId, actions.card.onShowDialog) },
    )
}

/** What stays of a comment that was deleted while it had replies: neither who wrote it nor what it said. */
@Composable
private fun DeletedComment(modifier: Modifier = Modifier) {
    ListItem(
        modifier = modifier,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = {
            Text(
                text = stringResource(R.string.feature_social_deleted_comment),
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        leadingContent = {
            Surface(
                modifier = Modifier.size(AvatarSize),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(GeoWoofIcons.ChatBubble),
                        contentDescription = null,
                        modifier = Modifier.size(DeletedIconSize),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    )
}

/** Whom the field answers, over it, and how to stop answering. */
@Composable
internal fun ReplyingTo(target: ReplyTarget, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    val spacing = GeoWoofTheme.spacing
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = spacing.large, end = spacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(R.string.feature_social_replying_to, target.username),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        IconButton(onClick = onCancel) {
            Icon(
                painter = painterResource(GeoWoofIcons.Close),
                contentDescription = stringResource(R.string.feature_social_cancel_reply),
            )
        }
    }
}

/** From this many replies, they are folded until someone opens them. */
private const val FOLDED_FROM = 3

private val AvatarSize = 40.dp
private val ReplyAvatarSize = 28.dp
private val DeletedIconSize = 20.dp
private val LoadingSize = 20.dp
private val ThreadLineWidth = 2.dp

/** Under the middle of the photo of the comment: the margin of the list plus half of the photo. */
private val ThreadLineStart = 16.dp + AvatarSize / 2
