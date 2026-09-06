package com.fabienlopes.biotrack.domain

import com.fabienlopes.biotrack.data.AppSnapshot
import com.fabienlopes.biotrack.data.Frequency
import com.fabienlopes.biotrack.data.FrequencyKind
import com.fabienlopes.biotrack.data.Metric
import com.fabienlopes.biotrack.data.MetricEntry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class AnalyticsPeriod(val days: Int?) {
    DAYS_7(7), DAYS_30(30), DAYS_90(90), ALL(null)
}

enum class HistoryPeriod(val days: Int?) {
    TODAY(1), DAYS_7(7), DAYS_30(30), ALL(null)
}

enum class RoutineGrouping { PROTOCOLS, SUPPLEMENTS }

data class AnalyticsPoint(val day: Long, val value: Double)

data class AnalyticsSeries(
    val id: String,
    val name: String,
    val unit: String?,
    val points: List<AnalyticsPoint>
)

data class MetricPeriodSummary(
    val sampleDays: Int,
    val average: Double?,
    val minimum: Double?,
    val maximum: Double?,
    val first: Double?,
    val last: Double?
) {
    val change: Double? get() = if (first == null || last == null) null else last - first
}

data class HeatmapDay(
    val day: Long,
    val entryCount: Int,
    val average: Double?,
    val intensity: Float
)

object TrackingAnalytics {
    fun metricCategory(metric: Metric): String {
        val normalized = normalize(metric.name)
        return when {
            "sommeil" in normalized -> "Sommeil"
            "poids" in normalized -> "Corps"
            listOf("humeur", "energie", "concentration", "stress").any { it in normalized } -> "Bien-être"
            else -> "Autre"
        }
    }

    fun filterHistory(
        entries: List<MetricEntry>,
        metrics: List<Metric>,
        metricId: String?,
        categories: Set<String>,
        period: HistoryPeriod,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault()
    ): List<MetricEntry> {
        val start = period.days?.let { startOfPeriod(it, now, zone) }
        val byId = metrics.associateBy { it.id }
        return entries.asSequence()
            .filter { it.value.isFinite() }
            .filter { metricId == null || it.metricId == metricId }
            .filter { entry ->
                categories.isEmpty() || byId[entry.metricId]?.let(::metricCategory) in categories
            }
            .filter { start == null || it.date >= start }
            .sortedByDescending { it.date }
            .toList()
    }

    fun metricSeries(
        snapshot: AppSnapshot,
        metricIds: Collection<String>,
        period: AnalyticsPeriod,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault()
    ): List<AnalyticsSeries> {
        val selected = metricIds.toSet()
        val start = analyticsStart(period, now, snapshot.metricEntries.filter { it.metricId in selected }.minOfOrNull { it.date }, zone)
        return snapshot.metrics.filter { it.id in selected }.map { metric ->
            val points = dailyAverages(snapshot.metricEntries.filter { it.metricId == metric.id }, start, now, zone)
            AnalyticsSeries(metric.id, metric.name, metric.unit, points)
        }
    }

    fun routineSeries(
        snapshot: AppSnapshot,
        grouping: RoutineGrouping,
        selectedId: String?,
        period: AnalyticsPeriod,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault()
    ): List<AnalyticsSeries> {
        val earliest = when (grouping) {
            RoutineGrouping.PROTOCOLS -> snapshot.protocolCompletions.filter { selectedId == null || it.protocolId == selectedId }.minOfOrNull { it.date }
            RoutineGrouping.SUPPLEMENTS -> snapshot.supplementIntakes.filter { selectedId == null || it.supplementId == selectedId }.minOfOrNull { it.date }
        }
        val start = analyticsStart(period, now, earliest, zone)
        val dates = daysBetween(start, now, zone)
        return when (grouping) {
            RoutineGrouping.PROTOCOLS -> snapshot.protocols
                .filter { selectedId == null || it.id == selectedId }
                .mapNotNull { protocol ->
                    val completed = snapshot.protocolCompletions
                        .filter { it.protocolId == protocol.id && it.completed }
                        .groupingBy { localDate(it.date, zone) }.eachCount()
                    val points = dates.mapNotNull { date ->
                        val timestamp = startOfDay(date, zone)
                        if (!isActive(protocol.active, protocol.activationSpans.map { it.start to it.end }, protocol.startDate, protocol.endDate, timestamp) ||
                            !isScheduled(protocol.frequency, emptyList(), date.dayOfWeek.value)
                        ) null else AnalyticsPoint(timestamp, (completed[date] ?: 0).toDouble())
                    }
                    AnalyticsSeries(protocol.id, protocol.name, "fois / jour", points).takeIf { it.points.isNotEmpty() }
                }

            RoutineGrouping.SUPPLEMENTS -> snapshot.supplements
                .filter { selectedId == null || it.id == selectedId }
                .mapNotNull { supplement ->
                    val taken = snapshot.supplementIntakes
                        .filter { it.supplementId == supplement.id && it.taken }
                        .groupingBy { localDate(it.date, zone) }.eachCount()
                    val points = dates.mapNotNull { date ->
                        val timestamp = startOfDay(date, zone)
                        if (!isActive(supplement.active, supplement.activationSpans.map { it.start to it.end }, null, null, timestamp) ||
                            !isScheduled(supplement.frequency, supplement.daysOfWeek.orEmpty(), date.dayOfWeek.value)
                        ) null else AnalyticsPoint(timestamp, (taken[date] ?: 0).toDouble())
                    }
                    AnalyticsSeries(supplement.id, supplement.name, "fois / jour", points).takeIf { it.points.isNotEmpty() }
                }
        }
    }

