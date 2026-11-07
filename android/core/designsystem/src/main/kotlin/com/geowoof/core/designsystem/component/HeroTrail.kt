package com.geowoof.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A little map of the place, as an ornament: the contour lines of the hills and, over them, a route that crosses the
 * band and passes behind the photo. The route draws itself the first time, as a walk does. It says nothing, so TalkBack
 * does not see it.
 */
@Composable
internal fun HeroTrail(color: Color, modifier: Modifier = Modifier) {
    var hasBeenDrawn by rememberSaveable { mutableStateOf(false) }
    val drawn = remember { Animatable(if (hasBeenDrawn) 1f else 0f) }
    LaunchedEffect(Unit) {
        drawn.animateTo(1f, tween(DRAW_MILLIS, easing = FastOutSlowInEasing))
        hasBeenDrawn = true
    }
    Spacer(
        modifier = modifier.drawWithCache {
            val contours = contourPaths(size, stepPx = CONTOUR_STEP.dp.toPx())
            val contourStroke = Stroke(width = 1.dp.toPx())
            // The bar is on top: fading before reaching it, they do not look cut by a straight line.
            val contourBrush = Brush.verticalGradient(
                0f to color.copy(alpha = 0f),
                CONTOUR_FADE to color.copy(alpha = CONTOUR_ALPHA),
            )
            val route = routePath(size)
            val routeStroke = Stroke(
                width = ROUTE_WIDTH.dp.toPx(),
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(ROUTE_DASH.dp.toPx(), ROUTE_GAP.dp.toPx())),
            )
            // It comes from outside and goes out: fading at the edges, it does not look like it was cut.
            val routeBrush = Brush.horizontalGradient(
                0f to color.copy(alpha = 0f),
                ROUTE_FADE to color.copy(alpha = ROUTE_ALPHA),
                1f - ROUTE_FADE to color.copy(alpha = ROUTE_ALPHA),
                1f to color.copy(alpha = 0f),
            )
            val stops = routeStops(size)
            val stopRadius = STOP_RADIUS.dp.toPx()
            onDrawBehind {
                val progress = drawn.value
                contours.forEach { drawPath(it, contourBrush, alpha = progress, style = contourStroke) }
                clipRect(right = size.width * progress) {
                    drawPath(route, routeBrush, style = routeStroke)
                    stops.forEach { drawCircle(color.copy(alpha = STOP_ALPHA), radius = stopRadius, center = it) }
                }
            }
        },
    )
}

/** It winds from left to right, always forward: that is why it can be revealed from one side to the other. */
@Suppress("MagicNumber") // Coordinates of a drawing, as a share of the space it has.
internal fun routePath(size: Size): Path {
    fun x(share: Float) = size.width * share
    fun y(share: Float) = size.height * share
    return Path().apply {
        moveTo(x(-0.02f), y(0.70f))
        cubicTo(x(0.05f), y(0.92f), x(0.13f), y(0.92f), x(0.20f), y(0.62f))
        cubicTo(x(0.25f), y(0.40f), x(0.33f), y(0.22f), x(0.42f), y(0.34f))
        cubicTo(x(0.47f), y(0.42f), x(0.53f), y(0.60f), x(0.60f), y(0.55f))
        cubicTo(x(0.67f), y(0.50f), x(0.70f), y(0.18f), x(0.79f), y(0.26f))
        cubicTo(x(0.86f), y(0.32f), x(0.90f), y(0.74f), x(1.02f), y(0.52f))
    }
}

/** Two stops of the route, one at each side of the photo. */
@Suppress("MagicNumber") // Coordinates of a drawing, as a share of the space it has.
internal fun routeStops(size: Size): List<Offset> = listOf(
    Offset(size.width * 0.20f, size.height * 0.62f),
    Offset(size.width * 0.79f, size.height * 0.26f),
)

/** Two hills, with their lines one around the other. */
@Suppress("MagicNumber") // Coordinates of a drawing, as a share of the space it has.
private fun contourPaths(size: Size, stepPx: Float): List<Path> = buildList {
    val right = Offset(size.width * 0.90f, size.height * 0.02f)
    val left = Offset(size.width * 0.08f, size.height * 1.05f)
    repeat(5) { add(contourPath(right, radius = stepPx * (it + 1), phase = 0.6f)) }
    repeat(4) { add(contourPath(left, radius = stepPx * (it + 1), phase = 2.4f)) }
}

/** A circle with two waves and flattened, so that it looks like a hill on a map and not like a ripple. */
@Suppress("MagicNumber") // Shape of a drawing.
private fun contourPath(center: Offset, radius: Float, phase: Float): Path = Path().apply {
    repeat(CONTOUR_POINTS + 1) { index ->
        val angle = 2 * PI.toFloat() * index / CONTOUR_POINTS
        val wave = 1f + 0.14f * sin(2 * angle + phase) + 0.07f * sin(3 * angle - phase)
        val x = center.x + radius * wave * 1.5f * cos(angle)
        val y = center.y + radius * wave * 0.72f * sin(angle)
        if (index == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

private const val DRAW_MILLIS = 1100
private const val CONTOUR_ALPHA = 0.11f
private const val CONTOUR_FADE = 0.45f
private const val CONTOUR_STEP = 22
private const val CONTOUR_POINTS = 72
private const val ROUTE_ALPHA = 0.46f
private const val ROUTE_FADE = 0.09f
private const val ROUTE_WIDTH = 2.5f
private const val ROUTE_DASH = 6f
private const val ROUTE_GAP = 7f
private const val STOP_ALPHA = 0.55f
private const val STOP_RADIUS = 3.5f
