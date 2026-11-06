package com.geowoof.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.geowoof.core.designsystem.component.ListGroupDivider
import com.geowoof.core.designsystem.component.ListGroupItem
import com.geowoof.core.designsystem.icon.GeoWoofIcons
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.core.model.LiveVisibility
import com.geowoof.core.model.UserSettings
import com.geowoof.core.ui.LiveVisibilityOptions
import com.geowoof.core.ui.labelRes

/** Who sees the person on the live map, and from where. */
@Composable
internal fun LiveSettings(
    settings: UserSettings,
    onVisibilityChange: (LiveVisibility) -> Unit,
    onHideStartChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isChoosingVisibility by rememberSaveable { mutableStateOf(false) }
    var isChoosingHideStart by rememberSaveable { mutableStateOf(false) }
    Column(modifier = modifier) {
        ListGroupItem(
            headline = stringResource(R.string.feature_settings_live_visibility),
            supporting = settings.liveVisibility?.let { stringResource(it.labelRes()) }
                ?: stringResource(R.string.feature_settings_live_visibility_not_chosen),
            icon = GeoWoofIcons.Visibility,
            onClick = { isChoosingVisibility = true },
        )
        ListGroupDivider()
        ListGroupItem(
            headline = stringResource(R.string.feature_settings_hide_start),
            supporting = hideStartLabel(settings.hideStartDistanceM),
            icon = GeoWoofIcons.LocationOn,
            onClick = { isChoosingHideStart = true },
        )
    }
    if (isChoosingVisibility) {
        LiveVisibilityDialog(
            selected = settings.liveVisibility,
            onSelect = {
                isChoosingVisibility = false
                onVisibilityChange(it)
            },
            onDismiss = { isChoosingVisibility = false },
        )
    }
    if (isChoosingHideStart) {
        HideStartDialog(
            selected = settings.hideStartDistanceM,
            onSelect = {
                isChoosingHideStart = false
                onHideStartChange(it)
            },
            onDismiss = { isChoosingHideStart = false },
        )
    }
}

@Composable
private fun hideStartLabel(meters: Int): String = if (meters == 0) {
    stringResource(R.string.feature_settings_hide_start_never)
} else {
    stringResource(R.string.feature_settings_hide_start_meters, meters)
}

@Composable
private fun LiveVisibilityDialog(selected: LiveVisibility?, onSelect: (LiveVisibility) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.feature_settings_live_visibility)) },
        text = { LiveVisibilityOptions(selected = selected, onSelect = onSelect) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.feature_settings_cancel)) }
        },
    )
}

@Composable
private fun HideStartDialog(selected: Int, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.feature_settings_hide_start)) },
        text = {
            Column(modifier = Modifier.selectableGroup()) {
                Text(
                    text = stringResource(R.string.feature_settings_hide_start_message),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = GeoWoofTheme.spacing.small),
                )
                UserSettings.HIDE_START_OPTIONS.forEach { meters ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = meters == selected, role = Role.RadioButton, onClick = {
                                onSelect(meters)
                            })
                            .padding(vertical = GeoWoofTheme.spacing.small),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(GeoWoofTheme.spacing.medium),
                    ) {
                        RadioButton(selected = meters == selected, onClick = null)
                        Text(hideStartLabel(meters), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.feature_settings_cancel)) }
        },
    )
}