    fun summary(series: AnalyticsSeries): MetricPeriodSummary {
        val values = series.points.map { it.value }.filter { it.isFinite() }
        return MetricPeriodSummary(
            sampleDays = values.size,
            average = values.averageOrNull(),
            minimum = values.minOrNull(),
            maximum = values.maxOrNull(),
            first = values.firstOrNull(),
            last = values.lastOrNull()
        )
    }

    fun heatmap(
        entries: List<MetricEntry>,
        period: AnalyticsPeriod,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault()
    ): List<HeatmapDay> {
        val earliest = entries.minOfOrNull { it.date }
        val start = analyticsStart(period, now, earliest, zone)
        val finiteByDay = entries.filter { it.value.isFinite() }.groupBy { localDate(it.date, zone) }
        val averages = finiteByDay.mapValues { (_, values) -> values.map { it.value }.average() }
        val minimum = averages.values.minOrNull()
        val maximum = averages.values.maxOrNull()
        val spread = if (minimum != null && maximum != null) maximum - minimum else 0.0
        return daysBetween(start, now, zone).map { date ->
            val values = finiteByDay[date].orEmpty()
            val average = averages[date]
            val intensity = when {
                average == null -> 0f
                spread <= 1e-12 -> 1f
                else -> (0.25 + 0.75 * ((average - checkNotNull(minimum)) / spread)).toFloat().coerceIn(0f, 1f)
            }
            HeatmapDay(startOfDay(date, zone), values.size, average, intensity)
        }
    }

    fun metricsCsv(metrics: List<Metric>, entries: List<MetricEntry>): String {
        val names = metrics.associate { it.id to it.name }
        val rows = entries.sortedBy { it.date }.map { entry ->
            listOf(
                entry.metricId,
                names[entry.metricId].orEmpty(),
                DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(entry.date)),
                entry.value.toString(),
                entry.notes.orEmpty()
            ).joinToString(",", transform = ::csvField)
        }
        return (listOf("metric_id,metric_name,date,value,notes") + rows).joinToString("\n")
    }

    fun seriesCsv(grouping: String, series: List<AnalyticsSeries>): String {
        val rows = series.flatMap { item ->
            item.points.map { point ->
                listOf(grouping, item.id, item.name, DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(point.day)), point.value.toString())
                    .joinToString(",", transform = ::csvField)
            }
        }.sorted()
        return (listOf("group,item_id,item_name,date,value") + rows).joinToString("\n")
    }

    private fun dailyAverages(entries: List<MetricEntry>, start: Long, end: Long, zone: ZoneId): List<AnalyticsPoint> =
        entries.asSequence()
            .filter { it.value.isFinite() && it.date >= start && it.date <= end }
            .groupBy { localDate(it.date, zone) }
            .map { (date, values) -> AnalyticsPoint(startOfDay(date, zone), values.map { it.value }.average()) }
            .sortedBy { it.day }

    private fun analyticsStart(period: AnalyticsPeriod, now: Long, earliest: Long?, zone: ZoneId): Long =
        period.days?.let { startOfPeriod(it, now, zone) }
            ?: earliest?.let { startOfDay(localDate(it, zone), zone) }
            ?: startOfPeriod(30, now, zone)

    private fun startOfPeriod(days: Int, now: Long, zone: ZoneId): Long {
        val startDate = localDate(now, zone).minusDays((days - 1).coerceAtLeast(0).toLong())
        return startOfDay(startDate, zone)
    }

    private fun daysBetween(start: Long, end: Long, zone: ZoneId): List<LocalDate> {
        val first = localDate(start, zone)
        val last = localDate(end, zone)
        return generateSequence(first) { previous -> previous.plusDays(1).takeIf { it <= last } }.toList()
    }

    private fun isScheduled(frequency: Frequency, fallbackDays: List<Int>, weekday: Int): Boolean = when (frequency.kind) {
        FrequencyKind.DAILY, FrequencyKind.TIMES_PER_DAY -> true
        FrequencyKind.WEEKLY -> (frequency.days.ifEmpty { fallbackDays }).contains(weekday)
    }

    private fun isActive(active: Boolean, spans: List<Pair<Long, Long?>>, start: Long?, end: Long?, timestamp: Long): Boolean {
        if (spans.isNotEmpty()) return spans.any { (spanStart, spanEnd) -> spanStart <= timestamp && (spanEnd == null || timestamp < spanEnd) }
        return active && (start == null || timestamp >= start) && (end == null || timestamp < end)
    }

    private fun localDate(timestamp: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()

    private fun startOfDay(date: LocalDate, zone: ZoneId): Long = date.atStartOfDay(zone).toInstant().toEpochMilli()

    private fun normalize(value: String): String = java.text.Normalizer.normalize(value.lowercase(Locale.FRENCH), java.text.Normalizer.Form.NFD)
        .replace("\\p{Mn}+".toRegex(), "")

    private fun csvField(value: String): String = "\"${value.replace("\"", "\"\"")}\""

    private fun List<Double>.averageOrNull(): Double? = if (isEmpty()) null else average()
}
