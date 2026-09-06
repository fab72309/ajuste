package com.fabienlopes.biotrack.domain

import com.fabienlopes.biotrack.data.AppSnapshot
import com.fabienlopes.biotrack.data.Frequency
import com.fabienlopes.biotrack.data.FrequencyKind
import com.fabienlopes.biotrack.data.ProtocolItem
import com.fabienlopes.biotrack.data.Reminder
import com.fabienlopes.biotrack.data.RoutineProfile
import com.fabienlopes.biotrack.data.RoutineProfileKind
import com.fabienlopes.biotrack.data.Supplement
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class PlannedItem(
    val id: String,
    val sourceId: String,
    val title: String,
    val kind: PlannedItemKind,
    val done: Boolean,
    val subtitle: String? = null,
    val preferredMinutes: Int? = null,
    val occurrenceIndex: Int = 0,
    val occurrenceCount: Int = 1,
    val frequency: Frequency = Frequency.daily()
)

enum class PlannedItemKind { PROTOCOL, SUPPLEMENT }

data class DailyPlan(
    val items: List<PlannedItem>,
    val reminders: List<Reminder>,
    val profileName: String?
) {
    val done: Int get() = items.count { it.done }
    val total: Int get() = items.size
}

object Planner {
    private val zone: ZoneId get() = ZoneId.systemDefault()

    fun today(now: Long = System.currentTimeMillis()): DailyPlan {
        val snapshot = currentSnapshot ?: return DailyPlan(emptyList(), emptyList(), null)
        return plan(snapshot, now)
    }

    // Used by lightweight consumers that already have a snapshot.
    var currentSnapshot: AppSnapshot? = null

    fun plan(snapshot: AppSnapshot, now: Long = System.currentTimeMillis()): DailyPlan {
        val profile = activeProfile(snapshot, now)
        val protocols = protocolsScheduledToday(snapshot, now)
            .filter { profile?.disabledProtocolIds?.contains(it.id) != true }
            .flatMap { protocol ->
                val count = protocol.frequency.effectiveTimesPerDay
                (0 until count).map { index ->
                    PlannedItem(
                        id = occurrenceId(protocol.id, index),
                        sourceId = protocol.id,
                        title = protocol.name,
                        kind = PlannedItemKind.PROTOCOL,
                        done = isProtocolOccurrenceDone(protocol.id, index, snapshot, now),
                        subtitle = null,
                        preferredMinutes = occurrenceMinutes(preferredMinutes(protocol.preferredHour, protocol.preferredMinute), index, count),
                        occurrenceIndex = index,
                        occurrenceCount = count,
                        frequency = protocol.frequency
                    )
                }
            }
        val supplements = supplementsScheduledToday(snapshot, now)
            .filter { profile?.disabledSupplementIds?.contains(it.id) != true }
            .flatMap { supplement ->
                val count = supplement.frequency.effectiveTimesPerDay
                (0 until count).map { index ->
                    PlannedItem(
                        id = occurrenceId(supplement.id, index),
                        sourceId = supplement.id,
                        title = supplement.name,
                        kind = PlannedItemKind.SUPPLEMENT,
                        done = isSupplementOccurrenceTaken(supplement.id, index, snapshot, now),
                        subtitle = supplement.dose ?: supplement.timeContext,
                        preferredMinutes = occurrenceMinutes(supplement.timeOfDay, index, count),
                        occurrenceIndex = index,
                        occurrenceCount = count,
                        frequency = supplement.frequency
                    )
                }
            }

        val items = (protocols + supplements).sortedWith(
            compareBy<PlannedItem> { it.done }
                .thenBy { preferredDistance(it.preferredMinutes, now) }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
        )
        val reminders = upcomingRemindersToday(snapshot, now)
            .filter { profile?.disabledReminderIds?.contains(it.id) != true }
        return DailyPlan(items, reminders, profile?.name)
    }

    fun activeProfile(snapshot: AppSnapshot, now: Long = System.currentTimeMillis()): RoutineProfile? {
        val kind = activeKind(snapshot, now)
        return snapshot.routineProfiles.firstOrNull { it.kind == kind }
    }

    fun activeKind(snapshot: AppSnapshot, now: Long = System.currentTimeMillis()): RoutineProfileKind {
        snapshot.activeRoutineProfileKindRaw.let { raw ->
            RoutineProfileKind.entries.firstOrNull { it.name == raw }?.let { return it }
        }
        val weekday = localDate(now).dayOfWeek.value
        return if (weekday >= 6) RoutineProfileKind.WEEKEND else RoutineProfileKind.WEEKDAY
    }

    fun protocolsScheduledToday(snapshot: AppSnapshot, now: Long = System.currentTimeMillis()): List<ProtocolItem> =
        snapshot.protocols.filter { protocol ->
            isActive(protocol.active, protocol.activationSpans.map { it.start to it.end }, protocol.startDate, protocol.endDate, now) &&
                isScheduledToday(protocol.frequency, emptyList(), now)
        }

