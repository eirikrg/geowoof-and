package com.geowoof.core.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.geowoof.core.model.RemoteImage

/**
 * Round avatar. The cache key uses the asset id and not the URL, because the signature of a URL
 * changes every time the server sends it, and the image would be downloaded again for nothing
 * (docs/decisions/0010). [localUri] shows a picture just picked, before it has been uploaded.
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
        val model = remember(context, localUri, url, cacheKey) {
            when {
                localUri != null -> localUri

                url != null -> ImageRequest.Builder(
                    context,
                ).data(url).memoryCacheKey(cacheKey).diskCacheKey(cacheKey).build()

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
