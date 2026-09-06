package com.fabienlopes.biotrack.domain

import com.fabienlopes.biotrack.data.AppSnapshot
import com.fabienlopes.biotrack.data.CorrelationEvidence
import com.fabienlopes.biotrack.data.CorrelationInsight
import com.fabienlopes.biotrack.data.Metric
import com.fabienlopes.biotrack.data.NOf1Experiment
import com.fabienlopes.biotrack.data.NOf1Phase
import com.fabienlopes.biotrack.data.RecommendationItem
import com.fabienlopes.biotrack.data.RecommendationPriority
import java.time.Instant
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.math.sign
import kotlin.math.tanh

data class CorrelationEstimate(
    val pearson: Double,
    val spearman: Double,
    val trendAdjustedPearson: Double?,
    val confidenceLower: Double,
    val confidenceUpper: Double,
    val pValue: Double,
    val sampleSize: Int,
    val effectiveSampleSize: Int
) {
    val confidenceExcludesZero: Boolean get() = confidenceLower > 0 || confidenceUpper < 0
    val rankAgreementIsAcceptable: Boolean get() =
        sign(pearson) == sign(spearman) && abs(pearson - spearman) <= 0.25
    val trendAgreementIsAcceptable: Boolean get() =
        trendAdjustedPearson != null && sign(pearson) == sign(trendAdjustedPearson) && abs(trendAdjustedPearson) >= 0.20
}

data class ChartPoint(val day: Long, val value: Double)

data class ExperimentSummary(
    val controlAverage: Double?,
    val interventionAverage: Double?,
    val delta: Double?
)

enum class AdherenceInsightKind { LOW, HIGH, HARDEST_DAY }

data class AdherenceInsight(
    val kind: AdherenceInsightKind,
    val days: Int,
    val percent: Int,
    val weekday: Int? = null
)

object Statistics {
    fun estimate(x: List<Double>, y: List<Double>): CorrelationEstimate? {
        if (x.size != y.size || x.size < 4 || x.any { !it.isFinite() } || y.any { !it.isFinite() }) return null
        val pearsonValue = pearson(x, y) ?: return null
        val spearmanValue = spearman(x, y) ?: return null
        val effective = effectiveSampleSize(x, y)
        val interval = fisherConfidenceInterval(pearsonValue, effective)
        return CorrelationEstimate(
            pearson = pearsonValue,
            spearman = spearmanValue,
            trendAdjustedPearson = trendAdjustedPearson(x, y),
            confidenceLower = interval.first,
            confidenceUpper = interval.second,
            pValue = twoSidedPValue(pearsonValue, effective),
            sampleSize = x.size,
            effectiveSampleSize = effective
        )
    }

    fun pearson(x: List<Double>, y: List<Double>): Double? {
        if (x.size != y.size || x.size <= 1) return null
        val meanX = x.average()
        val meanY = y.average()
        var covariance = 0.0
        var varianceX = 0.0
        var varianceY = 0.0
        x.indices.forEach { index ->
            val dx = x[index] - meanX
            val dy = y[index] - meanY
            covariance += dx * dy
            varianceX += dx * dx
            varianceY += dy * dy
        }
        if (varianceX <= 1e-15 || varianceY <= 1e-15) return null
        return (covariance / sqrt(varianceX * varianceY)).coerceIn(-1.0, 1.0)
    }

    fun spearman(x: List<Double>, y: List<Double>): Double? = pearson(averageRanks(x), averageRanks(y))

    fun adjustedPValues(pValues: List<Double>): List<Double> {
        if (pValues.isEmpty()) return emptyList()
        val sorted = pValues.mapIndexed { index, value -> index to value }.sortedWith(compareBy<Pair<Int, Double>> { it.second }.thenBy { it.first })
        val count = pValues.size.toDouble()
        val adjusted = MutableList(pValues.size) { 1.0 }
        var runningMinimum = 1.0
        for (position in sorted.indices.reversed()) {
            val (index, value) = sorted[position]
            val candidate = (value.coerceIn(0.0, 1.0) * count / (position + 1)).coerceIn(0.0, 1.0)
            runningMinimum = min(runningMinimum, candidate)
            adjusted[index] = runningMinimum
        }
        return adjusted
    }

