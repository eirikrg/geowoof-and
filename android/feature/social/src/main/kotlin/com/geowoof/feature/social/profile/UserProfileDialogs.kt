package com.geowoof.feature.social.profile

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.core.model.RelationshipStatus
import com.geowoof.core.model.ReportReason
import com.geowoof.feature.social.R

@Composable
internal fun ProfileDialogs(uiState: UserProfileUiState, reportDetails: TextFieldState, actions: UserProfileActions) {
    val name = uiState.profile?.displayName.orEmpty()
    when (uiState.dialog) {
        null -> Unit

        ProfileDialog.ConfirmRemoveFriend -> ConfirmDialog(
            title = stringResource(R.string.feature_social_remove_friend_title, name),
            message = stringResource(R.string.feature_social_remove_friend_message),
            confirm = stringResource(R.string.feature_social_remove_friend),
            onConfirm = actions.onConfirmRemoveFriend,
            onDismiss = actions.onDismissDialog,
        )

        ProfileDialog.ConfirmBlock -> ConfirmDialog(
            title = stringResource(R.string.feature_social_block_title, name),
            message = stringResource(
                if (uiState.relationship?.status == RelationshipStatus.Friends) {
                    R.string.feature_social_block_friend_message
                } else {
                    R.string.feature_social_block_message
                },
            ),
            confirm = stringResource(R.string.feature_social_block),
            onConfirm = actions.onConfirmBlock,
            onDismiss = actions.onDismissDialog,
        )

        ProfileDialog.VerifyEmail -> ConfirmDialog(
            title = stringResource(R.string.feature_social_verify_title),
            message = stringResource(R.string.feature_social_verify_message),
            confirm = stringResource(R.string.feature_social_verify),
            onConfirm = actions.onVerifyEmail,
            onDismiss = actions.onDismissDialog,
        )

        ProfileDialog.Report -> ReportDialog(uiState.reportReason, name, reportDetails, actions)
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
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.feature_social_dismiss)) } },
    )
}

@StringRes
private fun ReportReason.labelRes(): Int = when (this) {
    ReportReason.Spam -> R.string.feature_social_report_spam
    ReportReason.Harassment -> R.string.feature_social_report_harassment
    ReportReason.InappropriateContent -> R.string.feature_social_report_inappropriate
    ReportReason.Impersonation -> R.string.feature_social_report_impersonation
    ReportReason.Other -> R.string.feature_social_report_other
}

@Composable
private fun ReportDialog(
    selected: ReportReason?,
    name: String,
    details: TextFieldState,
    actions: UserProfileActions,
    modifier: Modifier = Modifier,
) {
    val spacing = GeoWoofTheme.spacing
    AlertDialog(
        onDismissRequest = actions.onDismissDialog,
        modifier = modifier,
        title = { Text(stringResource(R.string.feature_social_report_title, name)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(spacing.small),
            ) {
                Column(modifier = Modifier.selectableGroup()) {
                    ReportReason.entries.forEach { reason ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = reason == selected,
                                    onClick = { actions.onSelectReportReason(reason) },
                                    role = Role.RadioButton,
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(spacing.small),
                        ) {
                            RadioButton(selected = reason == selected, onClick = null)
                            Text(stringResource(reason.labelRes()), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                OutlinedTextField(
                    state = details,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.feature_social_report_details)) },
                    inputTransformation = InputTransformation.maxLength(MAX_REPORT_DETAILS),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = actions.onConfirmReport, enabled = selected != null) {
                Text(stringResource(R.string.feature_social_report_send))
            }
        },
        dismissButton = {
            TextButton(onClick = actions.onDismissDialog) { Text(stringResource(R.string.feature_social_dismiss)) }
        },
    )
}

private const val MAX_REPORT_DETAILS = 500
