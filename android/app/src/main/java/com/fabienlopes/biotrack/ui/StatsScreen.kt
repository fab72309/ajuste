package com.fabienlopes.biotrack.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fabienlopes.biotrack.R
import com.fabienlopes.biotrack.data.BioTrackViewModel
import com.fabienlopes.biotrack.data.CorrelationEvidence
import com.fabienlopes.biotrack.data.Metric
import com.fabienlopes.biotrack.data.NOf1Experiment
import com.fabienlopes.biotrack.data.NOf1Phase
import com.fabienlopes.biotrack.domain.AnalyticsPeriod
import com.fabienlopes.biotrack.domain.AnalyticsSeries
import com.fabienlopes.biotrack.domain.HeatmapDay
import com.fabienlopes.biotrack.domain.RoutineGrouping
import com.fabienlopes.biotrack.domain.Statistics
import com.fabienlopes.biotrack.domain.TrackingAnalytics
import java.util.Locale

private enum class StatsMode { CHART, CALENDAR }
private enum class StatsGrouping { METRICS, PROTOCOLS, SUPPLEMENTS }

@Composable
fun StatsScreen(viewModel: BioTrackViewModel) {
    val snapshot by viewModel.snapshot.collectAsState()
    val context = LocalContext.current
    var selectedMetricIds by remember(snapshot.metrics) { mutableStateOf(snapshot.metrics.firstOrNull()?.let { setOf(it.id) }.orEmpty()) }
    var mode by remember { mutableStateOf(StatsMode.CHART) }
    var period by remember { mutableStateOf(AnalyticsPeriod.DAYS_30) }
    var grouping by remember { mutableStateOf(StatsGrouping.METRICS) }
    var selectedProtocolId by remember { mutableStateOf<String?>(null) }
    var selectedSupplementId by remember { mutableStateOf<String?>(null) }
    var showExperimentDialog by remember { mutableStateOf(false) }
    var observationExperiment by remember { mutableStateOf<NOf1Experiment?>(null) }
    var csvToWrite by remember { mutableStateOf<String?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri: Uri? ->
        val payload = csvToWrite
        if (uri != null && payload != null) context.contentResolver.openOutputStream(uri)?.writer()?.use { it.write(payload) }
        csvToWrite = null
    }
    val series = when (grouping) {
        StatsGrouping.METRICS -> TrackingAnalytics.metricSeries(snapshot, selectedMetricIds, period)
        StatsGrouping.PROTOCOLS -> TrackingAnalytics.routineSeries(snapshot, RoutineGrouping.PROTOCOLS, selectedProtocolId, period)
        StatsGrouping.SUPPLEMENTS -> TrackingAnalytics.routineSeries(snapshot, RoutineGrouping.SUPPLEMENTS, selectedSupplementId, period)
    }
    val normalize = grouping == StatsGrouping.METRICS && series.map { it.unit.orEmpty() }.toSet().size > 1
    val groupingDisplayLabel = groupingLabel(grouping)

    LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.statistics_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.statistics_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { viewModel.refreshInsights() }) { Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh)) }
            }
        }
        item {
            BioCard {
                Text(stringResource(R.string.display_mode), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = mode == StatsMode.CHART, onClick = { mode = StatsMode.CHART }, label = { Text(stringResource(R.string.chart)) }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.ShowChart, null, Modifier.size(16.dp)) })
                    FilterChip(selected = mode == StatsMode.CALENDAR, onClick = { mode = StatsMode.CALENDAR }, label = { Text(stringResource(R.string.calendar)) }, leadingIcon = { Icon(Icons.Default.CalendarMonth, null, Modifier.size(16.dp)) })
                }
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.period), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    items(AnalyticsPeriod.entries) { value ->
                        FilterChip(selected = period == value, onClick = { period = value }, label = { Text(periodLabel(value)) })
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.displayed_data), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    items(StatsGrouping.entries) { value ->
                        FilterChip(selected = grouping == value, onClick = { grouping = value }, label = { Text(groupingLabel(value)) })
                    }
                }
            }
        }
        if (grouping == StatsGrouping.METRICS) item {
            BioCard {
                Text(stringResource(R.string.select_metrics), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (snapshot.metrics.isEmpty()) Text(stringResource(R.string.add_metrics_tracking), color = MaterialTheme.colorScheme.onSurfaceVariant)
                else LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    items(snapshot.metrics, key = { it.id }) { metric ->
                        val selected = metric.id in selectedMetricIds
                        FilterChip(
                            selected = selected,
                            enabled = selected || selectedMetricIds.size < 3,
                            onClick = {
                                selectedMetricIds = if (selected) {
                                    if (selectedMetricIds.size > 1) selectedMetricIds - metric.id else selectedMetricIds
                                } else selectedMetricIds + metric.id
                            },
                            label = { Text(if (snapshot.metricEntries.any { it.metricId == metric.id }) metric.name else stringResource(R.string.metric_empty_suffix, metric.name)) }
                        )
                    }
                }
                Text(stringResource(R.string.metric_selection_hint), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (grouping == StatsGrouping.PROTOCOLS) item {
            RoutineFilterCard(stringResource(R.string.filter_protocols), snapshot.protocols.map { it.id to it.name }, selectedProtocolId) { selectedProtocolId = it }
        }
        if (grouping == StatsGrouping.SUPPLEMENTS) item {
            RoutineFilterCard(stringResource(R.string.filter_supplements), snapshot.supplements.map { it.id to it.name }, selectedSupplementId) { selectedSupplementId = it }
        }
        item {
            BioCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(if (mode == StatsMode.CHART) R.string.evolution else R.string.heatmap), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.grouping_period, groupingDisplayLabel, periodDescription(period)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = {
                        csvToWrite = if (grouping == StatsGrouping.METRICS) {
                            val ids = selectedMetricIds
                            val entries = snapshot.metricEntries.filter { it.metricId in ids && series.any { item -> item.id == it.metricId && item.points.any { point -> sameAnalyticsDay(point.day, it.date) } } }
                            TrackingAnalytics.metricsCsv(snapshot.metrics, entries)
                        } else TrackingAnalytics.seriesCsv(groupingDisplayLabel, series)
                        exportLauncher.launch("ajuste-statistiques.csv")
                    }, enabled = series.any { it.points.isNotEmpty() }) { Icon(Icons.Default.FileDownload, contentDescription = stringResource(R.string.export_statistics_csv)) }
                }
                if (series.all { it.points.isEmpty() }) {
                    Text(stringResource(R.string.no_statistics_data), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 20.dp))
                } else if (mode == StatsMode.CHART) {
                    if (normalize) Text(stringResource(R.string.normalized_series_hint), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    MultiSeriesChart(series, normalize, Modifier.fillMaxWidth().height(240.dp))
                    SeriesLegend(series)
                    if (grouping != StatsGrouping.METRICS) Text(stringResource(R.string.scheduled_zero_hint), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    val heatmap = statsHeatmap(snapshot, series, grouping, selectedMetricIds.firstOrNull(), period)
                    HeatmapGrid(heatmap)
                    Text(stringResource(R.string.heatmap_hint), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            BioCard {
                Text(stringResource(R.string.averages_trends), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (series.isEmpty()) Text(stringResource(R.string.no_series_summary), color = MaterialTheme.colorScheme.onSurfaceVariant)
                series.forEach { item ->
                    val summary = TrackingAnalytics.summary(item)
                    Column(Modifier.padding(vertical = 7.dp)) {
                        Text(item.name, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.summary_values, formatAnalyticsValue(summary.average, item.unit), formatAnalyticsValue(summary.minimum, item.unit), formatAnalyticsValue(summary.maximum, item.unit)), style = MaterialTheme.typography.bodySmall)
                        Text(pluralStringResource(R.plurals.sample_days, summary.sampleDays, summary.sampleDays, formatSignedAnalyticsValue(summary.change, item.unit)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(stringResource(R.string.trends_no_causality), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            BioCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.correlation_insights), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.exploratory), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (snapshot.correlationInsights.isEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.no_robust_signal), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    snapshot.correlationInsights.take(6).forEach { insight ->
                        val a = snapshot.metrics.firstOrNull { it.id == insight.metricAId }?.name ?: stringResource(R.string.metric_a)
                        val b = snapshot.metrics.firstOrNull { it.id == insight.metricBId }?.name ?: stringResource(R.string.metric_b)
                        Column(modifier = Modifier.padding(vertical = 7.dp)) {
                            Text(stringResource(R.string.pair_names, a, b), fontWeight = FontWeight.SemiBold)
                            Text(localizedCorrelationSummary(insight, a, b), style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                                InsightStat(stringResource(R.string.pearson), insight.pearson.formatCorrelation(), Modifier.weight(1f))
                                InsightStat(stringResource(R.string.spearman), insight.spearman?.formatCorrelation() ?: "—", Modifier.weight(1f))
                                InsightStat(stringResource(R.string.days_label), insight.sampleSize.toString(), Modifier.weight(1f))
                            }
                            Text(
                                stringResource(R.string.correlation_details, formatInterval(insight.confidenceLower, insight.confidenceUpper), insight.effectiveSampleSize ?: "—", insight.adjustedPValue?.formatPValue() ?: "—"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                            Text(stringResource(R.string.evidence_exploratory, correlationEvidenceLabel(insight.evidence)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
            }
        }
        item {
            BioCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.experiments_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { showExperimentDialog = true }) { Icon(Icons.Default.Add, contentDescription = null); Spacer(Modifier.size(4.dp)); Text(stringResource(R.string.create)) }
                }
                if (snapshot.experiments.isEmpty()) Text(stringResource(R.string.experiments_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                snapshot.experiments.forEach { experiment ->
                    val summary = Statistics.experimentSummary(experiment, snapshot.experimentObservations)
                    Column(modifier = Modifier.padding(vertical = 7.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(experiment.title, fontWeight = FontWeight.SemiBold)
                                Text(experiment.hypothesis.ifBlank { stringResource(R.string.hypothesis_empty) }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = { observationExperiment = experiment }) { Text(stringResource(R.string.observe)) }
                        }
                        val delta = summary.delta?.let { "Δ ${"%.2f".format(it)}" } ?: stringResource(R.string.not_enough_observations)
                        Text(stringResource(R.string.experiment_summary, summary.controlAverage?.let { "%.2f".format(it) } ?: "—", summary.interventionAverage?.let { "%.2f".format(it) } ?: "—", delta), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item {
            Text(stringResource(R.string.statistics_disclaimer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (showExperimentDialog) {
        ExperimentDialog(metrics = snapshot.metrics, onDismiss = { showExperimentDialog = false }) { title, hypothesis, metricId, duration, phase ->
            viewModel.createExperiment(title, hypothesis, metricId, duration, phase)
            showExperimentDialog = false
        }
    }
    observationExperiment?.let { experiment ->
        ObservationDialog(experiment, onDismiss = { observationExperiment = null }) { value, notes ->
            viewModel.recordObservation(experiment.id, value, notes)
            observationExperiment = null
        }
    }
}

@Composable
private fun RoutineFilterCard(title: String, values: List<Pair<String, String>>, selectedId: String?, onSelected: (String?) -> Unit) {
    BioCard {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (values.isEmpty()) Text(stringResource(R.string.no_item_available), color = MaterialTheme.colorScheme.onSurfaceVariant)
        else LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            item { FilterChip(selected = selectedId == null, onClick = { onSelected(null) }, label = { Text(stringResource(R.string.all_filter)) }) }
            items(values, key = { it.first }) { (id, name) ->
                FilterChip(selected = selectedId == id, onClick = { onSelected(if (selectedId == id) null else id) }, label = { Text(name) })
            }
        }
    }
}

@Composable
private fun periodLabel(period: AnalyticsPeriod): String = stringResource(when (period) {
    AnalyticsPeriod.DAYS_7 -> R.string.period_7_days_short
    AnalyticsPeriod.DAYS_30 -> R.string.period_30_days_short
    AnalyticsPeriod.DAYS_90 -> R.string.period_90_days_short
    AnalyticsPeriod.ALL -> R.string.period_all_short
})

@Composable
private fun periodDescription(period: AnalyticsPeriod): String = stringResource(when (period) {
    AnalyticsPeriod.DAYS_7 -> R.string.period_7_days
    AnalyticsPeriod.DAYS_30 -> R.string.period_30_days
    AnalyticsPeriod.DAYS_90 -> R.string.period_90_days
    AnalyticsPeriod.ALL -> R.string.period_all_data
})

@Composable
private fun groupingLabel(grouping: StatsGrouping): String = stringResource(when (grouping) {
    StatsGrouping.METRICS -> R.string.group_metrics
    StatsGrouping.PROTOCOLS -> R.string.protocols_title
    StatsGrouping.SUPPLEMENTS -> R.string.supplements_title
})

@Composable
private fun correlationEvidenceLabel(evidence: CorrelationEvidence?): String = stringResource(when (evidence) {
    CorrelationEvidence.MODERATE -> R.string.evidence_moderate
    CorrelationEvidence.STRONG -> R.string.evidence_strong
    CorrelationEvidence.EXPLORATORY, null -> R.string.to_confirm
})

@Composable
private fun experimentPhaseLabel(phase: NOf1Phase): String = stringResource(when (phase) {
    NOf1Phase.BASELINE_A -> R.string.phase_baseline
    NOf1Phase.INTERVENTION_B -> R.string.phase_intervention
    NOf1Phase.WASHOUT -> R.string.phase_washout
})

private fun statsHeatmap(
    snapshot: com.fabienlopes.biotrack.data.AppSnapshot,
    series: List<AnalyticsSeries>,
    grouping: StatsGrouping,
    metricId: String?,
    period: AnalyticsPeriod
): List<HeatmapDay> {
    if (grouping == StatsGrouping.METRICS) {
        return TrackingAnalytics.heatmap(snapshot.metricEntries.filter { it.metricId == metricId }, period)
    }
    val totals = series.flatMap { it.points }.groupBy { it.day }.mapValues { (_, points) -> points.sumOf { it.value } }
    val maximum = totals.values.maxOrNull()?.takeIf { it > 0.0 } ?: 1.0
    return totals.toSortedMap().map { (day, value) ->
        HeatmapDay(day, if (value > 0) value.toInt().coerceAtLeast(1) else 0, value, (value / maximum).toFloat().coerceIn(0f, 1f))
    }
}

private fun formatAnalyticsValue(value: Double?, unit: String?): String = value?.let {
    val number = if (kotlin.math.abs(it - it.toInt()) < 0.001) it.toInt().toString() else String.format(Locale.getDefault(), "%.1f", it)
    listOf(number, unit).filterNot { part -> part.isNullOrBlank() }.joinToString(" ")
} ?: "—"

private fun formatSignedAnalyticsValue(value: Double?, unit: String?): String = value?.let {
    val number = String.format(Locale.getDefault(), "%+.1f", it)
    listOf(number, unit).filterNot { part -> part.isNullOrBlank() }.joinToString(" ")
} ?: "—"

private fun sameAnalyticsDay(left: Long, right: Long): Boolean {
    val zone = java.time.ZoneId.systemDefault()
    return java.time.Instant.ofEpochMilli(left).atZone(zone).toLocalDate() == java.time.Instant.ofEpochMilli(right).atZone(zone).toLocalDate()
}

@Composable
private fun MetricPicker(label: String, selected: Metric?, metrics: List<Metric>, modifier: Modifier = Modifier, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FilterChip(selected = selected != null, onClick = { expanded = !expanded }, label = { Text(selected?.name ?: stringResource(R.string.choose), maxLines = 1) })
        if (expanded) {
            metrics.forEach { metric -> TextButton(onClick = { onSelected(metric.id); expanded = false }) { Text(metric.name) } }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(10.dp)) { drawCircle(color) }
        Spacer(Modifier.size(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun InsightStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    }
}

private fun Double.formatCorrelation(): String = String.format(Locale.getDefault(), "%.2f", this)

private fun Double.formatPValue(): String = if (this < 0.001) String.format(Locale.getDefault(), "<%.3f", 0.001) else String.format(Locale.getDefault(), "%.3f", this)

private fun formatInterval(lower: Double?, upper: Double?): String = if (lower == null || upper == null) "—" else "[${lower.formatCorrelation()} ; ${upper.formatCorrelation()}]"

@Composable
private fun AccessibleComparisonChart(series: List<List<Double>>, days: List<Long>, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier
                    .width(28.dp)
                    .height(180.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text("+3", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("0", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("−3", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ComparisonChart(series, days, modifier = Modifier.weight(1f).height(180.dp))
        }
        Row(modifier = Modifier.padding(start = 28.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatChartDate(days.firstOrNull()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatChartDate(days.getOrNull(days.lastIndex / 2)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatChartDate(days.lastOrNull()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(stringResource(R.string.standardized_values_hint), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
    }
}

private fun formatChartDate(timestamp: Long?): String = timestamp?.let {
    java.time.format.DateTimeFormatter.ofPattern("dd/MM", Locale.getDefault())
        .format(java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()))
} ?: "—"

@Composable
private fun ComparisonChart(series: List<List<Double>>, days: List<Long>, modifier: Modifier = Modifier) {
    val colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
    val outline = MaterialTheme.colorScheme.outline
    Canvas(modifier) {
        listOf(0.17f, 0.5f, 0.83f).forEach { fraction ->
            drawLine(
                outline.copy(alpha = if (fraction == 0.5f) 0.45f else 0.18f),
                Offset(0f, size.height * fraction),
                Offset(size.width, size.height * fraction),
                strokeWidth = 1.dp.toPx()
            )
        }
        if (days.size < 2) return@Canvas
        val startDay = days.first()
        val daySpan = (days.last() - startDay).coerceAtLeast(1L)
        series.forEachIndexed { seriesIndex, values ->
            if (values.size < 2 || values.size != days.size) return@forEachIndexed
            val path = Path()
            values.forEachIndexed { index, value ->
                val x = size.width * ((days[index] - startDay).toFloat() / daySpan.toFloat())
                val y = size.height / 2f - value.coerceIn(-3.0, 3.0).toFloat() * (size.height / 6f)
                val hasGap = index > 0 && formatDateGap(days[index - 1], days[index]) > 1
                if (index == 0 || hasGap) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, colors[seriesIndex % colors.size], style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
            values.forEachIndexed { index, value ->
                val x = size.width * ((days[index] - startDay).toFloat() / daySpan.toFloat())
                val y = size.height / 2f - value.coerceIn(-3.0, 3.0).toFloat() * (size.height / 6f)
                drawCircle(colors[seriesIndex % colors.size], radius = 3.dp.toPx(), center = Offset(x, y))
            }
        }
        drawLine(outline.copy(alpha = 0.35f), Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), strokeWidth = 1.dp.toPx())
    }
}

private fun formatDateGap(start: Long, end: Long): Long = java.time.temporal.ChronoUnit.DAYS.between(
    java.time.Instant.ofEpochMilli(start).atZone(java.time.ZoneId.systemDefault()).toLocalDate(),
    java.time.Instant.ofEpochMilli(end).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
)

@Composable
private fun ExperimentDialog(metrics: List<Metric>, onDismiss: () -> Unit, onSave: (String, String, String, Int, Int) -> Unit) {
    var title by remember { mutableStateOf("") }
    var hypothesis by remember { mutableStateOf("") }
    var metricId by remember { mutableStateOf(metrics.firstOrNull()?.id) }
    var duration by remember { mutableStateOf("28") }
    var phase by remember { mutableStateOf("7") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.new_experiment)) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            OutlinedTextField(title, { title = it }, label = { Text(stringResource(R.string.reminder_title)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(hypothesis, { hypothesis = it }, label = { Text(stringResource(R.string.hypothesis)) }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            Text(stringResource(R.string.target_metric), style = MaterialTheme.typography.labelMedium)
            metrics.forEach { metric -> FilterChip(selected = metric.id == metricId, onClick = { metricId = metric.id }, label = { Text(metric.name) }) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(duration, { duration = it }, label = { Text(stringResource(R.string.duration_days)) }, modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(phase, { phase = it }, label = { Text(stringResource(R.string.phase_days)) }, modifier = Modifier.weight(1f), singleLine = true)
            }
        }
    }, confirmButton = { Button(onClick = { metricId?.let { onSave(title, hypothesis, it, duration.toIntOrNull() ?: 28, phase.toIntOrNull() ?: 7) } }, enabled = metricId != null) { Text(stringResource(R.string.create)) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

@Composable
private fun ObservationDialog(experiment: NOf1Experiment, onDismiss: () -> Unit, onSave: (Double, String?) -> Unit) {
    var value by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.observe_experiment, experiment.title)) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(stringResource(R.string.current_phase_value, experimentPhaseLabel(Statistics.experimentPhase(experiment))), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(value, { value = it }, label = { Text(stringResource(R.string.value)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(notes, { notes = it }, label = { Text(stringResource(R.string.optional_note)) }, modifier = Modifier.fillMaxWidth())
        }
    }, confirmButton = { Button(onClick = { value.toDoubleOrNull()?.let { onSave(it, notes.takeIf { it.isNotBlank() }) } }, enabled = value.toDoubleOrNull() != null) { Text(stringResource(R.string.save)) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}
