package com.geowoof.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewFontScale
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.geowoof.core.designsystem.icon.GeoWoofIcons
import com.geowoof.core.designsystem.theme.GeoWoofTheme

/**
 * Centered illustration, title and explanation for screens without content yet.
 * TalkBack reads the title and the message as a single element; the icon is decorative.
 */
@Composable
fun EmptyState(@DrawableRes icon: Int, title: String, message: String, modifier: Modifier = Modifier) {
    val spacing = GeoWoofTheme.spacing
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = spacing.extraLarge, vertical = spacing.huge)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(IllustrationSize),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(spacing.large))
        Text(
            text = title,
            modifier = Modifier.widthIn(max = MaxTextWidth),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(spacing.small))
        Text(
            text = message,
            modifier = Modifier.widthIn(max = MaxTextWidth),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private val IllustrationSize = 72.dp
private val MaxTextWidth = 360.dp

@PreviewLightDark
@PreviewFontScale
@Composable
private fun EmptyStatePreview() {
    GeoWoofTheme(dynamicColor = false) {
        Surface {
            EmptyState(
                icon = GeoWoofIcons.Pets,
                title = "No pets yet",
                message = "Add your first pet to start recording walks together.",
            )
        }
    }
}
