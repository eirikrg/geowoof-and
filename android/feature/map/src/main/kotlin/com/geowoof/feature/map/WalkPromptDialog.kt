package com.geowoof.feature.map

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

@Composable
internal fun WalkPromptDialog(prompt: WalkPrompt, actions: MapActions, modifier: Modifier = Modifier) {
    val (title, message) = when (prompt) {
        WalkPrompt.PermissionRationale -> R.string.feature_map_rationale_title to R.string.feature_map_rationale_message

        WalkPrompt.ApproximateLocation ->
            R.string.feature_map_approximate_title to
                R.string.feature_map_approximate_message

        WalkPrompt.PermissionDenied -> R.string.feature_map_denied_title to R.string.feature_map_denied_message

        WalkPrompt.LocationDisabled -> R.string.feature_map_disabled_title to R.string.feature_map_disabled_message
    }
    val (confirmLabel, onConfirm) = when (prompt) {
        WalkPrompt.PermissionRationale -> R.string.feature_map_rationale_continue to actions.onRequestPermissions
        WalkPrompt.LocationDisabled -> R.string.feature_map_open_settings to actions.onOpenLocationSettings
        else -> R.string.feature_map_open_settings to actions.onOpenAppSettings
    }
    AlertDialog(
        onDismissRequest = actions.onDismissPrompt,
        modifier = modifier,
        title = { Text(stringResource(title)) },
        text = { Text(stringResource(message)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(confirmLabel)) } },
        dismissButton = {
            TextButton(onClick = actions.onDismissPrompt) { Text(stringResource(R.string.feature_map_not_now)) }
        },
    )
}
