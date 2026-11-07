package com.geowoof.feature.map

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/**
 * The panel can change by itself just as a finger arrives, and where the small one has Pause, the whole one has Finish:
 * until it has settled, a touch does nothing.
 */
internal fun Modifier.ignoringTouches(isIgnoring: Boolean): Modifier = if (isIgnoring) {
    pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
        }
    }
} else {
    this
}

/** What leaves goes quickly, so that the two contents are never read on top of each other. */
internal fun AnimatedContentTransitionScope<Boolean>.panelTransition(): ContentTransform = (
    fadeIn(tween(CONTENT_IN_MILLIS, delayMillis = CONTENT_OUT_MILLIS)) +
        scaleIn(tween(RESIZE_MILLIS, easing = EmphasizedEasing), initialScale = CONTENT_INITIAL_SCALE)
    ).togetherWith(fadeOut(tween(CONTENT_OUT_MILLIS)))
    .using(SizeTransform(clip = true) { _, _ -> tween(RESIZE_MILLIS, easing = EmphasizedEasing) })

private const val RESIZE_MILLIS = 380
private const val CONTENT_IN_MILLIS = 220
private const val CONTENT_OUT_MILLIS = 90
private const val CONTENT_INITIAL_SCALE = 0.92f

/** The emphasized curve of Material 3: it starts decided and arrives slowly. */
private val EmphasizedEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
