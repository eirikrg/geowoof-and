package com.geowoof.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.geowoof.core.designsystem.R

/** Proportions of the silhouette. The ring is what fits in the head, leaving a margin of the silhouette around it. */
private const val HEIGHT_PER_WIDTH = 917f / 911f
private const val RING_CENTER_Y = 0.47f
private const val RING_SIZE = 0.575f
private const val FACE_SIZE = 0.5f

/**
 * The marker of the map: a dog's head whose point is the bottom center, with [face] in a white circle.
 * Decorative: what it means has to be said by whoever uses it.
 */
@Composable
fun GeoWoofPin(
    modifier: Modifier = Modifier,
    width: Dp = 56.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    face: @Composable BoxScope.(faceSize: Dp) -> Unit,
) {
    val height = width * HEIGHT_PER_WIDTH
    val ringSize = width * RING_SIZE
    val faceSize = width * FACE_SIZE
    Box(modifier = modifier.size(width, height)) {
        Icon(
            painter = painterResource(R.drawable.core_designsystem_pin),
            contentDescription = null,
            tint = color,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = height * RING_CENTER_Y - ringSize / 2)
                .size(ringSize)
                .background(Color.White, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(modifier = Modifier.size(faceSize).clip(CircleShape), contentAlignment = Alignment.Center) {
                face(faceSize)
            }
        }
    }
}
