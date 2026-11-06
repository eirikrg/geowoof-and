package com.geowoof.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.geowoof.core.designsystem.theme.GeoWoofTheme

/**
 * Asks the person for something that deserves a moment, like a permission. A very large font needs all
 * the room for the words, so it goes without the illustration; and the message scrolls if it does not fit.
 */
@Composable
fun IllustratedDialog(
    @DrawableRes illustration: Int,
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    dismissLabel: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasRoom = LocalDensity.current.fontScale <= MAX_FONT_SCALE_WITH_ILLUSTRATION
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        icon = if (hasRoom) {
            { GeoWoofIllustration(illustration, Modifier.heightIn(max = IllustrationMaxHeight)) }
        } else {
            null
        },
        // Material centers the title under an icon, but not the lines of a long one.
        title = { Text(title, textAlign = if (hasRoom) TextAlign.Center else TextAlign.Start) },
        text = { Text(message, modifier = Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismissLabel) } },
    )
}

private const val MAX_FONT_SCALE_WITH_ILLUSTRATION = 1.5f
private val IllustrationMaxHeight = 140.dp

@PreviewLightDark
@Composable
private fun IllustratedDialogPreview() {
    GeoWoofTheme(dynamicColor = false) {
        Surface {
            IllustratedDialog(
                illustration = GeoWoofIllustrations.PermissionLocation,
                title = "Your location during walks",
                message = "GeoWoof uses your location to draw the route, only while you are recording a walk.",
                confirmLabel = "Continue",
                onConfirm = {},
                dismissLabel = "Not now",
                onDismiss = {},
            )
        }
    }
}
