package com.geowoof.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.FilledTonalButton
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
 * Why there is nothing here and what to do about it. With an [illustration] it shows that, and
 * otherwise the [icon] on a soft circle with the trail of the logo around it.
 * TalkBack reads the title and the message as a single element.
 */
@Composable
fun EmptyState(
    @DrawableRes icon: Int,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    @DrawableRes illustration: Int? = null,
    action: (@Composable () -> Unit)? = null,
) {
    val spacing = GeoWoofTheme.spacing
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = spacing.extraLarge, vertical = spacing.huge),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (illustration != null) {
            Image(
                painter = painterResource(illustration),
                contentDescription = null,
                modifier = Modifier.size(IllustrationSize),
            )
        } else {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(IconAreaSize)) {
                TrailDecoration(modifier = Modifier.fillMaxSize())
                IconBadge(icon = icon, size = IconCircleSize, iconSize = IconSize)
            }
        }
        Spacer(Modifier.height(spacing.extraLarge))
        Column(
            modifier = Modifier.semantics(mergeDescendants = true) {},
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
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
        if (action != null) {
            Spacer(Modifier.height(spacing.extraLarge))
            action()
        }
    }
}

/** Something could not be loaded: says it without alarming and lets the person try again. */
@Composable
fun ErrorState(
    title: String,
    message: String,
    retryLabel: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int = GeoWoofIcons.SyncProblem,
    @DrawableRes illustration: Int? = null,
) {
    EmptyState(
        icon = icon,
        title = title,
        message = message,
        modifier = modifier,
        illustration = illustration,
        action = { FilledTonalButton(onClick = onRetry) { Text(retryLabel) } },
    )
}

private val IllustrationSize = 220.dp
private val IconAreaSize = 168.dp
private val IconCircleSize = 104.dp
private val IconSize = 48.dp
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
                action = { FilledTonalButton(onClick = {}) { Text("Add pet") } },
            )
        }
    }
}
