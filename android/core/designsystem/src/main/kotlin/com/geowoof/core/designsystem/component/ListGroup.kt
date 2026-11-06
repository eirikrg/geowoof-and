package com.geowoof.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.geowoof.core.designsystem.theme.GeoWoofTheme

/** The title of a part of a screen. TalkBack can jump between them. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    val spacing = GeoWoofTheme.spacing
    Text(
        text = text,
        modifier = modifier
            .padding(start = spacing.large, end = spacing.large, top = spacing.extraLarge, bottom = spacing.small)
            .semantics { heading() },
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Rows that belong together, on a card. */
@Composable
fun ListGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = GeoWoofTheme.spacing.large),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(content = content)
    }
}

/** A line between two rows of a [ListGroup]. */
@Composable
fun ListGroupDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.padding(horizontal = GeoWoofTheme.spacing.large),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = DIVIDER_ALPHA),
    )
}

/**
 * A row of a [ListGroup], with its icon on a circle. To make the whole row a switch, or anything else
 * that is not a tap, pass it in [modifier] and leave [onClick] alone. With [isDestructive] it goes in
 * the color of errors.
 */
@Composable
fun ListGroupItem(
    headline: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    @DrawableRes icon: Int? = null,
    isDestructive: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    ListItem(
        modifier = if (onClick != null) modifier.clickable(enabled = enabled, onClick = onClick) else modifier,
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
            headlineColor = if (isDestructive) colors.error else colors.onSurface,
        ),
        headlineContent = { Text(headline, style = MaterialTheme.typography.titleSmall) },
        supportingContent = supporting?.let { { Text(it, style = MaterialTheme.typography.bodySmall) } },
        leadingContent = icon?.let {
            {
                IconBadge(
                    icon = it,
                    containerColor = if (isDestructive) colors.errorContainer else colors.secondaryContainer,
                    contentColor = if (isDestructive) colors.onErrorContainer else colors.onSecondaryContainer,
                )
            }
        },
        trailingContent = trailing,
    )
}

private const val DIVIDER_ALPHA = 0.5f
