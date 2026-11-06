package com.geowoof.feature.pets.detail

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.core.model.WeightEntry
import com.geowoof.core.ui.formatted
import com.geowoof.feature.pets.R
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.Zoom
import com.patrykandpatrick.vico.compose.cartesian.axis.Axis
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisGuidelineComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLineComponent
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.layer.CartesianLayerPadding
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor

/**
 * The weight over time, from the oldest measurement to the newest, with all of them in view and the last
 * one in words above. Needs at least two, because one point is not a line.
 */
@Composable
internal fun WeightChart(weights: List<WeightEntry>, modifier: Modifier = Modifier) {
    val producer = remember { CartesianChartModelProducer() }
    LaunchedEffect(weights) {
        producer.runTransaction {
            lineModel {
                series(
                    x = weights.map { it.measuredOn.toEpochDay() },
                    y = weights.map { it.weightKg },
                )
            }
        }
    }
    val first = weights.first()
    val last = weights.last()
    val description = stringResource(
        R.string.feature_pets_chart_description,
        formatWeight(first.weightKg),
        formatWeight(last.weightKg),
    )
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(GeoWoofTheme.spacing.medium)) {
        WeightSummary(first, last)
        CartesianChartHost(
            chart = rememberCartesianChart(
                weightLayer(weights),
                startAxis = weightAxis(weights),
                bottomAxis = dateAxis(first.measuredOn, last.measuredOn),
                // Room for the first and the last date, which are centred under their points.
                layerPadding = {
                    CartesianLayerPadding(unscalableStart = FirstDatePadding, unscalableEnd = LastDatePadding)
                },
                // Measured whenever: the distance between two points is in days, not in measurements.
                getXStep = { _, _, _ -> 1.0 },
            ),
            modelProducer = producer,
            modifier = Modifier
                .fillMaxWidth()
                .height(ChartHeight)
                .semantics { contentDescription = description },
            scrollState = rememberVicoScrollState(scrollEnabled = false),
            zoomState = rememberVicoZoomState(zoomEnabled = false, initialZoom = Zoom.Content),
        )
    }
}

