package com.geowoof.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** An icon on a circle of color. Decorative: what it means has to be said next to it. */
@Composable
fun IconBadge(
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    iconSize: Dp = 22.dp,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    Box(
        modifier = modifier
            .size(size)
            .background(containerColor, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(iconSize),
            tint = contentColor,
        )
    }
}

/**
 * The dashed trail of the logo as an ornament, stretched to the space it is given. It says nothing,
 * so TalkBack does not see it.
 */
@Composable
fun TrailDecoration(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary.copy(alpha = TRAIL_ALPHA),
    strokeWidth: Dp = 4.dp,
) {
    Canvas(modifier = modifier) {
        val width = strokeWidth.toPx()
        drawPath(
            path = trailPath(size),
            color = color,
            style = Stroke(
                width = width,
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(width * DASH, width * GAP)),
            ),
        )
    }
}

/** The curve of the logo: it goes down and to the left, turns and crosses to the right. */
@Suppress("MagicNumber") // Coordinates of a drawing, as a share of the space it has.
internal fun trailPath(size: Size): Path {
    fun point(x: Float, y: Float) = Offset(size.width * x, size.height * y)
    val start = point(0.30f, 0.04f)
    val turn = point(0.20f, 0.72f)
    val end = point(0.95f, 0.86f)
    return Path().apply {
        moveTo(start.x, start.y)
        cubicTo(
            point(0.02f, 0.14f).x,
            point(0.02f, 0.14f).y,
            point(-0.06f, 0.62f).x,
            point(-0.06f, 0.62f).y,
            turn.x,
            turn.y,
        )
        cubicTo(
            point(0.48f, 0.84f).x,
            point(0.48f, 0.84f).y,
            point(0.74f, 0.56f).x,
            point(0.74f, 0.56f).y,
            end.x,
            end.y,
        )
    }
}

private const val TRAIL_ALPHA = 0.38f
private const val DASH = 3f
private const val GAP = 3.2f