    fun supplementsScheduledToday(snapshot: AppSnapshot, now: Long = System.currentTimeMillis()): List<Supplement> =
        snapshot.supplements.filter { supplement ->
            isActive(supplement.active, supplement.activationSpans.map { it.start to it.end }, null, null, now) &&
                isScheduledToday(supplement.frequency, supplement.daysOfWeek ?: emptyList(), now)
        }

    fun upcomingRemindersToday(snapshot: AppSnapshot, now: Long = System.currentTimeMillis()): List<Reminder> {
        val current = java.time.ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), zone).let { it.hour * 60 + it.minute }
        return remindersScheduledToday(snapshot, now)
            .filter { reminder -> reminder.enabled && reminder.hour * 60 + reminder.minute >= current }
    }

    fun remindersScheduledToday(snapshot: AppSnapshot, now: Long = System.currentTimeMillis()): List<Reminder> {
        val weekday = localDate(now).dayOfWeek.value
        return snapshot.reminders
            .filter { reminder -> reminder.weekdays.isEmpty() || weekday in reminder.weekdays }
            .sortedWith(compareBy<Reminder> { it.hour * 60 + it.minute }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.title })
    }

    fun isProtocolDoneToday(id: String, snapshot: AppSnapshot, now: Long = System.currentTimeMillis()): Boolean =
        snapshot.protocolCompletions.any { it.protocolId == id && it.completed && sameDay(it.date, now) }

    fun isProtocolOccurrenceDone(id: String, occurrenceIndex: Int, snapshot: AppSnapshot, now: Long = System.currentTimeMillis()): Boolean =
        occurrenceDone(
            occurrenceIndex = occurrenceIndex,
            explicitIndices = snapshot.protocolCompletions
                .filter { it.protocolId == id && it.completed && sameDay(it.date, now) }
                .map { it.occurrenceIndex }
        )

    fun isSupplementTakenToday(id: String, snapshot: AppSnapshot, now: Long = System.currentTimeMillis()): Boolean =
        snapshot.supplementIntakes.any { it.supplementId == id && it.taken && sameDay(it.date, now) }

    fun isSupplementOccurrenceTaken(id: String, occurrenceIndex: Int, snapshot: AppSnapshot, now: Long = System.currentTimeMillis()): Boolean =
        occurrenceDone(
            occurrenceIndex = occurrenceIndex,
            explicitIndices = snapshot.supplementIntakes
                .filter { it.supplementId == id && it.taken && sameDay(it.date, now) }
                .map { it.occurrenceIndex }
        )

    private fun occurrenceDone(occurrenceIndex: Int, explicitIndices: List<Int?>): Boolean {
        if (occurrenceIndex in explicitIndices.filterNotNull()) return true
        val occupied = explicitIndices.filterNotNull().toSet()
        val legacyAssignments = generateSequence(0) { it + 1 }
            .filterNot { it in occupied }
            .take(explicitIndices.count { it == null })
            .toSet()
        return occurrenceIndex in legacyAssignments
    }

    fun isScheduledToday(frequency: Frequency, fallbackDays: List<Int>, now: Long): Boolean {
        return when (frequency.kind) {
            FrequencyKind.DAILY, FrequencyKind.TIMES_PER_DAY -> true
            FrequencyKind.WEEKLY -> {
                val days = if (frequency.days.isNotEmpty()) frequency.days else fallbackDays
                days.isNotEmpty() && days.contains(localDate(now).dayOfWeek.value)
            }
        }
    }

    fun isActive(
        active: Boolean,
        spans: List<Pair<Long, Long?>>,
        startDate: Long?,
        endDate: Long?,
        now: Long
    ): Boolean {
        if (spans.isNotEmpty()) return spans.any { (start, end) -> start <= now && (end == null || now < end) }
        if (!active) return false
        if (startDate != null && now < startDate) return false
        if (endDate != null && now >= endDate) return false
        return true
    }

    fun preferredMinutes(hour: Int?, minute: Int?): Int? =
        if (hour == null || minute == null) null else hour * 60 + minute

    fun occurrenceId(sourceId: String, occurrenceIndex: Int): String = "$sourceId#$occurrenceIndex"

    private fun occurrenceMinutes(base: Int?, index: Int, count: Int): Int? {
        if (base == null) return null
        if (count <= 1 || index <= 0) return base.coerceIn(0, 1439)
        val spacing = (12 * 60 / count).coerceAtLeast(60)
        return (base + index * spacing).coerceAtMost(1439)
    }

    private fun preferredDistance(preferred: Int?, now: Long): Int {
        val current = java.time.ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), zone).let { it.hour * 60 + it.minute }
        return preferred?.let { kotlin.math.abs(it - current) } ?: Int.MAX_VALUE - 1
    }

    fun localDate(timestamp: Long): LocalDate = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()

    fun sameDay(left: Long, right: Long): Boolean = localDate(left) == localDate(right)
}
