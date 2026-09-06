package com.fabienlopes.biotrack

import com.fabienlopes.biotrack.data.AppSnapshot
import com.fabienlopes.biotrack.data.Frequency
import com.fabienlopes.biotrack.data.Metric
import com.fabienlopes.biotrack.data.MetricEntry
import com.fabienlopes.biotrack.data.ProtocolCompletion
import com.fabienlopes.biotrack.data.ProtocolItem
import com.fabienlopes.biotrack.domain.AnalyticsPeriod
import com.fabienlopes.biotrack.domain.HistoryPeriod
import com.fabienlopes.biotrack.domain.RoutineGrouping
import com.fabienlopes.biotrack.domain.TrackingAnalytics
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackingAnalyticsTest {
    private val zone = ZoneId.of("UTC")
    private val now = timestamp(2026, 8, 18, 12)

    @Test
    fun historyFiltersMetricCategoryAndInclusivePeriod() {
        val sleep = Metric(id = "sleep", name = "Durée du sommeil")
        val mood = Metric(id = "mood", name = "Humeur")
        val entries = listOf(
            MetricEntry(id = "old", metricId = "sleep", date = timestamp(2026, 8, 11), value = 400.0),
            MetricEntry(id = "start", metricId = "sleep", date = timestamp(2026, 8, 12), value = 420.0),
            MetricEntry(id = "mood", metricId = "mood", date = timestamp(2026, 8, 18), value = 8.0)
        )

        val filtered = TrackingAnalytics.filterHistory(
            entries = entries,
            metrics = listOf(sleep, mood),
            metricId = null,
            categories = setOf("Sommeil"),
            period = HistoryPeriod.DAYS_7,
            now = now,
            zone = zone
        )

        assertEquals(listOf("start"), filtered.map { it.id })
    }

    @Test
    fun metricSeriesAveragesMultipleEntriesPerDayAndUsesSevenDays() {
        val metric = Metric(id = "energy", name = "Énergie", unit = "1-10")
        val snapshot = AppSnapshot(
            metrics = listOf(metric),
            metricEntries = listOf(
                MetricEntry(metricId = metric.id, date = timestamp(2026, 8, 12, 8), value = 4.0),
                MetricEntry(metricId = metric.id, date = timestamp(2026, 8, 12, 18), value = 8.0),
                MetricEntry(metricId = metric.id, date = timestamp(2026, 8, 18), value = 9.0),
                MetricEntry(metricId = metric.id, date = timestamp(2026, 8, 11), value = 1.0)
            )
        )

        val series = TrackingAnalytics.metricSeries(snapshot, listOf(metric.id), AnalyticsPeriod.DAYS_7, now, zone).single()

        assertEquals(2, series.points.size)
        assertEquals(6.0, series.points.first().value, 0.0001)
        assertEquals(9.0, series.points.last().value, 0.0001)
        assertEquals(3.0, TrackingAnalytics.summary(series).change!!, 0.0001)
    }

    @Test
    fun protocolSeriesKeepsScheduledDaysAtZeroAndSkipsUnscheduledDays() {
        val protocol = ProtocolItem(
            id = "walk",
            name = "Marche",
            startDate = timestamp(2026, 8, 10),
            frequency = Frequency.weekly(listOf(1, 3, 5))
        )
        val snapshot = AppSnapshot(
            protocols = listOf(protocol),
            protocolCompletions = listOf(
                ProtocolCompletion(protocolId = protocol.id, date = timestamp(2026, 8, 17, 9), completed = true)
            )
        )

        val series = TrackingAnalytics.routineSeries(snapshot, RoutineGrouping.PROTOCOLS, null, AnalyticsPeriod.DAYS_7, now, zone).single()

        assertEquals(3, series.points.size)
        assertEquals(listOf(0.0, 0.0, 1.0), series.points.map { it.value })
    }

    @Test
    fun heatmapIncludesEmptyDaysAndNormalizesRecordedValues() {
        val entries = listOf(
            MetricEntry(metricId = "m", date = timestamp(2026, 8, 16), value = 2.0),
            MetricEntry(metricId = "m", date = timestamp(2026, 8, 18), value = 8.0)
        )

        val cells = TrackingAnalytics.heatmap(entries, AnalyticsPeriod.DAYS_7, now, zone)

        assertEquals(7, cells.size)
        assertEquals(0f, cells.first().intensity)
        assertTrue(cells.last().intensity > cells[cells.lastIndex - 2].intensity)
        assertEquals(1, cells.last().entryCount)
    }

    @Test
    fun csvEscapesNamesAndNotesAndUsesIsoDates() {
        val metric = Metric(id = "m1", name = "Humeur, soir")
        val entry = MetricEntry(metricId = metric.id, date = timestamp(2026, 8, 18), value = 7.5, notes = "Dit \"bien\"")

        val csv = TrackingAnalytics.metricsCsv(listOf(metric), listOf(entry))

        assertTrue(csv.startsWith("metric_id,metric_name,date,value,notes\n"))
        assertTrue(csv.contains("\"Humeur, soir\""))
        assertTrue(csv.contains("\"Dit \"\"bien\"\"\""))
        assertTrue(csv.contains("2026-08-18T00:00:00Z"))
        assertFalse(csv.contains("null"))
    }

    private fun timestamp(year: Int, month: Int, day: Int, hour: Int = 0): Long =
        LocalDate.of(year, month, day).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
}
