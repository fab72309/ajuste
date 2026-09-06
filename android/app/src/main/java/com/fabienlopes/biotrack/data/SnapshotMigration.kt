package com.fabienlopes.biotrack.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class UnsupportedSnapshotVersionException(val version: Int) :
    IllegalArgumentException("Unsupported AJUSTE Android snapshot schema version: $version")

/**
 * Android snapshots from schemas 1, 2 and 3 decode through model defaults, then converge on
 * schema 4. The transform is deliberately idempotent and keeps `weekly(days = [])` as the
 * persisted representation of "Si besoin".
 */
object SnapshotMigration {
    const val CURRENT_SCHEMA_VERSION = 4
    private val supportedLegacyVersions = 1 until CURRENT_SCHEMA_VERSION

    fun decode(raw: String, json: Json): AppSnapshot {
        val root = json.parseToJsonElement(raw).jsonObject
        val declaredVersion = root["schemaVersion"]?.jsonPrimitive?.intOrNull ?: 1
        validateVersion(declaredVersion)
        val decoded = json.decodeFromJsonElement(AppSnapshot.serializer(), root)
        return migrate(decoded.copy(schemaVersion = declaredVersion))
    }

    fun migrate(snapshot: AppSnapshot): AppSnapshot {
        validateVersion(snapshot.schemaVersion)
        val normalized = snapshot.copy(
            schemaVersion = CURRENT_SCHEMA_VERSION,
            protocols = snapshot.protocols.map { protocol ->
                protocol.copy(
                    frequency = protocol.frequency.normalized(),
                    customReminder = protocol.customReminder?.normalized()
                )
            },
            customProtocolTemplates = snapshot.customProtocolTemplates.map { template ->
                template.copy(
                    minutes = template.minutes.coerceAtLeast(1),
                    frequency = template.frequency.normalized(),
                    hour = template.hour.coerceIn(0, 23),
                    minute = template.minute.coerceIn(0, 59),
                    customReminder = template.customReminder?.normalized()
                )
            },
            supplements = snapshot.supplements.map { supplement ->
                val frequency = supplement.frequency.withLegacySupplementFallbacks(
                    timesPerDay = supplement.timesPerDay,
                    daysOfWeek = supplement.daysOfWeek
                )
                supplement.copy(
                    frequency = frequency,
                    timesPerDay = supplement.timesPerDay?.coerceAtLeast(1),
                    daysOfWeek = supplement.daysOfWeek?.normalizedWeekdays(),
                    customReminder = supplement.customReminder?.normalized()
                )
            },
            customSupplementTemplates = snapshot.customSupplementTemplates.map { template ->
                val frequency = template.frequency.withLegacySupplementFallbacks(
                    timesPerDay = template.timesPerDay,
                    daysOfWeek = template.daysOfWeek
                )
                template.copy(
                    frequency = frequency,
                    timesPerDay = template.timesPerDay?.coerceAtLeast(1),
                    daysOfWeek = template.daysOfWeek?.normalizedWeekdays(),
                    customReminder = template.customReminder?.normalized()
                )
            },
            reminders = snapshot.reminders.map { reminder ->
                reminder.copy(
                    hour = reminder.hour.coerceIn(0, 23),
                    minute = reminder.minute.coerceIn(0, 59),
                    weekdays = reminder.weekdays.normalizedWeekdays()
                )
            }
        )
        return MetricPresets.ensureIn(normalized)
    }

    fun sourceVersion(raw: String, json: Json): Int {
        val root = json.parseToJsonElement(raw).jsonObject
        return root["schemaVersion"]?.jsonPrimitive?.intOrNull ?: 1
    }

    private fun validateVersion(version: Int) {
        if (version != CURRENT_SCHEMA_VERSION && version !in supportedLegacyVersions) {
            throw UnsupportedSnapshotVersionException(version)
        }
    }

    private fun Frequency.normalized(): Frequency = copy(
        days = days.normalizedWeekdays(),
        timesPerDay = timesPerDay.coerceAtLeast(1)
    )

    private fun Frequency.withLegacySupplementFallbacks(
        timesPerDay: Int?,
        daysOfWeek: List<Int>?
    ): Frequency {
        val normalized = normalized()
        return when {
            normalized.kind == FrequencyKind.DAILY && (timesPerDay ?: 1) > 1 ->
                normalized.copy(kind = FrequencyKind.TIMES_PER_DAY, timesPerDay = checkNotNull(timesPerDay))
            normalized.kind == FrequencyKind.WEEKLY && normalized.days.isEmpty() && !daysOfWeek.isNullOrEmpty() ->
                normalized.copy(days = daysOfWeek.normalizedWeekdays())
            else -> normalized
        }
    }

    private fun ItemReminderConfig.normalized(): ItemReminderConfig = copy(
        hour = hour.coerceIn(0, 23),
        minute = minute.coerceIn(0, 59),
        weekdays = weekdays.normalizedWeekdays()
    )

    private fun List<Int>.normalizedWeekdays(): List<Int> = filter { it in 1..7 }.distinct().sorted()
}
