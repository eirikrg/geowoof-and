package com.geowoof.feature.social.post

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.geowoof.core.designsystem.icon.GeoWoofIcons
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.core.model.Comment
import com.geowoof.core.ui.MentionText
import com.geowoof.core.ui.RemoteAvatar
import com.geowoof.core.ui.relativeToNow
import com.geowoof.core.ui.shortAgo
import com.geowoof.feature.social.R

/*
 * A comment of the post with its replies under it, as threads are drawn in messaging apps: a line comes down from
 * under its photo, and turns with a curve towards the photo of each reply and towards "View N replies". It ends at the
 * last of them, without a tail. The replies start where the text of the comment does.
 *
 * With one or two replies they are there already. With more, they are folded behind "View N replies", and they come
 * page by page once opened.
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
    val hasControls = hasThreadControls(comment, openThread)
    val line = MaterialTheme.colorScheme.outlineVariant
    Column(modifier = modifier.fillMaxWidth().padding(bottom = GeoWoofTheme.spacing.extraSmall)) {
        val parentModifier = if (replies.isNotEmpty() || hasControls) Modifier.threadStart(line) else Modifier
        if (comment.isDeleted) {
            DeletedComment(parentModifier)
        } else {
            CommentRow(comment, myUserId, actions, isReply = false, modifier = parentModifier)
        }
        replies.forEachIndexed { index, reply ->
            val isLast = index == replies.lastIndex && !hasControls
            CommentRow(
                reply,
                myUserId,
                actions,
                isReply = true,
                modifier = Modifier.threadBranch(line, isLast, ReplyBranchY),
            )
        }
        if (hasControls) {
            ThreadControls(comment, openThread, actions, Modifier.threadBranch(line, isLast = true, ControlsBranchY))
        }
    }
}

/** Whether the thread has a row of buttons under its replies. */
private fun hasThreadControls(comment: Comment, openThread: OpenThread?): Boolean = if (openThread == null) {
    comment.replyCount >= FOLDED_FROM
} else {
    openThread.isLoading || openThread.failed || openThread.nextCursor != null || comment.replyCount >= FOLDED_FROM
}

/** "View N replies" while folded; once open, the next ones, a retry or the wheel, and the way to fold them again. */
@Composable
private fun ThreadControls(
    comment: Comment,
    openThread: OpenThread?,
    actions: PostDetailActions,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = ReplyStart, top = ControlsPadding, bottom = ControlsPadding),
        horizontalArrangement = Arrangement.spacedBy(GeoWoofTheme.spacing.large),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            openThread == null -> SmallAction(
                text = pluralStringResource(
                    R.plurals.feature_social_view_replies,
                    comment.replyCount,
                    comment.replyCount,
                ),
                onClick = { actions.onOpenReplies(comment.id) },
            )

            openThread.isLoading -> CircularProgressIndicator(
                modifier = Modifier.padding(vertical = SmallActionPadding).size(LoadingSize),
                strokeWidth = LineWidth,
            )

            openThread.failed -> SmallAction(
                text = stringResource(R.string.feature_social_replies_failed),
                onClick = { actions.onMoreReplies(comment.id) },
            )

            openThread.nextCursor != null -> SmallAction(
                text = stringResource(R.string.feature_social_more_replies),
                onClick = { actions.onMoreReplies(comment.id) },
            )
        }
        if (openThread != null && comment.replyCount >= FOLDED_FROM) {
            SmallAction(text = stringResource(R.string.feature_social_hide_replies), onClick = {
                actions.onHideReplies(comment.id)
            })
        }
    }
}

/**
 * A word that does something, small so as not to take room from the comments. Compose takes the touch to 48 dp around
 * it, without taking that room either.
 */
