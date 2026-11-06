package com.geowoof.core.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import com.geowoof.core.designsystem.icon.GeoWoofIcons
import com.geowoof.core.model.RemoteImage

/**
 * Round avatar. The cache key uses the asset id and not the URL, because the signature of a URL
 * changes every time the server sends it, and the image would be downloaded again for nothing
 * (docs/decisions/0010). [localUri] shows a picture just picked, before it has been uploaded.
 * Without [allowHardware] it can be drawn on a bitmap, which is what a marker of the map is.
 */
@Composable
fun RemoteAvatar(
    image: RemoteImage?,
    @DrawableRes fallbackIcon: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    variant: ImageVariant = ImageVariant.Thumb,
    localUri: String? = null,
    allowHardware: Boolean = true,
) {
    val context = LocalContext.current
    val url = if (variant == ImageVariant.Thumb) image?.thumbUrl else image?.mediumUrl
    val cacheKey = image?.let { "${it.assetId}:${variant.name}" }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        val model = remember(context, localUri, url, cacheKey, allowHardware) {
            when {
                localUri != null -> localUri

                url != null -> ImageRequest.Builder(
                    context,
                ).data(url).memoryCacheKey(cacheKey).diskCacheKey(cacheKey).allowHardware(allowHardware).build()

                else -> null
            }
        }
        if (model == null) {
            Icon(
                painter = painterResource(fallbackIcon),
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        } else {
            AsyncImage(
                model = model,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size),
            )
        }
    }
}

/**
 * A photo with its proportions, from the device while it has not been sent and from the server afterwards.
 * The server has no links while it processes a photo: without this, there would be nothing to see, at no size.
 */
@Composable
fun RemotePhoto(image: RemoteImage?, localPath: String?, contentDescription: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val url = image?.mediumUrl ?: image?.thumbUrl
    val cacheKey = image?.let { "${it.assetId}:${ImageVariant.Medium.name}" }
    if (url == null && localPath == null) {
        PhotoNotReady(contentDescription, modifier)
    } else {
        val model = remember(context, localPath, url, cacheKey) {
            when {
                url != null -> ImageRequest.Builder(
                    context,
                ).data(url).memoryCacheKey(cacheKey).diskCacheKey(cacheKey).build()

                else -> localPath
            }
        }
        AsyncImage(
            model = model,
            contentDescription = contentDescription,
            contentScale = ContentScale.FillWidth,
            modifier = modifier.clip(MaterialTheme.shapes.medium),
        )
    }
}

@Composable
private fun PhotoNotReady(description: String?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(PHOTO_NOT_READY_RATIO)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .semantics { description?.let { contentDescription = it } },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(GeoWoofIcons.Image),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private const val PHOTO_NOT_READY_RATIO = 4f / 3f
