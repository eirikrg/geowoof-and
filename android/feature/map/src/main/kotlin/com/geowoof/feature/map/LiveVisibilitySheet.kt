package com.geowoof.feature.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.geowoof.core.designsystem.icon.GeoWoofIcons
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.core.model.LiveVisibility
import com.geowoof.core.ui.LiveVisibilityOptions
import com.geowoof.core.ui.labelRes

/**
 * Before sharing anything the person is told what is sent and chooses who sees it, with no option
 * marked the first time. It also serves as the prominent disclosure of Google Play
 * (docs/decisions/0007). Choosing takes a second step, because a slip here has consequences.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LiveVisibilitySheet(uiState: MapUiState, actions: MapActions, modifier: Modifier = Modifier) {
    val spacing = GeoWoofTheme.spacing
    var choice by rememberSaveable { mutableStateOf(uiState.liveVisibility) }
    ModalBottomSheet(
        onDismissRequest = actions.onDismissVisibility,
        modifier = modifier,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .padding(start = spacing.large, end = spacing.large, bottom = spacing.large)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(spacing.medium),
        ) {
            Text(
                text = stringResource(R.string.feature_map_live_visibility_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(R.string.feature_map_live_visibility_message),
                style = MaterialTheme.typography.bodyMedium,
            )
            LiveVisibilityOptions(selected = choice, onSelect = { choice = it })
            Text(
                text = if (uiState.hideStartDistanceM > 0) {
                    stringResource(R.string.feature_map_live_visibility_hide_start, uiState.hideStartDistanceM)
                } else {
                    stringResource(R.string.feature_map_live_visibility_no_hide)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = { choice?.let(actions.onChooseVisibility) },
                enabled = choice != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.feature_map_live_visibility_confirm))
            }
        }
    }
}

/** Who can see the person now, and the way to change it. */
@Composable
internal fun LiveVisibilityChip(visibility: LiveVisibility?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val isShared = visibility != null && visibility != LiveVisibility.Nobody
    AssistChip(
        onClick = onClick,
        modifier = modifier,
        leadingIcon = {
            Icon(
                painter = painterResource(if (isShared) GeoWoofIcons.Visibility else GeoWoofIcons.VisibilityOff),
                contentDescription = null,
            )
        },
        label = {
            Text(
                if (isShared) {
                    stringResource(R.string.feature_map_live_shown_to, stringResource(visibility.labelRes()))
                } else {
                    stringResource(R.string.feature_map_live_not_shared)
                },
            )
        },
    )
}
