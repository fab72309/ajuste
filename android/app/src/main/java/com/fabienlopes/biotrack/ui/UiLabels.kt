package com.fabienlopes.biotrack.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.fabienlopes.biotrack.R
import com.fabienlopes.biotrack.data.AppSnapshot
import com.fabienlopes.biotrack.data.CorrelationInsight
import com.fabienlopes.biotrack.data.RecommendationItem
import com.fabienlopes.biotrack.domain.AdherenceInsightKind
import com.fabienlopes.biotrack.domain.Planner
import com.fabienlopes.biotrack.domain.Statistics
import kotlin.math.abs

internal data class RecommendationDisplayText(val title: String, val message: String)

@Composable
internal fun localizedCorrelationSummary(insight: CorrelationInsight, metricA: String, metricB: String): String {
    val strength = stringResource(when {
        abs(insight.pearson) < 0.4 -> R.string.correlation_strength_weak
        abs(insight.pearson) < 0.6 -> R.string.correlation_strength_moderate
        abs(insight.pearson) < 0.8 -> R.string.correlation_strength_strong
        else -> R.string.correlation_strength_very_strong
    })
    val direction = stringResource(if (insight.pearson >= 0) R.string.correlation_direction_same else R.string.correlation_direction_opposite)
    if (insight.lagDays == 0) {
        return stringResource(R.string.correlation_summary_same_day, metricA, metricB, direction, strength, insight.sampleSize)
    }
    val earlier = if (insight.lagDays > 0) metricA else metricB
    val later = if (insight.lagDays > 0) metricB else metricA
    val lag = abs(insight.lagDays)
    val lagLabel = pluralStringResource(R.plurals.correlation_lag_days, lag, lag)
    return stringResource(R.string.correlation_summary_lag, earlier, lagLabel, direction, later, strength, insight.sampleSize)
}

@Composable
internal fun localizedRecommendation(item: RecommendationItem, snapshot: AppSnapshot): RecommendationDisplayText {
    return when (item.reason) {
        "checkin_missing_morning" -> RecommendationDisplayText(
            stringResource(R.string.recommendation_title_morning_checkin),
            stringResource(R.string.recommendation_message_morning_checkin)
        )
        "checkin_missing_evening" -> RecommendationDisplayText(
            stringResource(R.string.recommendation_title_evening_checkin),
            stringResource(R.string.recommendation_message_evening_checkin)
        )
        "daily_pending" -> {
            val pending = (Planner.plan(snapshot).total - Planner.plan(snapshot).done).coerceAtLeast(0)
            RecommendationDisplayText(
                stringResource(R.string.recommendation_title_daily_priorities),
                pluralStringResource(R.plurals.recommendation_pending, pending, pending)
            )
        }
        "streak_positive" -> {
            val streak = Statistics.globalStreak(snapshot)
            RecommendationDisplayText(
                stringResource(R.string.recommendation_title_streak_positive),
                pluralStringResource(R.plurals.recommendation_streak, streak, streak)
            )
        }
        "streak_low" -> RecommendationDisplayText(
            stringResource(R.string.recommendation_title_streak_restart),
            stringResource(R.string.recommendation_message_streak_restart)
        )
        "stress_high" -> RecommendationDisplayText(
            stringResource(R.string.recommendation_title_stress),
            stringResource(R.string.recommendation_message_stress)
        )
        "correlation_signal" -> {
            val insight = snapshot.correlationInsights.firstOrNull { it.summary == item.message }
                ?: snapshot.correlationInsights.firstOrNull()
            val message = insight?.let {
                val a = snapshot.metrics.firstOrNull { metric -> metric.id == it.metricAId }?.name ?: stringResource(R.string.metric_a)
                val b = snapshot.metrics.firstOrNull { metric -> metric.id == it.metricBId }?.name ?: stringResource(R.string.metric_b)
                localizedCorrelationSummary(it, a, b)
            } ?: item.message
            RecommendationDisplayText(stringResource(R.string.recommendation_title_correlation), message)
        }
        "adherence_low", "adherence_high", "adherence_hardest_day" -> {
            val kind = when (item.reason) {
                "adherence_low" -> AdherenceInsightKind.LOW
                "adherence_high" -> AdherenceInsightKind.HIGH
                else -> AdherenceInsightKind.HARDEST_DAY
            }
            val insight = Statistics.adherenceInsights(snapshot).firstOrNull { it.kind == kind }
            val message = when {
                insight == null -> item.message
                kind == AdherenceInsightKind.LOW -> stringResource(R.string.recommendation_adherence_low, insight.days, insight.percent)
                kind == AdherenceInsightKind.HIGH -> stringResource(R.string.recommendation_adherence_high, insight.percent)
                else -> stringResource(
                    R.string.recommendation_adherence_hardest_day,
                    localizedWeekdayLabel(insight.weekday),
                    insight.percent
                )
            }
            RecommendationDisplayText(stringResource(R.string.recommendation_title_adherence), message)
        }
        else -> RecommendationDisplayText(item.title, item.message)
    }
}
