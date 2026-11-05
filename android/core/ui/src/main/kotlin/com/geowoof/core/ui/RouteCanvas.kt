package com.geowoof.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.geowoof.core.common.geo.PolylineCodec
import com.geowoof.core.model.GeoPoint
import com.geowoof.core.model.RouteSegment
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

/** The lines of the previews of a walk. One that cannot be decoded is left out. */
fun decodeRoute(polylines: List<String>): List<List<GeoPoint>> = polylines.mapNotNull(PolylineCodec::decode)

fun List<RouteSegment>.toLines(): List<List<GeoPoint>> = map { segment -> segment.points.map { it.position } }

/**
 * Draws a route without a map, so that it does not depend on a key or on a connection. North is up
 * and the proportions are kept. Each line is a stretch without pauses: nothing joins them.
 */
@Composable
fun RouteCanvas(
    lines: List<List<GeoPoint>>,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    strokeWidth: Dp = 3.dp,
    padding: Dp = 8.dp,
) {
    val semantics = if (contentDescription == null) {
        Modifier
    } else {
        Modifier.semantics { this.contentDescription = contentDescription }
    }
    Canvas(modifier = modifier.then(semantics)) {
        val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        fitRoute(lines, size.width, size.height, padding.toPx()).forEach { points ->
            if (points.size == 1) {
                drawCircle(color, radius = stroke.width, center = points.single())
            } else {
                val path = Path()
                points.forEachIndexed { index, point ->
                    if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
                }
                drawPath(path, color, style = stroke)
            }
        }
    }
}

/** Scales the route to fit the area, centered. Longitude is corrected: a degree is shorter away from the equator. */
internal fun fitRoute(lines: List<List<GeoPoint>>, width: Float, height: Float, padding: Float): List<List<Offset>> {
    val points = lines.flatten()
    if (points.isEmpty()) return emptyList()

    val minLat = points.minOf { it.latitude }
    val maxLat = points.maxOf { it.latitude }
    val minLon = points.minOf { it.longitude }
    val maxLon = points.maxOf { it.longitude }
    val lonScale = cos(Math.toRadians((minLat + maxLat) / 2))
    val routeWidth = (maxLon - minLon) * lonScale
    val routeHeight = maxLat - minLat
    val usableWidth = max(width - 2 * padding, 0f)
    val usableHeight = max(height - 2 * padding, 0f)
    val scale = when {
        routeWidth <= 0.0 && routeHeight <= 0.0 -> 0.0
        routeWidth <= 0.0 -> usableHeight / routeHeight
        routeHeight <= 0.0 -> usableWidth / routeWidth
        else -> min(usableWidth / routeWidth, usableHeight / routeHeight)
    }
    val marginX = (width - routeWidth * scale) / 2
    val marginY = (height - routeHeight * scale) / 2
    return lines.map { line ->
        line.map { point ->
            Offset(
                x = (marginX + (point.longitude - minLon) * lonScale * scale).toFloat(),
                y = (height - marginY - (point.latitude - minLat) * scale).toFloat(),
            )
        }
    }
}
