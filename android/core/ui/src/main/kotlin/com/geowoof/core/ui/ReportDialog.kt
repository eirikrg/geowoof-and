package com.geowoof.core.ui

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
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.core.model.ReportReason

private const val MAX_REPORT_DETAILS = 500

@StringRes
private fun ReportReason.labelRes(): Int = when (this) {
    ReportReason.Spam -> R.string.core_ui_report_spam
    ReportReason.Harassment -> R.string.core_ui_report_harassment
    ReportReason.InappropriateContent -> R.string.core_ui_report_inappropriate
    ReportReason.Impersonation -> R.string.core_ui_report_impersonation
    ReportReason.Other -> R.string.core_ui_report_other
}

/** Asks why something is being reported: a person, a post or a comment. It needs a reason to be sent. */
@Suppress("LongParameterList")
@Composable
fun ReportDialog(
    title: String,
    selected: ReportReason?,
    details: TextFieldState,
    onSelect: (ReportReason) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = GeoWoofTheme.spacing
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(title) },
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
                                    onClick = { onSelect(reason) },
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
                    label = { Text(stringResource(R.string.core_ui_report_details)) },
                    inputTransformation = InputTransformation.maxLength(MAX_REPORT_DETAILS),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = selected != null) {
                Text(stringResource(R.string.core_ui_report_send))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.core_ui_report_dismiss)) }
        },
    )
}

@PreviewLightDark
@Composable
private fun ReportDialogPreview() {
    GeoWoofTheme(dynamicColor = false) {
        ReportDialog(
            title = "Report",
            selected = ReportReason.Spam,
            details = TextFieldState(),
            onSelect = {},
            onConfirm = {},
            onDismiss = {},
        )
    }
}
