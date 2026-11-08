package com.geowoof.feature.pets.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.core.model.HealthEvent
import com.geowoof.feature.pets.R
import com.geowoof.feature.pets.health.HealthEventRow

/**
 * What is due of the health of the pet, the nearest first, after what is late: the rest, and what was done, is in its
 * own screen. Without anything pending, what it is for and the way to add the first.
 */
internal fun LazyListScope.healthSection(pending: List<HealthEvent>, onOpen: () -> Unit, onAdd: () -> Unit) {
    item(key = "health") {
        val spacing = GeoWoofTheme.spacing
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = spacing.large, end = spacing.large, top = spacing.large),
            verticalArrangement = Arrangement.spacedBy(spacing.small),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.feature_pets_health_title),
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.titleMedium,
                )
                TextButton(onClick = onOpen) { Text(stringResource(R.string.feature_pets_health_see_all)) }
            }
            if (pending.isEmpty()) {
                Text(
                    text = stringResource(R.string.feature_pets_health_section_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FilledTonalButton(onClick = onAdd) { Text(stringResource(R.string.feature_pets_health_add)) }
            }
        }
    }
    items(pending.take(SHOWN), key = { "health:${it.id}" }) { event ->
        HealthEventRow(event = event, isCompleting = false, onOpen = onOpen)
    }
}

/** The rest is a touch away, in its own screen. */
private const val SHOWN = 3
