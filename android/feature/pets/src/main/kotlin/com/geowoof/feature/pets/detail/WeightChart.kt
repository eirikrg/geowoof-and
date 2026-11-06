package com.geowoof.feature.pets.detail

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.geowoof.core.model.WeightEntry
import com.geowoof.feature.pets.R
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.ProvideVicoTheme
import com.patrykandpatrick.vico.compose.m3.common.rememberM3VicoTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** The weight over time. Needs at least two measurements, because one point is not a line. */
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
    val description = stringResource(R.string.feature_pets_chart_description, first.weightKg, last.weightKg)
    val locale = LocalConfiguration.current.locales[0]
    val dates = remember(locale) {
        val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(locale)
        CartesianValueFormatter { _, value, _ -> LocalDate.ofEpochDay(value.toLong()).format(formatter) }
    }
    ProvideVicoTheme(rememberM3VicoTheme()) {
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberLineCartesianLayer(),
                startAxis = VerticalAxis.rememberStart(),
                bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = dates),
            ),
            modelProducer = producer,
            modifier = modifier
                .fillMaxWidth()
                .height(ChartHeight)
                .semantics { contentDescription = description },
        )
    }
}

private val ChartHeight = 200.dp
