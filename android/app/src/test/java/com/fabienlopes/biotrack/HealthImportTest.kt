package com.fabienlopes.biotrack

import com.fabienlopes.biotrack.data.AppSnapshot
import com.fabienlopes.biotrack.data.HealthImport
import com.fabienlopes.biotrack.data.Metric
import com.fabienlopes.biotrack.data.MetricEntry
import com.fabienlopes.biotrack.data.MetricKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthImportTest {
    @Test
    fun repeatedImportReplacesHealthValueAndPreservesManualEntry() {
        val day = 1_786_915_200_000L
        val steps = Metric(id = "steps", name = "Pas", unit = "pas")
        val snapshot = AppSnapshot(
            metrics = listOf(steps),
            metricEntries = listOf(MetricEntry(id = "manual", metricId = steps.id, date = day, value = 5_000.0, notes = "Saisie manuelle"))
        )

        val first = HealthImport.merge(snapshot, mapOf("Pas" to mapOf(day to 7_000.0)))
        val second = HealthImport.merge(first, mapOf("Pas" to mapOf(day + 1_000 to 8_000.0)))

        assertEquals(2, second.metricEntries.count { it.metricId == steps.id })
        assertTrue(second.metricEntries.any { it.id == "manual" && it.value == 5_000.0 })
        assertEquals(8_000.0, second.metricEntries.single { it.notes == HealthImport.sourceNote }.value, 0.0)
    }

    @Test
    fun sleepUsesExistingDurationMetricAndHrvRemainsRmssd() {
        val day = 1_786_915_200_000L
        val sleep = Metric(id = "sleep", name = "Sommeil", kind = MetricKind.HOURS_MINUTES, unit = "h")
        val merged = HealthImport.merge(
            AppSnapshot(metrics = listOf(sleep)),
            mapOf("Durée du sommeil" to mapOf(day to 480.0), "HRV (RMSSD)" to mapOf(day to 42.0))
        )

        assertEquals("sleep", merged.metricEntries.single { it.value == 480.0 }.metricId)
        assertTrue(merged.metrics.any { it.name == "HRV (RMSSD)" })
        assertTrue(merged.metrics.none { it.name.contains("SDNN") })
    }
}
