package com.geowoof.feature.social.post

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.geowoof.core.model.ReportReason
import com.geowoof.core.ui.ReportDialog
import com.geowoof.feature.social.R

/** Asks before deleting anything, and why something is being reported. */
@Composable
internal fun PostDialogs(
    state: PostInteractionState,
    reportDetails: TextFieldState,
    onSelectReason: (ReportReason) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (val dialog = state.dialog) {
        null -> Unit

        is PostDialog.ConfirmDeletePost -> ConfirmDelete(
            title = stringResource(R.string.feature_social_delete_post_title),
            message = stringResource(R.string.feature_social_delete_post_message),
            onConfirm = onConfirm,
            onDismiss = onDismiss,
        )

        is PostDialog.ConfirmDeleteComment -> ConfirmDelete(
            title = stringResource(R.string.feature_social_delete_comment_title),
            message = stringResource(
                if (dialog.comment.replyCount > 0) {
                    R.string.feature_social_delete_comment_with_replies_message
                } else {
                    R.string.feature_social_delete_comment_message
                },
            ),
            onConfirm = onConfirm,
            onDismiss = onDismiss,
        )

        is PostDialog.ReportPost, is PostDialog.ReportComment -> ReportDialog(
            title = stringResource(
                if (dialog is PostDialog.ReportPost) {
                    R.string.feature_social_report_post
                } else {
                    R.string.feature_social_report_comment
                },
            ),
            selected = state.reportReason,
            details = reportDetails,
            onSelect = onSelectReason,
            onConfirm = onConfirm,
            onDismiss = onDismiss,
        )
    }
}

@Composable
private fun ConfirmDelete(title: String, message: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.feature_social_delete)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.feature_social_dismiss)) } },
    )
}
