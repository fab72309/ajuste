package com.fabienlopes.biotrack.data

import kotlinx.serialization.Serializable
import java.util.UUID

private fun newId(): String = UUID.randomUUID().toString()

@Serializable
enum class MetricKind { NUMBER, HOURS_MINUTES }

@Serializable
data class Metric(
    val id: String = newId(),
    val name: String,
    val kind: MetricKind = MetricKind.NUMBER,
    val unit: String? = null,
    val description: String? = null
)

@Serializable
data class MetricEntry(
    val id: String = newId(),
    val metricId: String,
    val date: Long = System.currentTimeMillis(),
    val value: Double,
    val notes: String? = null
)

@Serializable
enum class CheckInPeriod {
    MORNING,
    EVENING;

    val displayName: String
        get() = if (this == MORNING) "Matin" else "Soir"
}

@Serializable
data class DailyCheckIn(
    val id: String = newId(),
    val date: Long = System.currentTimeMillis(),
    val period: CheckInPeriod,
    val energy: Int,
    val mood: Int,
    val sleepQuality: Int? = null,
    val stress: Int? = null,
    val note: String? = null
)

@Serializable
enum class FrequencyKind { DAILY, WEEKLY, TIMES_PER_DAY }

/** A deliberately simple representation that remains easy to export and migrate. */
@Serializable
data class Frequency(
    val kind: FrequencyKind = FrequencyKind.DAILY,
    val days: List<Int> = emptyList(),
    val timesPerDay: Int = 1
) {
    /** `weekly(days = [])` is the persisted, backward-compatible "as needed" value. */
    val isAsNeeded: Boolean
        get() = kind == FrequencyKind.WEEKLY && days.isEmpty()

    val hasSpecificDays: Boolean
        get() = kind == FrequencyKind.WEEKLY && days.isNotEmpty()

    /** The number of occurrences represented by this frequency on a scheduled day. */
    val effectiveTimesPerDay: Int
        get() = if (kind == FrequencyKind.TIMES_PER_DAY) timesPerDay.coerceAtLeast(1) else 1

    companion object {
        fun daily() = Frequency()
        fun weekly(days: List<Int>) = Frequency(FrequencyKind.WEEKLY, days.distinct().sorted())
        fun timesPerDay(value: Int) = Frequency(FrequencyKind.TIMES_PER_DAY, timesPerDay = value.coerceAtLeast(1))
    }
}

@Serializable
data class ActivationSpan(
    val start: Long,
    val end: Long? = null
)

/**
 * Optional reminder settings attached to an item or reusable template.
 * Weekdays use ISO values: 1 = Monday through 7 = Sunday. An empty list means every day.
 */
@Serializable
data class ItemReminderConfig(
    val enabled: Boolean = true,
    val title: String? = null,
    val notes: String? = null,
    val hour: Int = 8,
    val minute: Int = 0,
    val weekdays: List<Int> = emptyList()
)

@Serializable
data class ProtocolItem(
    val id: String = newId(),
    val name: String,
    val detail: String? = null,
    val goal: String? = null,
    val intervention: String? = null,
    val frequency: Frequency = Frequency.daily(),
    val preferredHour: Int? = null,
    val preferredMinute: Int? = null,
    val targetMinutes: Int? = null,
    val notes: String? = null,
    val remindersEnabled: Boolean = false,
    val isArchived: Boolean = false,
    val startDate: Long = System.currentTimeMillis(),
    val endDate: Long? = null,
    val active: Boolean = true,
    val activationSpans: List<ActivationSpan> = emptyList(),
    val category: String? = null,
    val customReminder: ItemReminderConfig? = null
)

@Serializable
data class ProtocolCompletion(
    val id: String = newId(),
    val protocolId: String,
    val date: Long = System.currentTimeMillis(),
    val completed: Boolean = true,
    val occurrenceIndex: Int? = null,
    val notes: String? = null,
    val goal: String? = null,
    val intervention: String? = null
)

@Serializable
data class CustomProtocolTemplate(
    val id: String = newId(),
    val name: String,
    val detail: String? = null,
    val category: String = "Autre",
    val minutes: Int = 10,
    val frequency: Frequency = Frequency.daily(),
    val hour: Int = 8,
    val minute: Int = 0,
    val goal: String? = null,
    val intervention: String? = null,
    val notes: String? = null,
    val customReminder: ItemReminderConfig? = null
)

@Serializable
data class Supplement(
    val id: String = newId(),
    val name: String,
    val brand: String? = null,
    val dose: String? = null,
    val category: String? = null,
    val timeOfDay: Int? = null,
    val timeContext: String? = null,
    val frequency: Frequency = Frequency.daily(),
    val timesPerDay: Int? = null,
    val daysOfWeek: List<Int>? = null,
    val durationNote: String? = null,
    val notes: String? = null,
    val active: Boolean = true,
    val activationSpans: List<ActivationSpan> = emptyList(),
    val customReminder: ItemReminderConfig? = null
)

@Serializable
data class SupplementIntake(
    val id: String = newId(),
    val supplementId: String,
    val date: Long = System.currentTimeMillis(),
    val taken: Boolean = true,
    val occurrenceIndex: Int? = null,
    val dose: String? = null,
    val brand: String? = null,
    val notes: String? = null
)

