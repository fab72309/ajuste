package com.fabienlopes.biotrack.data

import com.fabienlopes.biotrack.domain.Planner

object CheckInData {
    fun upsert(
        snapshot: AppSnapshot,
        period: CheckInPeriod,
        energy: Int,
        mood: Int,
        sleepQuality: Int?,
        stress: Int?,
        note: String?,
        now: Long,
        metricValues: Map<String, Double>
    ): AppSnapshot {
        val checkIns = snapshot.dailyCheckIns.filterNot { it.period == period && Planner.sameDay(it.date, now) }
        val marker = marker(period)
        val entries = snapshot.metricEntries.filterNot { entry ->
            entry.notes == marker && Planner.sameDay(entry.date, now)
        } + metricValues.mapNotNull { (metricId, value) ->
            value.takeIf { it.isFinite() }?.let { MetricEntry(metricId = metricId, date = now, value = it, notes = marker) }
        }
        return snapshot.copy(
            dailyCheckIns = checkIns + DailyCheckIn(
                date = now,
                period = period,
                energy = energy.coerceIn(1, 10),
                mood = mood.coerceIn(1, 10),
                sleepQuality = sleepQuality?.coerceIn(1, 10),
                stress = stress?.coerceIn(1, 10),
                note = note?.trim()?.takeIf { it.isNotEmpty() }
            ),
            metricEntries = entries
        )
    }

    fun marker(period: CheckInPeriod): String = "Check-in ${period.name}"

    fun sanitizeSelection(ids: List<String>, metrics: List<Metric>): List<String> {
        val available = metrics.map { it.id }.toSet()
        return ids.filter { it in available }.distinct()
    }

    fun defaultSelection(metrics: List<Metric>): List<String> {
        val names = setOf("energie", "humeur", "qualite du sommeil", "stress")
        return metrics.filter { normalizedCatalogKey(it.name) in names }.map { it.id }
    }
}
