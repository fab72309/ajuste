package com.fabienlopes.biotrack.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fabienlopes.biotrack.R
import com.fabienlopes.biotrack.data.AppSnapshot
import com.fabienlopes.biotrack.data.Metric
import com.fabienlopes.biotrack.data.MetricEntry
import com.fabienlopes.biotrack.data.MetricKind
import com.fabienlopes.biotrack.domain.AnalyticsSeries
import com.fabienlopes.biotrack.domain.HeatmapDay
import com.fabienlopes.biotrack.domain.HistoryPeriod
import com.fabienlopes.biotrack.domain.Statistics
import com.fabienlopes.biotrack.domain.TrackingAnalytics
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun MetricTrendCard(metric: Metric, snapshot: AppSnapshot, onAdd: () -> Unit) {
    val series = Statistics.chartSeries(snapshot, metric.id)
    BioCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.metric_last_days, metric.name), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(if (series.isEmpty()) stringResource(R.string.no_data_yet) else pluralStringResource(R.plurals.measured_days, series.size, series.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onAdd) { Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_entry)) }
        }
        Spacer(Modifier.height(12.dp))
        if (series.size >= 2) {
            val values = series.map { it.value }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.width(58.dp).height(170.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    Text(formatValue(values.maxOrNull() ?: 0.0, metric.kind, metric.unit), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    Text(formatValue(values.average(), metric.kind, metric.unit), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    Text(formatValue(values.minOrNull() ?: 0.0, metric.kind, metric.unit), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                SingleLineChart(series.map { it.day to it.value }, modifier = Modifier.weight(1f).height(170.dp))
            }
            Row(modifier = Modifier.padding(start = 58.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(shortAnalyticsDate(series.first().day), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(shortAnalyticsDate(series.last().day), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else Text(stringResource(R.string.trend_needs_two_days), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun MultiSeriesChart(series: List<AnalyticsSeries>, normalize: Boolean, modifier: Modifier = Modifier) {
    val palette = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.tertiary)
    val displaySeries = if (normalize && series.size > 1) series.map { item ->
        item.copy(points = item.points.zip(Statistics.robustStandardScores(item.points.map { it.value })) { point, value -> point.copy(value = value) })
    } else series
    val allValues = displaySeries.flatMap { it.points }.map { it.value }
    val minimum = if (normalize) -3.0 else allValues.minOrNull() ?: 0.0
    val maximum = if (normalize) 3.0 else allValues.maxOrNull() ?: 1.0
    val spread = (maximum - minimum).takeIf { it > 0.000001 } ?: 1.0
    val allDays = displaySeries.flatMap { it.points }.map { it.day }
    val firstDay = allDays.minOrNull() ?: 0L
    val lastDay = allDays.maxOrNull() ?: firstDay + 1
    val span = (lastDay - firstDay).coerceAtLeast(1L)
    val outline = MaterialTheme.colorScheme.outline
    Canvas(modifier) {
        listOf(0f, 0.5f, 1f).forEach { fraction ->
            drawLine(outline.copy(alpha = if (fraction == 0.5f) 0.35f else 0.16f), Offset(0f, size.height * fraction), Offset(size.width, size.height * fraction), strokeWidth = 1.dp.toPx())
        }
        displaySeries.forEachIndexed { seriesIndex, item ->
            if (item.points.size < 2) return@forEachIndexed
            val path = Path()
            item.points.forEachIndexed { index, point ->
                val x = size.width * ((point.day - firstDay).toFloat() / span.toFloat())
                val y = size.height - ((point.value - minimum) / spread).toFloat() * (size.height - 8.dp.toPx()) - 4.dp.toPx()
                val gap = index > 0 && analyticsDayGap(item.points[index - 1].day, point.day) > 1
                if (index == 0 || gap) path.moveTo(x, y) else path.lineTo(x, y)
                drawCircle(palette[seriesIndex % palette.size], radius = 3.dp.toPx(), center = Offset(x, y))
            }
            drawPath(path, palette[seriesIndex % palette.size], style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}

@Composable
internal fun SeriesLegend(series: List<AnalyticsSeries>) {
    val palette = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.tertiary)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(series, key = { it.id }) { item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.width(10.dp).height(10.dp)) { drawCircle(palette[series.indexOf(item) % palette.size]) }
                Spacer(Modifier.width(5.dp))
                Text(item.name, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
internal fun HeatmapGrid(days: List<HeatmapDay>, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.surfaceVariant
    LazyVerticalGrid(columns = GridCells.Fixed(7), modifier = modifier.height(260.dp), userScrollEnabled = false, horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        items(days, key = { it.day }) { day ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(Instant.ofEpochMilli(day.day).atZone(ZoneId.systemDefault()).dayOfMonth.toString(), style = MaterialTheme.typography.labelSmall)
                Canvas(Modifier.fillMaxWidth().height(22.dp)) {
                    drawRoundRect(if (day.entryCount == 0) empty else primary.copy(alpha = day.intensity.coerceIn(0.2f, 1f)), cornerRadius = androidx.compose.ui.geometry.CornerRadius(5.dp.toPx()))
                }
            }
        }
    }
}

@Composable
internal fun MetricEntryDialog(title: String, metric: Metric, initial: MetricEntry?, onDismiss: () -> Unit, onSave: (Double, String?) -> Unit) {
    val initialMinutes = initial?.value?.toInt() ?: 0
    var numberValue by remember(initial?.id) { mutableStateOf(initial?.value?.toString().orEmpty()) }
    var hours by remember(initial?.id) { mutableStateOf((initialMinutes / 60).toString()) }
    var minutes by remember(initial?.id) { mutableStateOf((initialMinutes % 60).toString()) }
    var notes by remember(initial?.id) { mutableStateOf(initial?.notes.orEmpty()) }
    val parsedValue = if (metric.kind == MetricKind.HOURS_MINUTES) {
        val h = hours.toIntOrNull(); val m = minutes.toIntOrNull()
        if (h != null && m != null && h >= 0 && m in 0..59) (h * 60 + m).toDouble() else null
    } else numberValue.replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (metric.kind == MetricKind.HOURS_MINUTES) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(hours, { hours = it.filter(Char::isDigit) }, label = { Text(stringResource(R.string.hours)) }, modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(minutes, { minutes = it.filter(Char::isDigit).take(2) }, label = { Text(stringResource(R.string.minutes_field)) }, modifier = Modifier.weight(1f), singleLine = true)
            } else OutlinedTextField(numberValue, { numberValue = it }, label = { Text(metric.unit?.let { stringResource(R.string.value_with_unit, it) } ?: stringResource(R.string.value)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(notes, { notes = it }, label = { Text(stringResource(R.string.optional_note)) }, modifier = Modifier.fillMaxWidth(), minLines = 2)
        }
    }, confirmButton = { Button(onClick = { parsedValue?.let { onSave(it, notes.trim().takeIf(String::isNotEmpty)) } }, enabled = parsedValue != null) { Text(stringResource(R.string.save)) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

@Composable
internal fun HistoryFilterDialog(metrics: List<Metric>, selectedMetricId: String?, selectedCategories: Set<String>, period: HistoryPeriod, onDismiss: () -> Unit, onApply: (String?, Set<String>, HistoryPeriod) -> Unit) {
    var metricId by remember { mutableStateOf(selectedMetricId) }
    var categories by remember { mutableStateOf(selectedCategories) }
    var selectedPeriod by remember { mutableStateOf(period) }
    val availableCategories = metrics.map(TrackingAnalytics::metricCategory).distinct()
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.filter_history)) }, text = {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Text(stringResource(R.string.filter_metric), fontWeight = FontWeight.SemiBold) }
            item { LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item { FilterChip(metricId == null, { metricId = null }, { Text(stringResource(R.string.all_filter)) }) }
                items(metrics, key = { it.id }) { metric -> FilterChip(metricId == metric.id, { metricId = metric.id }, { Text(metric.name) }) }
            } }
            item { Text(stringResource(R.string.categories), fontWeight = FontWeight.SemiBold) }
            item { LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(availableCategories) { category -> FilterChip(category in categories, { categories = if (category in categories) categories - category else categories + category }, { Text(category) }) }
            } }
            item { Text(stringResource(R.string.period), fontWeight = FontWeight.SemiBold) }
            item { LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(HistoryPeriod.entries) { value -> FilterChip(selectedPeriod == value, { selectedPeriod = value }, { Text(historyPeriodLabel(value)) }) }
            } }
        }
    }, confirmButton = { Button(onClick = { onApply(metricId, categories, selectedPeriod) }) { Text(stringResource(R.string.apply)) } }, dismissButton = {
        Column { TextButton(onClick = { metricId = null; categories = emptySet(); selectedPeriod = HistoryPeriod.ALL }) { Text(stringResource(R.string.reset)) }; TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    })
}

@Composable
private fun SingleLineChart(points: List<Pair<Long, Double>>, modifier: Modifier = Modifier) {
    val series = AnalyticsSeries("metric", "metric", null, points.map { com.fabienlopes.biotrack.domain.AnalyticsPoint(it.first, it.second) })
    MultiSeriesChart(listOf(series), normalize = false, modifier = modifier)
}

@Composable
private fun historyPeriodLabel(value: HistoryPeriod): String = stringResource(when (value) {
    HistoryPeriod.TODAY -> R.string.period_today
    HistoryPeriod.DAYS_7 -> R.string.period_7_days_short
    HistoryPeriod.DAYS_30 -> R.string.period_30_days_short
    HistoryPeriod.ALL -> R.string.period_all_short
})

internal fun shortAnalyticsDate(timestamp: Long): String = DateTimeFormatter.ofPattern("dd/MM", Locale.getDefault()).format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()))

private fun analyticsDayGap(start: Long, end: Long): Long = java.time.temporal.ChronoUnit.DAYS.between(
    Instant.ofEpochMilli(start).atZone(ZoneId.systemDefault()).toLocalDate(),
    Instant.ofEpochMilli(end).atZone(ZoneId.systemDefault()).toLocalDate()
)
