package com.geowoof.core.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import com.geowoof.core.designsystem.icon.GeoWoofIcons
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import java.io.File

/**
 * Photos on the whole screen with their real proportions, also those that a card cuts in a square. They can be enlarged
 * with two fingers or a double tap, and when they are not enlarged, one finger goes to the next one. It is black in
 * both themes, so that nothing competes with the photo.
 *
 * @param onDismiss With the photo it was on, for whoever opened it to stay on the same one.
 */
@Composable
fun PhotoViewer(
    photos: List<ViewerPhoto>,
    initialPage: Int,
    contentDescription: String?,
    onDismiss: (lastPage: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pager = rememberPagerState(initialPage = initialPage) { photos.size }
    val zooms = remember(photos.size) { List(photos.size) { PhotoZoom() } }
    Dialog(
        onDismissRequest = { onDismiss(pager.currentPage) },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        LightSystemBarIcons()
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxSize(),
                // One finger is moving the photo: going to the next one has to wait until it is back to its size.
                userScrollEnabled = !zooms[pager.currentPage].isZoomed,
            ) { page ->
                ViewerPage(photos[page], zooms[page], contentDescription)
            }
            ViewerTopBar(
                currentPage = pager.currentPage,
                pageCount = photos.size,
                onClose = { onDismiss(pager.currentPage) },
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
}

/** A dialog has its own window, and in the light theme it would write dark icons on the black of the viewer. */
@Composable
private fun LightSystemBarIcons() {
    val view = LocalView.current
    SideEffect {
        (view.parent as? DialogWindowProvider)?.window?.let { window ->
            val bars = WindowCompat.getInsetsController(window, view)
            bars.isAppearanceLightStatusBars = false
            bars.isAppearanceLightNavigationBars = false
        }
    }
}

@Composable
private fun ViewerPage(
    photo: ViewerPhoto,
    zoom: PhotoZoom,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val model = remember(context, photo) { photo.model(context) }
    var state by remember { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }
    Box(
        modifier = modifier
            .fillMaxSize()
            .photoZoomGestures(zoom),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = model,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            onState = { new ->
                state = new
                (new as? AsyncImagePainter.State.Success)?.painter?.let { zoom.imageSize = it.intrinsicSize }
            },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = zoom.scale
                    scaleY = zoom.scale
                    translationX = zoom.offset.x
                    translationY = zoom.offset.y
                },
        )
        when (val current = state) {
            // With the small copy as a placeholder there is already something to look at.
            is AsyncImagePainter.State.Loading -> if (current.painter == null) {
                CircularProgressIndicator(color = Color.White)
            }

            is AsyncImagePainter.State.Error -> Text(
                text = stringResource(R.string.core_ui_photo_failed),
                modifier = Modifier.padding(GeoWoofTheme.spacing.extraLarge),
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )

            else -> Unit
        }
    }
}

@Composable
private fun ViewerTopBar(currentPage: Int, pageCount: Int, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val spacing = GeoWoofTheme.spacing
    Row(
        modifier = modifier
            .fillMaxWidth()
            .safeDrawingPadding()
            .padding(spacing.small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(
            onClick = onClose,
            modifier = Modifier.background(Color.Black.copy(alpha = SCRIM_ALPHA), CircleShape),
        ) {
            Icon(
                painter = painterResource(GeoWoofIcons.Close),
                contentDescription = stringResource(R.string.core_ui_close),
                tint = Color.White,
            )
        }
        if (pageCount > 1) {
            val description = stringResource(R.string.core_ui_photo_position_description, currentPage + 1, pageCount)
            Column(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = SCRIM_ALPHA), CircleShape)
                    .padding(horizontal = spacing.medium, vertical = spacing.extraSmall)
                    .clearAndSetSemantics { this.contentDescription = description },
            ) {
                Text(
                    text = stringResource(R.string.core_ui_photo_position, currentPage + 1, pageCount),
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

/** With the same cache keys as the rest of the app: a photo seen in a card is already on the device. */
private fun ViewerPhoto.model(context: Context): Any? {
    val url = image?.mediumUrl ?: image?.thumbUrl
    val variant = if (image?.mediumUrl != null) ImageVariant.Medium else ImageVariant.Thumb
    return when {
        localPath != null -> File(localPath)

        url != null && image != null -> {
            val key = "${image.assetId}:${variant.name}"
            ImageRequest.Builder(context)
                .data(url)
                .memoryCacheKey(key)
                .diskCacheKey(key)
                .placeholderMemoryCacheKey("${image.assetId}:${ImageVariant.Thumb.name}")
                .build()
        }

        else -> null
    }
}

private const val SCRIM_ALPHA = 0.55f
