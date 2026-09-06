package com.fabienlopes.biotrack.data

import com.fabienlopes.biotrack.domain.Planner

/** Pure Health Connect merge used by the ViewModel and JVM fixtures. */
object HealthImport {
    const val sourceNote = "Health Connect"

    private data class Definition(val name: String, val kind: MetricKind, val unit: String)

    private val definitions = listOf(
        Definition("Durée du sommeil", MetricKind.HOURS_MINUTES, "h"),
        Definition("Pas", MetricKind.NUMBER, "pas"),
        Definition("Poids", MetricKind.NUMBER, "kg"),
        Definition("FC au repos", MetricKind.NUMBER, "bpm"),
        Definition("HRV (RMSSD)", MetricKind.NUMBER, "ms")
    )

    fun merge(snapshot: AppSnapshot, valuesByMetricName: Map<String, Map<Long, Double>>): AppSnapshot {
        if (valuesByMetricName.isEmpty()) return snapshot
        var metrics = snapshot.metrics
        val entries = snapshot.metricEntries.toMutableList()
        valuesByMetricName.forEach { (incomingName, values) ->
            val definition = definitions.firstOrNull { normalizedCatalogKey(it.name) == normalizedCatalogKey(incomingName) }
                ?: return@forEach
            val metric = metrics.firstOrNull { equivalentMetric(it, definition) }
                ?: Metric(name = definition.name, kind = definition.kind, unit = definition.unit).also { metrics += it }
            values.forEach { (date, value) ->
                if (!value.isFinite()) return@forEach
                entries.removeAll { entry ->
                    entry.metricId == metric.id && entry.notes == sourceNote && Planner.sameDay(entry.date, date)
                }
                entries += MetricEntry(metricId = metric.id, date = date, value = value, notes = sourceNote)
            }
        }
        return MetricPresets.ensureIn(snapshot.copy(metrics = metrics, metricEntries = entries))
    }

    private fun equivalentMetric(metric: Metric, definition: Definition): Boolean {
        val existing = normalizedCatalogKey(metric.name)
        val expected = normalizedCatalogKey(definition.name)
        if (existing == expected) return true
        if (definition.kind == MetricKind.HOURS_MINUTES && metric.kind == MetricKind.HOURS_MINUTES) {
            return existing.contains("sommeil")
        }
        return definition.name.startsWith("HRV") && existing.contains("hrv") && existing.contains("rmssd")
    }
}
