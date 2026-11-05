package com.geowoof.feature.profile

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.geowoof.core.model.RelationshipStatus
import com.geowoof.core.ui.ReportDialog

@Composable
internal fun ProfileDialogs(uiState: ProfileUiState, reportDetails: TextFieldState, actions: ProfileActions) {
    val name = uiState.profile?.displayName.orEmpty()
    val isFriend = uiState.relationship?.status == RelationshipStatus.Friends
    when (uiState.dialog) {
        null -> Unit

        // Following back gives access to what is only for friends, which is easy to miss.
        ProfileDialog.ConfirmFollowBack -> ConfirmDialog(
            title = stringResource(R.string.feature_profile_follow_back_title, name),
            message = stringResource(R.string.feature_profile_follow_back_message, name),
            confirm = stringResource(R.string.feature_profile_follow_back),
            onConfirm = actions.onConfirmFollowBack,
            onDismiss = actions.onDismissDialog,
        )

        ProfileDialog.ConfirmUnfollow -> ConfirmDialog(
            title = stringResource(R.string.feature_profile_unfollow_title, name),
            message = stringResource(
                when {
                    isFriend -> R.string.feature_profile_unfollow_friend_message
                    uiState.profile?.isPrivate == true -> R.string.feature_profile_unfollow_private_message
                    else -> R.string.feature_profile_unfollow_message
                },
            ),
            confirm = stringResource(R.string.feature_profile_unfollow),
            onConfirm = actions.onConfirmUnfollow,
            onDismiss = actions.onDismissDialog,
        )

        ProfileDialog.ConfirmRemoveFollower -> ConfirmDialog(
            title = stringResource(R.string.feature_profile_remove_follower_title, name),
            message = stringResource(
                if (isFriend) {
                    R.string.feature_profile_remove_follower_friend_message
                } else {
                    R.string.feature_profile_remove_follower_message
                },
            ),
            confirm = stringResource(R.string.feature_profile_remove_follower_confirm),
            onConfirm = actions.onConfirmRemoveFollower,
            onDismiss = actions.onDismissDialog,
        )

        ProfileDialog.ConfirmBlock -> ConfirmDialog(
            title = stringResource(R.string.feature_profile_block_title, name),
            message = stringResource(
                if (isFriend) {
                    R.string.feature_profile_block_friend_message
                } else {
                    R.string.feature_profile_block_message
                },
            ),
            confirm = stringResource(R.string.feature_profile_block),
            onConfirm = actions.onConfirmBlock,
            onDismiss = actions.onDismissDialog,
        )

        ProfileDialog.VerifyEmail -> ConfirmDialog(
            title = stringResource(R.string.feature_profile_verify_title),
            message = stringResource(R.string.feature_profile_verify_message),
            confirm = stringResource(R.string.feature_profile_verify),
            onConfirm = actions.onVerifyEmail,
            onDismiss = actions.onDismissDialog,
        )

        ProfileDialog.Report -> ReportDialog(
            title = stringResource(R.string.feature_profile_report_title, name),
            selected = uiState.reportReason,
            details = reportDetails,
            onSelect = actions.onSelectReportReason,
            onConfirm = actions.onConfirmReport,
            onDismiss = actions.onDismissDialog,
        )
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.feature_profile_dismiss)) } },
    )
}