@Composable
private fun WeightSummary(first: WeightEntry, last: WeightEntry, modifier: Modifier = Modifier) {
    val change = last.weightKg - first.weightKg
    val changeText = formatWeight(change)
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.feature_pets_weight_value, formatWeight(last.weightKg)),
            style = MaterialTheme.typography.headlineSmall,
        )
        // Without a color: whether gaining weight is good depends on the dog.
        Text(
            text = when {
                changeText == formatWeight(0.0) ->
                    stringResource(R.string.feature_pets_weight_no_change, first.measuredOn.formatted())

                else -> stringResource(
                    R.string.feature_pets_weight_change,
                    if (change > 0) "+$changeText" else changeText,
                    first.measuredOn.formatted(),
                )
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun weightLayer(weights: List<WeightEntry>): LineCartesianLayer {
    val colors = MaterialTheme.colorScheme
    val scale = remember(weights) { weightScale(weights.map(WeightEntry::weightKg)) }
    val point = LineCartesianLayer.Point(
        component = rememberShapeComponent(
            fill = Fill(colors.primary),
            shape = CircleShape,
            strokeFill = Fill(colors.surface),
            strokeThickness = PointRing,
        ),
        size = PointSize,
    )
    return rememberLineCartesianLayer(
        lineProvider = LineCartesianLayer.LineProvider.series(
            LineCartesianLayer.rememberLine(
                fill = LineCartesianLayer.LineFill.single(Fill(colors.primary)),
                stroke = LineCartesianLayer.LineStroke.Continuous(LineWidth),
                areaFill = LineCartesianLayer.AreaFill.single(Fill(colors.primary.copy(alpha = AREA_ALPHA))),
                // With many, the points would only hide the line.
                pointProvider = if (weights.size <= MAX_VISIBLE_POINTS) {
                    LineCartesianLayer.PointProvider.single(point)
                } else {
                    null
                },
            ),
        ),
        rangeProvider = remember(scale) { CartesianLayerRangeProvider.fixed(minY = scale.min, maxY = scale.max) },
    )
}

@Composable
private fun axisLabel() = rememberAxisLabelComponent(
    style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
)

@Composable
private fun weightAxis(weights: List<WeightEntry>): VerticalAxis<Axis.Position.Vertical.Start> {
    val scale = remember(weights) { weightScale(weights.map(WeightEntry::weightKg)) }
    return VerticalAxis.rememberStart(
        line = null,
        label = axisLabel(),
        valueFormatter = remember { CartesianValueFormatter { _, value, _ -> formatWeight(value) } },
        tick = null,
        guideline = rememberAxisGuidelineComponent(
            fill = Fill(MaterialTheme.colorScheme.outlineVariant),
            thickness = HairLine,
            shape = RectangleShape,
        ),
        itemPlacer = remember(scale) { VerticalAxis.ItemPlacer.step({ scale.step }) },
    )
}

@Composable
private fun dateAxis(from: LocalDate, to: LocalDate): HorizontalAxis<Axis.Position.Horizontal.Bottom> {
    val locale = LocalConfiguration.current.locales[0]
    val days = ChronoUnit.DAYS.between(from, to)
    val labels = remember(locale, days) {
        val skeleton = if (days > DAYS_TO_SAY_THE_YEAR) "MMMyy" else "MMMd"
        val formatter = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
        CartesianValueFormatter { _, value, _ -> LocalDate.ofEpochDay(value.toLong()).format(formatter) }
    }
    val spacing = (days / DATE_INTERVALS).toInt().coerceAtLeast(1)
    return HorizontalAxis.rememberBottom(
        line = rememberAxisLineComponent(
            fill = Fill(MaterialTheme.colorScheme.outlineVariant),
            thickness = HairLine,
        ),
        label = axisLabel(),
        valueFormatter = labels,
        tick = null,
        guideline = null,
        itemPlacer = remember(spacing) {
            HorizontalAxis.ItemPlacer.aligned(spacing = { spacing })
        },
    )
}

internal data class WeightScale(val min: Double, val max: Double, val step: Double)

/**
 * Round limits around the measurements, with few lines between them. It does not start at zero, because
 * what matters is the change: a dog of 18 kg that gains 200 g would be a flat line.
 */
internal fun weightScale(values: List<Double>): WeightScale {
    val low = values.min()
    val high = values.max()
    val step = RoundSteps.firstOrNull { ceil(high / it) - floor(low / it) <= MAX_INTERVALS } ?: RoundSteps.last()
    val min = floor(low / step) * step
    val max = ceil(high / step) * step
    return if (max > min) {
        WeightScale(min, max, step)
    } else {
        WeightScale((min - step).coerceAtLeast(0.0), max + step, step)
    }
}

/** In the way of the language of the phone, and only with the decimals it has: 18, 18.5 or 18,5. */
internal fun formatWeight(kg: Double, locale: Locale = Locale.getDefault()): String =
    NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = MAX_DECIMALS }.format(kg)

private val RoundSteps = listOf(0.1, 0.2, 0.5, 1.0, 2.0, 5.0, 10.0, 20.0, 50.0)
private const val MAX_INTERVALS = 4
private const val MAX_DECIMALS = 2
private const val MAX_VISIBLE_POINTS = 24
private const val DATE_INTERVALS = 3L
private const val DAYS_TO_SAY_THE_YEAR = 330L
private const val AREA_ALPHA = 0.1f
private val ChartHeight = 200.dp
private val FirstDatePadding = 12.dp
private val LastDatePadding = 24.dp
private val LineWidth = 2.dp
private val HairLine = 1.dp
private val PointSize = 10.dp
private val PointRing = 2.dp
