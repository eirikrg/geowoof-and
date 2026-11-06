package com.geowoof.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.geowoof.core.designsystem.R
import com.geowoof.core.designsystem.theme.GeoWoofTheme

/**
 * Where someone is in a few steps in a row, as stops along the dashed trail of the logo. [step] starts
 * at 1. TalkBack reads it as "Step 2 of 3".
 */
@Composable
fun TrailProgress(step: Int, total: Int, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.core_designsystem_step_of, step, total)
    val reached = MaterialTheme.colorScheme.primary
    val ahead = MaterialTheme.colorScheme.outlineVariant
    val halo = MaterialTheme.colorScheme.primaryContainer
    Canvas(
        modifier = modifier
            .width(StopSpacing * (total - 1) + HaloRadius * 2)
            .height(HaloRadius * 2)
            .semantics { contentDescription = description },
    ) {
        val radius = StopRadius.toPx()
        val width = StrokeWidth.toPx()
        val spacing = StopSpacing.toPx()
        val dashes = PathEffect.dashPathEffect(floatArrayOf(width * DASH, width * GAP))
        fun stop(index: Int) = Offset(HaloRadius.toPx() + spacing * index, center.y)
        for (index in 0 until total - 1) {
            drawLine(
                color = if (index + 1 < step) reached else ahead,
                start = stop(index) + Offset(radius * LINE_CLEARANCE, 0f),
                end = stop(index + 1) - Offset(radius * LINE_CLEARANCE, 0f),
                strokeWidth = width,
                cap = StrokeCap.Round,
                pathEffect = dashes,
            )
        }
        for (index in 0 until total) {
            if (index + 1 == step) drawCircle(halo, HaloRadius.toPx(), stop(index))
            drawCircle(if (index < step) reached else ahead, radius, stop(index))
        }
    }
}

private val StopRadius = 6.dp
private val HaloRadius = 12.dp
private val StopSpacing = 56.dp
private val StrokeWidth = 3.dp
private const val LINE_CLEARANCE = 2.2f
private const val DASH = 2f
private const val GAP = 2.4f

@PreviewLightDark
@Composable
private fun TrailProgressPreview() {
    GeoWoofTheme(dynamicColor = false) {
        Surface { TrailProgress(step = 2, total = 3) }
    }
}