@Serializable
data class CustomSupplementTemplate(
    val id: String = newId(),
    val name: String,
    val brand: String? = null,
    val dose: String? = null,
    val category: String = "Autre",
    val timeContext: String? = null,
    val frequency: Frequency = Frequency.daily(),
    val timeOfDay: Int? = null,
    val timesPerDay: Int? = null,
    val daysOfWeek: List<Int>? = null,
    val durationNote: String? = null,
    val notes: String? = null,
    val customReminder: ItemReminderConfig? = null
)

@Serializable
enum class ReminderTargetKind { PROTOCOL, SUPPLEMENT }

@Serializable
data class Reminder(
    val id: String = newId(),
    val notificationBaseId: String = "reminder-$id",
    val title: String,
    val hour: Int,
    val minute: Int,
    val weekdays: List<Int> = emptyList(),
    val notes: String? = null,
    val enabled: Boolean = true,
    val targetKind: ReminderTargetKind? = null,
    val targetId: String? = null
)

@Serializable
enum class RoutineProfileKind { WEEKDAY, WEEKEND, TRAVEL }

@Serializable
data class RoutineProfile(
    val id: String = newId(),
    val kind: RoutineProfileKind,
    val name: String,
    val weekdays: List<Int>,
    val disabledProtocolIds: List<String> = emptyList(),
    val disabledSupplementIds: List<String> = emptyList(),
    val disabledReminderIds: List<String> = emptyList()
)

@Serializable
enum class NOf1ExperimentStatus { DRAFT, ACTIVE, COMPLETED, PAUSED }

@Serializable
enum class NOf1Phase { BASELINE_A, INTERVENTION_B, WASHOUT }

@Serializable
data class NOf1Experiment(
    val id: String = newId(),
    val title: String,
    val hypothesis: String,
    val targetMetricId: String,
    val startDate: Long = System.currentTimeMillis(),
    val durationDays: Int = 28,
    val phaseDurationDays: Int = 7,
    val status: NOf1ExperimentStatus = NOf1ExperimentStatus.ACTIVE,
    val controlLabel: String = "Contrôle",
    val interventionLabel: String = "Intervention"
)

@Serializable
data class NOf1Observation(
    val id: String = newId(),
    val experimentId: String,
    val date: Long = System.currentTimeMillis(),
    val phase: NOf1Phase,
    val value: Double,
    val notes: String? = null
)

@Serializable
data class AdaptiveGoalPolicy(
    val enabled: Boolean = false,
    val aggressiveness: Double = 0.15,
    val minDailyTarget: Int = 1,
    val maxDailyTarget: Int = 20,
    val lastAppliedAt: Long? = null
)

@Serializable
enum class CorrelationEvidence {
    EXPLORATORY,
    MODERATE,
    STRONG;

    val displayName: String
        get() = when (this) {
            EXPLORATORY -> "À confirmer"
            MODERATE -> "Signal cohérent"
            STRONG -> "Signal robuste"
        }
}

@Serializable
data class CorrelationInsight(
    val id: String = newId(),
    val metricAId: String,
    val metricBId: String,
    val windowDays: Int,
    val lagDays: Int,
    val pearson: Double,
    val sampleSize: Int,
    val summary: String,
    val spearman: Double? = null,
    val trendAdjustedPearson: Double? = null,
    val confidenceLower: Double? = null,
    val confidenceUpper: Double? = null,
    val adjustedPValue: Double? = null,
    val effectiveSampleSize: Int? = null,
    val evidence: CorrelationEvidence? = null
)

@Serializable
enum class RecommendationPriority { LOW, MEDIUM, HIGH }

@Serializable
data class RecommendationItem(
    val id: String = newId(),
    val title: String,
    val message: String,
    val actionDeepLink: String? = null,
    val priority: RecommendationPriority = RecommendationPriority.MEDIUM,
    val reason: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
data class AppSnapshot(
    val schemaVersion: Int = 4,
    val protocols: List<ProtocolItem> = emptyList(),
    val customProtocolTemplates: List<CustomProtocolTemplate> = emptyList(),
    val protocolCompletions: List<ProtocolCompletion> = emptyList(),
    val supplements: List<Supplement> = emptyList(),
    val customSupplementTemplates: List<CustomSupplementTemplate> = emptyList(),
    val supplementIntakes: List<SupplementIntake> = emptyList(),
    val metrics: List<Metric> = emptyList(),
    val metricEntries: List<MetricEntry> = emptyList(),
    val reminders: List<Reminder> = emptyList(),
    val dailyCheckIns: List<DailyCheckIn> = emptyList(),
    val routineProfiles: List<RoutineProfile> = emptyList(),
    val activeRoutineProfileKindRaw: String = RoutineProfileKind.WEEKDAY.name,
    val experiments: List<NOf1Experiment> = emptyList(),
    val experimentObservations: List<NOf1Observation> = emptyList(),
    val adaptiveGoalPolicy: AdaptiveGoalPolicy = AdaptiveGoalPolicy(),
    val correlationInsights: List<CorrelationInsight> = emptyList(),
    val recommendations: List<RecommendationItem> = emptyList()
)
