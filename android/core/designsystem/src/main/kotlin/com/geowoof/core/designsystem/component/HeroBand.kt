package com.geowoof.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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

/** The height of the band. The center of the photo is on its bottom edge. */
val HeroBandHeight: Dp = 72.dp
private val BandShape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)
private val PhotoBorder = 4.dp

/** The color of the top bar over a [HeroBand], so that they look like one. */
@Composable
fun heroTopBarColors(): TopAppBarColors =
    TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)

/**
 * The start of the page of someone: a band of color with a little map of the place, which continues the top bar,
 * and their [photo] halfway out of it, of [photoSize] and in a [HeroPhotoFrame]. A profile leaves [photo] empty,
 * because its photo flies over the page to reach the bar.
 */
@Composable
fun HeroBand(photoSize: Dp, modifier: Modifier = Modifier, photo: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(HeroBandHeight + photoSize / 2 + PhotoBorder),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(HeroBandHeight)
                .clip(BandShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
        ) {
            HeroTrail(
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(modifier = Modifier.align(Alignment.BottomCenter)) { photo() }
    }
}

/** The ring around the photo of a [HeroBand], which separates it from the band. */
@Composable
fun HeroPhotoFrame(modifier: Modifier = Modifier, photo: @Composable () -> Unit) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Box(modifier = Modifier.padding(PhotoBorder)) { photo() }
    }
}
