package com.geowoof.core.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.core.model.LiveVisibility

/** The short name of each option. */
@StringRes
fun LiveVisibility.labelRes(): Int = when (this) {
    LiveVisibility.Everyone -> R.string.core_ui_live_everyone
    LiveVisibility.FriendsOnly -> R.string.core_ui_live_friends
    LiveVisibility.Nobody -> R.string.core_ui_live_nobody
}

@StringRes
private fun LiveVisibility.descriptionRes(): Int = when (this) {
    LiveVisibility.Everyone -> R.string.core_ui_live_everyone_description
    LiveVisibility.FriendsOnly -> R.string.core_ui_live_friends_description
    LiveVisibility.Nobody -> R.string.core_ui_live_nobody_description
}

/**
 * The three options with what each one means. With [selected] `null` none is marked, because the
 * person has to choose.
 */
@Composable
fun LiveVisibilityOptions(
    selected: LiveVisibility?,
    onSelect: (LiveVisibility) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.selectableGroup()) {
        LiveVisibility.entries.forEach { option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(selected = option == selected, role = Role.RadioButton, onClick = { onSelect(option) })
                    .padding(vertical = GeoWoofTheme.spacing.small),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(GeoWoofTheme.spacing.medium),
            ) {
                RadioButton(selected = option == selected, onClick = null)
                Column {
                    Text(stringResource(option.labelRes()), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = stringResource(option.descriptionRes()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun LiveVisibilityOptionsPreview() {
    GeoWoofTheme {
        LiveVisibilityOptions(selected = LiveVisibility.FriendsOnly, onSelect = {})
    }
}
