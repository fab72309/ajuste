package com.fabienlopes.biotrack

import com.fabienlopes.biotrack.data.AppSnapshot
import com.fabienlopes.biotrack.data.CheckInData
import com.fabienlopes.biotrack.data.CheckInPeriod
import com.fabienlopes.biotrack.data.MetricPresets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckInDataTest {
    @Test
    fun upsertReplacesSamePeriodAndMetricValuesWithoutDuplicating() {
        val day = 1_786_915_200_000L
        val metrics = MetricPresets.all
        val energy = metrics.single { it.name == "Énergie" }
        val initial = AppSnapshot(metrics = metrics)

        val first = CheckInData.upsert(initial, CheckInPeriod.MORNING, 6, 7, 8, null, "note", day, mapOf(energy.id to 6.0))
        val second = CheckInData.upsert(first, CheckInPeriod.MORNING, 9, 8, 7, null, "corrigé", day + 1_000, mapOf(energy.id to 9.0))

        assertEquals(1, second.dailyCheckIns.size)
        assertEquals(9, second.dailyCheckIns.single().energy)
        assertEquals(1, second.metricEntries.size)
        assertEquals(9.0, second.metricEntries.single().value, 0.0)
    }

    @Test
    fun defaultSelectionIsOrderedAndSanitized() {
        val metrics = MetricPresets.all
        val defaults = CheckInData.defaultSelection(metrics)

        assertEquals(listOf("Qualité du sommeil", "Humeur", "Énergie", "Stress"), defaults.map { id -> metrics.single { it.id == id }.name })
        assertEquals(defaults, CheckInData.sanitizeSelection(defaults + defaults.first() + "missing", metrics))
        assertTrue(CheckInData.sanitizeSelection(emptyList(), metrics).isEmpty())
    }
}
