package com.geowoof.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val BandHeight = 72.dp
private val BandShape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)
private val PhotoBorder = 4.dp
private const val TRAIL_SHARE = 0.55f
private const val TRAIL_ALPHA = 0.22f

/** The color of the top bar over a [HeroBand], so that they look like one. */
@Composable
fun heroTopBarColors(): TopAppBarColors =
    TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)

/**
 * The start of the page of someone: a band of color with the trail of the logo, which continues the top bar,
 * and their [photo] halfway out of it, of [photoSize].
 */
@Composable
fun HeroBand(photoSize: Dp, modifier: Modifier = Modifier, photo: @Composable BoxScope.(Modifier) -> Unit) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(BandHeight + photoSize / 2 + PhotoBorder),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(BandHeight)
                .clip(BandShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
        ) {
            TrailDecoration(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .fillMaxWidth(TRAIL_SHARE),
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = TRAIL_ALPHA),
            )
        }
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Box { photo(Modifier.padding(PhotoBorder)) }
        }
    }
}