    fun robustStandardScores(values: List<Double>, displayLimit: Double = 3.0): List<Double> {
        if (values.isEmpty() || values.any { !it.isFinite() }) return emptyList()
        val center = median(values)
        val deviations = values.map { abs(it - center) }
        val robustScale = median(deviations) * 1.4826
        val scale = if (robustScale > 1e-9) robustScale else standardDeviation(values)
        if (scale <= 1e-9) return values.map { 0.0 }
        return values.map { ((it - center) / scale).coerceIn(-max(displayLimit, 0.5), max(displayLimit, 0.5)) }
    }

    fun chartSeries(snapshot: AppSnapshot, metricId: String, days: Int = 30, now: Long = System.currentTimeMillis()): List<ChartPoint> {
        val end = Planner.localDate(now)
        val start = end.minusDays((days - 1).coerceAtLeast(0).toLong())
        return snapshot.metricEntries
            .asSequence()
            .filter { it.metricId == metricId && it.value.isFinite() }
            .groupBy { Planner.localDate(it.date) }
            .mapNotNull { (date, entries) ->
                if (date < start || date > end) null else ChartPoint(date.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(), entries.map { it.value }.average())
            }
            .sortedBy { it.day }
    }

    fun generateInsights(
        snapshot: AppSnapshot,
        windowDays: Int = 90,
        now: Long = System.currentTimeMillis(),
        minSampleSize: Int = 12,
        lags: List<Int> = DEFAULT_LAGS
    ): List<CorrelationInsight> {
        if (snapshot.metrics.size < 2) return emptyList()
        val end = Planner.localDate(now)
        val start = end.minusDays((windowDays - 1).coerceAtLeast(1).toLong())
        val maps = snapshot.metrics.associate { metric -> metric.id to dailyAverageMap(snapshot, metric.id, start, end) }
        val candidates = mutableListOf<Candidate>()
        snapshot.metrics.forEachIndexed { i, metricA ->
            snapshot.metrics.drop(i + 1).forEach { metricB ->
                val mapA = maps[metricA.id].orEmpty()
                val mapB = maps[metricB.id].orEmpty()
                lags.distinct().forEach { lag ->
                    val pairs = alignedPairs(mapA, mapB, lag)
                    if (pairs.size >= minSampleSize.coerceAtLeast(4)) {
                        val estimate = estimate(pairs.map { it.first }, pairs.map { it.second })
                        if (estimate != null) candidates += Candidate(metricA, metricB, lag, estimate)
                    }
                }
            }
        }
        val adjusted = adjustedPValues(candidates.map { it.estimate.pValue })
        val eligible = candidates.mapIndexed { index, candidate -> candidate.copy(adjustedPValue = adjusted[index]) }
            .filter { candidate ->
                val e = candidate.estimate
                candidate.adjustedPValue <= 0.10 && abs(e.pearson) >= 0.30 && abs(e.spearman) >= 0.25 &&
                    e.effectiveSampleSize >= 8 && e.confidenceExcludesZero && e.rankAgreementIsAcceptable && e.trendAgreementIsAcceptable
            }
        return eligible.groupBy { it.metricA.id to it.metricB.id }.values.mapNotNull { group ->
            val best = group.maxByOrNull { evidenceScore(it) } ?: return@mapNotNull null
            val e = best.estimate
            CorrelationInsight(
                metricAId = best.metricA.id,
                metricBId = best.metricB.id,
                windowDays = windowDays,
                lagDays = best.lag,
                pearson = e.pearson,
                sampleSize = e.sampleSize,
                summary = summaryText(best.metricA.name, best.metricB.name, e.pearson, best.lag, e.sampleSize),
                spearman = e.spearman,
                trendAdjustedPearson = e.trendAdjustedPearson,
                confidenceLower = e.confidenceLower,
                confidenceUpper = e.confidenceUpper,
                adjustedPValue = best.adjustedPValue,
                effectiveSampleSize = e.effectiveSampleSize,
                evidence = evidenceLevel(e, best.adjustedPValue)
            )
        }.sortedByDescending { insightScore(it) }
    }

