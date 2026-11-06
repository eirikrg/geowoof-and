package com.geowoof.core.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.geowoof.core.designsystem.component.IconBadge
import com.geowoof.core.designsystem.icon.GeoWoofIcons
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.core.model.RemoteImage

/** The photo of a form: big, tappable, and with what can be done to it below. No [removeLabel], no removing. */
@Composable
fun EditablePhoto(
    image: RemoteImage?,
    localUri: String?,
    @DrawableRes fallbackIcon: Int,
    contentDescription: String,
    chooseLabel: String,
    removeLabel: String?,
    onChoose: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.BottomEnd) {
            RemoteAvatar(
                image = image,
                fallbackIcon = fallbackIcon,
                contentDescription = contentDescription,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClickLabel = chooseLabel, onClick = onChoose),
                size = PhotoSize,
                variant = ImageVariant.Medium,
                localUri = localUri,
            )
            IconBadge(
                icon = GeoWoofIcons.PhotoCamera,
                size = BadgeSize,
                iconSize = BadgeIconSize,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(GeoWoofTheme.spacing.extraSmall)) {
            TextButton(onClick = onChoose) { Text(chooseLabel) }
            if (removeLabel != null) {
                TextButton(onClick = onRemove) { Text(removeLabel) }
            }
        }
    }
}

private val PhotoSize = 120.dp
private val BadgeSize = 36.dp
private val BadgeIconSize = 20.dp

@PreviewLightDark
@Composable
private fun EditablePhotoPreview() {
    GeoWoofTheme(dynamicColor = false) {
        Surface {
            EditablePhoto(
                image = null,
                localUri = null,
                fallbackIcon = GeoWoofIcons.Pets,
                contentDescription = "Photo",
                chooseLabel = "Choose photo",
                removeLabel = "Remove",
                onChoose = {},
                onRemove = {},
            )
        }
    }
}