@Composable
private fun SmallAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = SmallActionPadding),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** A comment or a reply: who wrote it, what it says, the way to answer it and its menu. */
@Composable
private fun CommentRow(
    comment: Comment,
    myUserId: String?,
    actions: PostDetailActions,
    isReply: Boolean,
    modifier: Modifier = Modifier,
) {
    CommentLayout(
        isReply = isReply,
        modifier = modifier,
        photo = { size ->
            RemoteAvatar(
                image = comment.author.avatar,
                fallbackIcon = GeoWoofIcons.Person,
                contentDescription = null,
                size = size,
            )
        },
        menu = { CommentMenu(comment, myUserId, actions.card.onShowDialog) },
    ) {
        // Who and how long ago. A long name gives way, and the time is always seen.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = comment.author.displayName,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .clickable { actions.card.onOpenUser(comment.author.id) },
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val whole = comment.createdAt.relativeToNow()
            Text(
                text = comment.createdAt.shortAgo(),
                modifier = Modifier
                    .padding(start = TimeGap)
                    .semantics { contentDescription = whole },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        MentionText(text = comment.text, mentions = comment.mentions, onOpenUser = actions.card.onOpenUser)
        SmallAction(text = stringResource(R.string.feature_social_reply), onClick = { actions.onReply(comment) })
    }
}

/** What stays of a comment that was deleted while it had replies: neither who wrote it nor what it said. */
@Composable
private fun DeletedComment(modifier: Modifier = Modifier) {
    CommentLayout(
        isReply = false,
        modifier = modifier,
        photo = { size ->
            Surface(
                modifier = Modifier.size(size),
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
    ) {
        Text(
            text = stringResource(R.string.feature_social_deleted_comment),
            modifier = Modifier.padding(top = DeletedTextPadding),
            style = MaterialTheme.typography.bodyMedium,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The photo, what it says beside it and the menu at the end. A reply starts where the text of its comment does, with
 * a smaller photo.
 */
@Composable
private fun CommentLayout(
    isReply: Boolean,
    photo: @Composable (size: Dp) -> Unit,
    modifier: Modifier = Modifier,
    menu: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = if (isReply) ReplyStart else ScreenMargin,
                top = RowTop,
                bottom = RowBottom,
                end = MenuMargin,
            ),
        verticalAlignment = Alignment.Top,
    ) {
        photo(if (isReply) ReplyPhotoSize else PhotoSize)
        Spacer(Modifier.width(PhotoGap))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TextGap), content = content)
        // The dots at the height of the name.
        Box(modifier = Modifier.offset(y = MenuOffset)) { menu() }
    }
}

/** The line of the thread, from under the photo of the comment to the end of its row. */
private fun Modifier.threadStart(color: Color): Modifier = drawBehind {
    val from = (RowTop + PhotoSize + LineGap).toPx()
    if (from < size.height) {
        val x = startX(LineX)
        drawLine(color, Offset(x, from), Offset(x, size.height), strokeWidth = LineWidth.toPx())
    }
}

/**
 * The line goes on beside this row and turns with a curve towards it at [branchY]. In the last one, it ends at the
 * curve.
 */
private fun Modifier.threadBranch(color: Color, isLast: Boolean, branchY: Dp): Modifier = drawBehind {
    val stroke = LineWidth.toPx()
    val x = startX(LineX)
    val y = branchY.toPx()
    val radius = BranchRadius.toPx()
    val end = startX(ReplyStart - BranchGap)
    drawLine(color, Offset(x, 0f), Offset(x, if (isLast) y - radius else size.height), strokeWidth = stroke)
    val curve = Path().apply {
        moveTo(x, y - radius)
        quadraticTo(x, y, startX(LineX + BranchRadius), y)
        lineTo(end, y)
    }
    drawPath(curve, color, style = Stroke(width = stroke))
}

/** A distance from the start, which is the right in a language written from right to left. */
private fun DrawScope.startX(distance: Dp): Float =
    if (layoutDirection == LayoutDirection.Ltr) distance.toPx() else size.width - distance.toPx()

/** From this many replies, they are folded until someone opens them. */
private const val FOLDED_FROM = 3

private val ScreenMargin = 16.dp
private val MenuMargin = 4.dp
private val PhotoSize = 40.dp
private val ReplyPhotoSize = 28.dp
private val PhotoGap = 12.dp
private val TextGap = 2.dp
private val TimeGap = 6.dp
private val RowTop = 8.dp
private val RowBottom = 2.dp
private val SmallActionPadding = 6.dp
private val ControlsPadding = 2.dp
private val DeletedIconSize = 20.dp
private val DeletedTextPadding = 10.dp
private val LoadingSize = 16.dp

/** The dots of the menu are 48 dp tall: up a little, they are at the height of the name. */
private val MenuOffset = (-8).dp

/** Replies start where the text of their comment does. */
private val ReplyStart = ScreenMargin + PhotoSize + PhotoGap

/** The line goes under the middle of the photo of the comment, and starts a little under it. */
private val LineX = ScreenMargin + PhotoSize / 2
private val LineGap = 4.dp
private val LineWidth = 2.dp
private val BranchRadius = 10.dp
private val BranchGap = 6.dp

/** Where the line turns: at the middle of the photo of a reply, and of the text of the buttons. */
private val ReplyBranchY = RowTop + ReplyPhotoSize / 2
private val ControlsBranchY = ControlsPadding + SmallActionPadding + 10.dp
