package com.geowoof.core.designsystem.component

import androidx.compose.foundation.Image
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

/** Proportions of the head of the logo, measured on the traced icon. */
private const val HEIGHT_PER_WIDTH = 655f / 701f
private const val FACE_CENTER_Y = 0.403f
private const val FACE_SIZE = 0.456f

/**
 * The head of the logo as a marker: its point is the bottom center, and [face] goes where its face is.
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
    val faceSize = width * FACE_SIZE
    Box(modifier = modifier.size(width, height)) {
        Image(
            painter = painterResource(R.drawable.core_designsystem_logo_pin_face),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
        )
        Icon(
            painter = painterResource(R.drawable.core_designsystem_logo_pin),
            contentDescription = null,
            tint = color,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = height * FACE_CENTER_Y - faceSize / 2)
                .size(faceSize)
                .clip(CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            face(faceSize)
        }
    }
}
