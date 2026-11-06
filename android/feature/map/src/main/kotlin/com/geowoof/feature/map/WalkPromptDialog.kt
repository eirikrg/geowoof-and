package com.geowoof.feature.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.geowoof.core.designsystem.component.GeoWoofIllustrations
import com.geowoof.core.designsystem.component.IllustratedDialog

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
    IllustratedDialog(
        illustration = GeoWoofIllustrations.PermissionLocation,
        title = stringResource(title),
        message = stringResource(message),
        confirmLabel = stringResource(confirmLabel),
        onConfirm = onConfirm,
        dismissLabel = stringResource(R.string.feature_map_not_now),
        onDismiss = actions.onDismissPrompt,
        modifier = modifier,
    )
}
