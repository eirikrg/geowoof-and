package com.geowoof.core.ui

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.max

/** How much a photo is enlarged and where it has been moved to, from the centre of the screen. */
@Stable
internal class PhotoZoom {
    var scale by mutableFloatStateOf(MIN_SCALE)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set
    var containerSize = Size.Zero
    var imageSize = Size.Unspecified

    val isZoomed: Boolean get() = scale > MIN_SCALE

    /** Keeps under the fingers the point of the photo that was under them. */
    fun transform(centroid: Offset, pan: Offset, gestureZoom: Float) {
        val newScale = (scale * gestureZoom).coerceIn(MIN_SCALE, MAX_SCALE)
        val growth = newScale / scale
        moveTo(newScale, fromCentre(centroid) * (1 - growth) + offset * growth + pan)
    }

    /** Where a double tap takes it: closer to that point, or back to the whole photo. */
    fun doubleTapTarget(tap: Offset): Pair<Float, Offset> = if (isZoomed) {
        MIN_SCALE to Offset.Zero
    } else {
        DOUBLE_TAP_SCALE to fromCentre(tap) * (1 - DOUBLE_TAP_SCALE)
    }

    suspend fun toggle(tap: Offset) {
        val startScale = scale
        val startOffset = offset
        val (endScale, endOffset) = doubleTapTarget(tap)
        animate(0f, 1f, animationSpec = tween(TOGGLE_MILLIS)) { progress, _ ->
            moveTo(lerp(startScale, endScale, progress), lerp(startOffset, endOffset, progress))
        }
    }

    fun moveTo(newScale: Float, newOffset: Offset) {
        scale = newScale
        offset = limited(newOffset, newScale)
    }

    private fun fromCentre(point: Offset) = point - Offset(containerSize.width / 2, containerSize.height / 2)

    /** Without going past the edges of the photo, or moving at all in a direction where it fits in the screen. */
    private fun limited(candidate: Offset, atScale: Float): Offset {
        val image = fittedImage()
        val maxX = max(0f, (image.width * atScale - containerSize.width) / 2)
        val maxY = max(0f, (image.height * atScale - containerSize.height) / 2)
        return Offset(candidate.x.coerceIn(-maxX, maxX), candidate.y.coerceIn(-maxY, maxY))
    }

    private fun fittedImage(): Size {
        val hasImage = imageSize.isSpecified && imageSize.minDimension > 0 && containerSize.minDimension > 0
        return if (hasImage) {
            imageSize * ContentScale.Fit.computeScaleFactor(imageSize, containerSize).scaleX
        } else {
            containerSize
        }
    }

    private companion object {
        const val MIN_SCALE = 1f
        const val MAX_SCALE = 5f
        const val DOUBLE_TAP_SCALE = 2.5f
        const val TOGGLE_MILLIS = 220
    }
}

/**
 * Two fingers enlarge the photo, a double tap goes closer or back, and when it is enlarged one finger moves it.
 * One finger on a photo that is not enlarged is left alone, for the pager to go to the next one.
 */
internal fun Modifier.photoZoomGestures(zoom: PhotoZoom): Modifier = this
    .onSizeChanged { zoom.containerSize = it.toSize() }
    .pointerInput(zoom) { handlePinchAndPan(zoom) }
    .pointerInput(zoom) {
        coroutineScope { detectTapGestures(onDoubleTap = { tap -> launch { zoom.toggle(tap) } }) }
    }

private suspend fun PointerInputScope.handlePinchAndPan(zoom: PhotoZoom) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent()
            val isPinching = event.changes.count { it.pressed } > 1
            val centroid = event.calculateCentroid()
            if ((isPinching || zoom.isZoomed) && centroid.isSpecified) {
                zoom.transform(centroid, event.calculatePan(), event.calculateZoom())
                event.changes.forEach { if (it.positionChanged()) it.consume() }
            }
        } while (event.changes.any { it.pressed })
    }
}
