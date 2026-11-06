package com.geowoof.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.geowoof.core.designsystem.icon.GeoWoofIcons
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.core.model.Mention
import com.geowoof.core.model.RemoteImage

private val RepostBadgeSize = 16.dp
private const val SCRIM_ALPHA = 0.5f

/**
 * A photo of a post, cut to fill its space. The cache key uses the asset id and not the URL, because the signature
 * of a URL changes every time the server sends it (docs/decisions/0010).
 */
@Composable
fun PostImage(
    image: RemoteImage,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    variant: ImageVariant = ImageVariant.Medium,
) {
    val context = LocalContext.current
    val url = if (variant == ImageVariant.Thumb) image.thumbUrl else image.mediumUrl
    val cacheKey = "${image.assetId}:${variant.name}"
    val model = remember(context, url, cacheKey) {
        url?.let { ImageRequest.Builder(context).data(it).memoryCacheKey(cacheKey).diskCacheKey(cacheKey).build() }
    }
    Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
        // Without a URL the server is still processing it, and there is only the background.
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** A picture of the device that has just been chosen, before it is uploaded. */
@Composable
fun LocalPhoto(contentUri: String, modifier: Modifier = Modifier) {
    AsyncImage(
        model = contentUri,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier,
    )
}

/** A post in a grid, such as explore or the photos of a profile. A repost says so with an icon. */
@Composable
fun PostGridItem(image: RemoteImage?, isRepost: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val description = stringResource(if (isRepost) R.string.core_ui_open_repost else R.string.core_ui_open_post)
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClickLabel = description, onClick = onClick),
    ) {
        image?.let {
            PostImage(
                image = it,
                contentDescription = description,
                variant = ImageVariant.Thumb,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (isRepost) {
            // On a dark circle, because it can fall on any photo.
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(GeoWoofTheme.spacing.extraSmall)
                    .background(Color.Black.copy(alpha = SCRIM_ALPHA), CircleShape)
                    .padding(GeoWoofTheme.spacing.extraSmall),
            ) {
                Icon(
                    painter = painterResource(GeoWoofIcons.Repeat),
                    contentDescription = null,
                    modifier = Modifier.size(RepostBadgeSize),
                    tint = Color.White,
                )
            }
        }
    }
}

/** Finds each `@username` of [mentions] in the text, without caring about case. The rest stays as plain text. */
internal fun mentionRanges(text: String, mentions: List<Mention>): List<Pair<IntRange, Mention>> =
    mentions.flatMap { mention ->
        Regex("(?<![\\p{L}\\p{N}_.@])@${Regex.escape(mention.username)}(?![A-Za-z0-9_])", RegexOption.IGNORE_CASE)
            .findAll(text)
            .map { it.range to mention }
            .toList()
    }.sortedBy { it.first.first }

/** A text whose mentions open the profile of each person, for a list of those that count according to the server. */
@Composable
fun MentionText(
    text: String,
    mentions: List<Mention>,
    onOpenUser: (userId: String) -> Unit,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val annotated = remember(text, mentions, linkColor) {
        buildMentionText(text, mentions, linkColor, onOpenUser)
    }
    Text(text = annotated, modifier = modifier, style = style)
}

private fun buildMentionText(
    text: String,
    mentions: List<Mention>,
    linkColor: Color,
    onOpenUser: (userId: String) -> Unit,
): AnnotatedString = buildAnnotatedString {
    val linkStyle = TextLinkStyles(SpanStyle(color = linkColor, fontWeight = FontWeight.Medium))
    var position = 0
    mentionRanges(text, mentions).forEach { (range, mention) ->
        append(text.substring(position, range.first))
        withLink(LinkAnnotation.Clickable(mention.userId, linkStyle) { onOpenUser(mention.userId) }) {
            append(text.substring(range))
        }
        position = range.last + 1
    }
    append(text.substring(position))
}

@PreviewLightDark
@Composable
private fun MentionTextPreview() {
    GeoWoofTheme(dynamicColor = false) {
        MentionText(text = "En el Retiro con @bea", mentions = listOf(Mention("bea", "1")), onOpenUser = {})
    }
}
