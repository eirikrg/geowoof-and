package com.geowoof.feature.social.post

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.geowoof.core.designsystem.icon.GeoWoofIcons
import com.geowoof.core.model.Comment
import com.geowoof.feature.social.R

/**
 * What can be done with a comment, behind three dots, as with a post. Deleting is for whoever wrote it and for the
 * author of the post, which the server says with `canDelete`, and it asks first. Reporting is for anything that
 * someone else wrote, also in your own post. Without anything to do, there are no dots.
 */
@Composable
internal fun CommentMenu(
    comment: Comment,
    myUserId: String?,
    onShowDialog: (PostDialog) -> Unit,
    modifier: Modifier = Modifier,
) {
    val canReport = myUserId != null && comment.author.id != myUserId
    if (!comment.canDelete && !canReport) return
    var isOpen by rememberSaveable { mutableStateOf(false) }
    Box(modifier = modifier) {
        IconButton(onClick = { isOpen = true }) {
            Icon(
                painter = painterResource(GeoWoofIcons.MoreVert),
                contentDescription = stringResource(
                    R.string.feature_social_comment_options,
                    comment.author.displayName,
                ),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = isOpen, onDismissRequest = { isOpen = false }) {
            if (comment.canDelete) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.feature_social_delete)) },
                    leadingIcon = { Icon(painterResource(GeoWoofIcons.Delete), contentDescription = null) },
                    onClick = {
                        isOpen = false
                        onShowDialog(PostDialog.ConfirmDeleteComment(comment))
                    },
                )
            }
            if (canReport) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.feature_social_report)) },
                    leadingIcon = { Icon(painterResource(GeoWoofIcons.Flag), contentDescription = null) },
                    onClick = {
                        isOpen = false
                        onShowDialog(PostDialog.ReportComment(comment))
                    },
                )
            }
        }
    }
}