    fun adherenceInsights(snapshot: AppSnapshot, days: Int = 14, now: Long = System.currentTimeMillis()): List<AdherenceInsight> {
        var total = 0
        var done = 0
        val weekdayCounts = mutableMapOf<Int, Pair<Int, Int>>()
        for (offset in 0 until days) {
            val day = Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault()).toLocalDate().minusDays(offset.toLong())
            val timestamp = day.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            val plan = Planner.plan(snapshot, timestamp)
            done += plan.done
            total += plan.total
            val previous = weekdayCounts[day.dayOfWeek.value] ?: (0 to 0)
            weekdayCounts[day.dayOfWeek.value] = (previous.first + plan.done) to (previous.second + plan.total)
        }
        val rate = if (total == 0) 0.0 else done.toDouble() / total
        val insights = mutableListOf<AdherenceInsight>()
        if (rate < 0.60) insights += AdherenceInsight(AdherenceInsightKind.LOW, days, (rate * 100).toInt())
        if (rate >= 0.85) insights += AdherenceInsight(AdherenceInsightKind.HIGH, days, (rate * 100).toInt())
        weekdayCounts.minByOrNull { (_, values) -> if (values.second == 0) 0.0 else values.first.toDouble() / values.second }?.let { (weekday, values) ->
            val ratio = if (values.second == 0) 0 else values.first * 100 / values.second
            insights += AdherenceInsight(AdherenceInsightKind.HARDEST_DAY, days, ratio, weekday)
        }
        return insights
    }

    fun recommendations(snapshot: AppSnapshot, now: Long = System.currentTimeMillis(), limit: Int = 8): List<RecommendationItem> {
        val items = mutableListOf<RecommendationItem>()
        val plan = Planner.plan(snapshot, now)
        val pending = plan.total - plan.done
        if (snapshot.dailyCheckIns.none { it.period.name == "MORNING" && Planner.sameDay(it.date, now) }) {
            val hour = Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault()).hour
            if (hour <= 14) items += RecommendationItem(title = "Check-in du matin", message = "Complétez votre check-in du matin pour calibrer votre journée.", priority = RecommendationPriority.HIGH, reason = "checkin_missing_morning")
        }
        if (snapshot.dailyCheckIns.none { it.period.name == "EVENING" && Planner.sameDay(it.date, now) }) {
            val hour = Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault()).hour
            if (hour >= 18) items += RecommendationItem(title = "Check-in du soir", message = "Terminez la journée avec votre check-in pour améliorer vos insights.", priority = RecommendationPriority.MEDIUM, reason = "checkin_missing_evening")
        }
        if (pending > 0) items += RecommendationItem(title = "Priorités du jour", message = "Il reste $pending objectif(s) à compléter aujourd'hui.", priority = if (pending >= 4) RecommendationPriority.HIGH else RecommendationPriority.MEDIUM, reason = "daily_pending")
        val streak = globalStreak(snapshot, now)
        items += if (streak >= 7) {
            RecommendationItem(title = "Série solide", message = "Vous êtes à $streak jours de constance. Gardez le rythme.", priority = RecommendationPriority.LOW, reason = "streak_positive")
        } else {
            RecommendationItem(title = "Relance de constance", message = "Choisissez 1 protocole clé pour reconstruire votre série.", priority = RecommendationPriority.MEDIUM, reason = "streak_low")
        }
        val stress = snapshot.dailyCheckIns.filter { it.period.name == "EVENING" }.maxByOrNull { it.date }?.stress
        if ((stress ?: 0) >= 8) items += RecommendationItem(title = "Check-in : stress élevé", message = "Envisagez d’alléger votre checklist. Si ce ressenti persiste ou vous inquiète, parlez-en à un professionnel.", priority = RecommendationPriority.HIGH, reason = "stress_high")
        snapshot.correlationInsights.firstOrNull { it.evidence == CorrelationEvidence.MODERATE || it.evidence == CorrelationEvidence.STRONG }?.let {
            if (abs(it.pearson) >= 0.45) items += RecommendationItem(title = "Insight de corrélation", message = it.summary, priority = RecommendationPriority.MEDIUM, reason = "correlation_signal")
        }
        adherenceInsights(snapshot, now = now).take(2).forEach { insight ->
            val message = when (insight.kind) {
                AdherenceInsightKind.LOW -> "Votre adhérence sur ${insight.days} jours est faible (${insight.percent}%). Réduisez la charge quotidienne."
                AdherenceInsightKind.HIGH -> "Très bonne constance (${insight.percent}%). Conservez ce rythme ou ajustez vos objectifs prudemment."
                AdherenceInsightKind.HARDEST_DAY -> "Jour le plus difficile: ${insight.weekday ?: "-"} (${insight.percent}%)."
            }
            items += RecommendationItem(title = "Adhérence", message = message, priority = RecommendationPriority.MEDIUM, reason = "adherence_${insight.kind.name.lowercase()}")
        }
        return items.sortedByDescending { when (it.priority) { RecommendationPriority.HIGH -> 3; RecommendationPriority.MEDIUM -> 2; RecommendationPriority.LOW -> 1 } }.take(limit)
    }

    fun globalStreak(snapshot: AppSnapshot, now: Long = System.currentTimeMillis()): Int {
        var streak = 0
        var cursor = Planner.localDate(now)
        while (true) {
            val timestamp = cursor.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            val plan = Planner.plan(snapshot, timestamp)
            if (plan.total > 0 && plan.done == plan.total) {
                streak++
                cursor = cursor.minusDays(1)
            } else break
        }
        return streak
    }

    fun experimentPhase(experiment: NOf1Experiment, date: Long = System.currentTimeMillis()): NOf1Phase {
        val elapsed = ((Planner.localDate(date).toEpochDay() - Planner.localDate(experiment.startDate).toEpochDay()).coerceAtLeast(0)).toInt()
        val block = experiment.phaseDurationDays.coerceAtLeast(1)
        return if ((elapsed / block) % 2 == 0) NOf1Phase.BASELINE_A else NOf1Phase.INTERVENTION_B
    }

    fun experimentSummary(experiment: NOf1Experiment, observations: List<com.fabienlopes.biotrack.data.NOf1Observation>): ExperimentSummary {
        val data = observations.filter { it.experimentId == experiment.id }
        val control = data.filter { it.phase == NOf1Phase.BASELINE_A }.map { it.value }.averageOrNull()
        val intervention = data.filter { it.phase == NOf1Phase.INTERVENTION_B }.map { it.value }.averageOrNull()
        return ExperimentSummary(control, intervention, if (control != null && intervention != null) intervention - control else null)
    }

    private data class Candidate(
        val metricA: Metric,
        val metricB: Metric,
        val lag: Int,
        val estimate: CorrelationEstimate,
        val adjustedPValue: Double = 1.0
    )

    private fun dailyAverageMap(snapshot: AppSnapshot, metricId: String, start: java.time.LocalDate, end: java.time.LocalDate): Map<java.time.LocalDate, Double> =
        snapshot.metricEntries.filter { it.metricId == metricId && it.value.isFinite() }.groupBy { Planner.localDate(it.date) }
            .filterKeys { it >= start && it <= end }.mapValues { (_, entries) -> entries.map { it.value }.average() }

    private fun alignedPairs(a: Map<java.time.LocalDate, Double>, b: Map<java.time.LocalDate, Double>, lag: Int): List<Pair<Double, Double>> =
        a.keys.sorted().mapNotNull { day -> b[day.plusDays(lag.toLong())]?.let { a.getValue(day) to it } }

    private fun evidenceScore(candidate: Candidate): Double {
        val e = candidate.estimate
        val trend = abs(e.trendAdjustedPearson ?: 0.0)
        val strength = min(min(abs(e.pearson), abs(e.spearman)), trend)
        val confidence = min(abs(e.confidenceLower), abs(e.confidenceUpper))
        return strength * 0.62 + confidence * 0.28 + min(1.0, e.effectiveSampleSize / 30.0) * 0.10 - abs(candidate.lag) * 0.02
    }

    private fun insightScore(insight: CorrelationInsight): Double {
        val strength = min(min(abs(insight.pearson), abs(insight.spearman ?: insight.pearson)), abs(insight.trendAdjustedPearson ?: 0.0))
        val confidence = min(abs(insight.confidenceLower ?: 0.0), abs(insight.confidenceUpper ?: 0.0))
        return strength * 0.7 + confidence * 0.3
    }

    private fun evidenceLevel(e: CorrelationEstimate, adjustedP: Double): CorrelationEvidence {
        val strength = min(min(abs(e.pearson), abs(e.spearman)), abs(e.trendAdjustedPearson ?: 0.0))
        return when {
            e.effectiveSampleSize >= 30 && strength >= 0.50 && adjustedP <= 0.01 -> CorrelationEvidence.STRONG
            e.effectiveSampleSize >= 20 && strength >= 0.40 && adjustedP <= 0.05 -> CorrelationEvidence.MODERATE
            else -> CorrelationEvidence.EXPLORATORY
        }
    }

    private fun summaryText(a: String, b: String, pearson: Double, lag: Int, sample: Int): String {
        val strength = when {
            abs(pearson) < 0.4 -> "faible"
            abs(pearson) < 0.6 -> "modérée"
            abs(pearson) < 0.8 -> "forte"
            else -> "très forte"
        }
        val direction = if (pearson >= 0) "dans le même sens" else "en sens opposé"
        if (lag == 0) return "$a et $b évoluent $direction le même jour. Association $strength, sur $sample jours alignés."
        val earlier = if (lag > 0) a else b
        val later = if (lag > 0) b else a
        val count = abs(lag)
        return "$earlier précède de $count ${if (count > 1) "jours" else "jour"} des variations $direction de $later. Association $strength, sur $sample jours alignés."
    }

    private fun averageRanks(values: List<Double>): List<Double> {
        val sorted = values.mapIndexed { index, value -> index to value }.sortedWith(compareBy<Pair<Int, Double>> { it.second }.thenBy { it.first })
        val ranks = MutableList(values.size) { 0.0 }
        var start = 0
        while (start < sorted.size) {
            var end = start
            while (end + 1 < sorted.size && sorted[end + 1].second == sorted[start].second) end++
            val average = (start + 1 + end + 1) / 2.0
            for (index in start..end) ranks[sorted[index].first] = average
            start = end + 1
        }
        return ranks
    }

    private fun effectiveSampleSize(x: List<Double>, y: List<Double>): Int {
        if (x.size < 5) return x.size
        val autocorrelationX = pearson(x.dropLast(1), x.drop(1)) ?: return x.size
        val autocorrelationY = pearson(y.dropLast(1), y.drop(1)) ?: return x.size
        val product = autocorrelationX * autocorrelationY
        if (product <= 0) return x.size
        return (x.size * (1 - product) / (1 + product)).toInt().coerceIn(4, x.size)
    }

    private fun trendAdjustedPearson(x: List<Double>, y: List<Double>): Double? {
        val residualX = linearTrendResiduals(x) ?: return null
        val residualY = linearTrendResiduals(y) ?: return null
        return pearson(residualX, residualY)
    }

    private fun linearTrendResiduals(values: List<Double>): List<Double>? {
        if (values.size < 4) return null
        val meanIndex = (values.size - 1) / 2.0
        val meanValue = values.average()
        var covariance = 0.0
        var indexVariance = 0.0
        values.forEachIndexed { index, value ->
            val centeredIndex = index - meanIndex
            covariance += centeredIndex * (value - meanValue)
            indexVariance += centeredIndex * centeredIndex
        }
        if (indexVariance <= 1e-15) return null
        val slope = covariance / indexVariance
        val residuals = values.mapIndexed { index, value -> value - (meanValue + slope * (index - meanIndex)) }
        val residualEnergy = residuals.sumOf { it * it }
        val originalEnergy = values.sumOf { (it - meanValue) * (it - meanValue) }
        if (residualEnergy <= max(1e-15, originalEnergy * 1e-10)) return null
        return residuals
    }

    private fun fisherConfidenceInterval(correlation: Double, sampleSize: Int): Pair<Double, Double> {
        if (sampleSize <= 3) return -1.0 to 1.0
        val clamped = correlation.coerceIn(-0.999999, 0.999999)
        val fisherZ = 0.5 * ln((1 + clamped) / (1 - clamped))
        val margin = 1.959963984540054 / sqrt((sampleSize - 3).toDouble())
        return tanh(fisherZ - margin) to tanh(fisherZ + margin)
    }

    private fun twoSidedPValue(correlation: Double, sampleSize: Int): Double {
        if (sampleSize <= 3) return 1.0
        val clamped = correlation.coerceIn(-0.999999, 0.999999)
        val fisherZ = abs(0.5 * ln((1 + clamped) / (1 - clamped))) * sqrt((sampleSize - 3).toDouble())
        return erfc(fisherZ / sqrt(2.0)).coerceIn(0.0, 1.0)
    }

    private fun erfc(value: Double): Double {
        val z = abs(value)
        val t = 1 / (1 + 0.5 * z)
        val polynomial = t * exp(-z * z - 1.26551223 + t * (1.00002368 + t * (0.37409196 + t * (0.09678418 + t * (-0.18628806 + t * (0.27886807 + t * (-1.13520398 + t * (1.48851587 + t * (-0.82215223 + t * 0.17087277)))))))))
        return if (value >= 0) polynomial else 2 - polynomial
    }

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[middle - 1] + sorted[middle]) / 2 else sorted[middle]
    }

    private fun standardDeviation(values: List<Double>): Double {
        if (values.size <= 1) return 0.0
        val mean = values.average()
        return sqrt(values.sumOf { (it - mean) * (it - mean) } / (values.size - 1))
    }

    private fun List<Double>.averageOrNull(): Double? = if (isEmpty()) null else average()

    private val DEFAULT_LAGS = listOf(-2, -1, 0, 1, 2)
}
