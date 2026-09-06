package com.fabienlopes.biotrack

import com.fabienlopes.biotrack.data.AppSnapshot
import com.fabienlopes.biotrack.data.CustomProtocolTemplate
import com.fabienlopes.biotrack.data.CustomSupplementTemplate
import com.fabienlopes.biotrack.data.ExchangeFormat
import com.fabienlopes.biotrack.data.ExchangePayloadKind
import com.fabienlopes.biotrack.data.Frequency
import com.fabienlopes.biotrack.data.FrequencyKind
import com.fabienlopes.biotrack.data.ItemReminderConfig
import com.fabienlopes.biotrack.data.Metric
import com.fabienlopes.biotrack.data.MetricEntry
import com.fabienlopes.biotrack.data.MetricKind
import com.fabienlopes.biotrack.data.MetricPresets
import com.fabienlopes.biotrack.data.ProtocolCatalog
import com.fabienlopes.biotrack.data.ProtocolCompletion
import com.fabienlopes.biotrack.data.ProtocolItem
import com.fabienlopes.biotrack.data.Reminder
import com.fabienlopes.biotrack.data.ReminderTargetKind
import com.fabienlopes.biotrack.data.SnapshotMigration
import com.fabienlopes.biotrack.data.Supplement
import com.fabienlopes.biotrack.data.SupplementCatalog
import com.fabienlopes.biotrack.data.SupplementIntake
import com.fabienlopes.biotrack.data.UnsupportedExchangeFormatException
import com.fabienlopes.biotrack.data.UnsupportedSnapshotVersionException
import com.fabienlopes.biotrack.data.normalizedCatalogKey
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DataCompatibilityTest {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun legacyV3SnapshotMigratesWithoutLosingExistingData() {
        val raw = """
            {
              "schemaVersion": 3,
              "protocols": [{
                "id": "protocol-1",
                "name": "Pause libre",
                "frequency": {"kind": "WEEKLY", "days": [], "timesPerDay": 1},
                "remindersEnabled": true
              }],
              "protocolCompletions": [{
                "id": "completion-1", "protocolId": "protocol-1", "date": 1234, "completed": true
              }],
              "supplements": [{
                "id": "supplement-1",
                "name": "Suivi",
                "frequency": {"kind": "DAILY", "days": [], "timesPerDay": 1},
                "timesPerDay": 3
              }],
              "supplementIntakes": [{
                "id": "intake-1", "supplementId": "supplement-1", "date": 5678, "taken": true
              }]
            }
        """.trimIndent()

        val migrated = SnapshotMigration.decode(raw, json)

        assertEquals(4, migrated.schemaVersion)
        assertTrue(migrated.protocols.single().frequency.isAsNeeded)
        assertEquals("completion-1", migrated.protocolCompletions.single().id)
        assertEquals(null, migrated.protocolCompletions.single().notes)
        assertEquals(FrequencyKind.TIMES_PER_DAY, migrated.supplements.single().frequency.kind)
        assertEquals(3, migrated.supplements.single().frequency.effectiveTimesPerDay)
        assertEquals("intake-1", migrated.supplementIntakes.single().id)
        assertEquals(null, migrated.supplementIntakes.single().dose)
        assertEquals(10, migrated.metrics.size)
    }

    @Test
    fun schemasOneThroughThreeAreAcceptedAndMigrationIsIdempotent() {
        (1..3).forEach { version ->
            val migrated = SnapshotMigration.decode("""{"schemaVersion":$version}""", json)
            assertEquals(4, migrated.schemaVersion)
            assertEquals(migrated, SnapshotMigration.migrate(migrated))
        }
        assertEquals(4, SnapshotMigration.decode("{}", json).schemaVersion)
        assertThrows(UnsupportedSnapshotVersionException::class.java) {
            SnapshotMigration.decode("""{"schemaVersion":5}""", json)
        }
    }

    @Test
    fun schemaV4RoundTripsAllNewLogAndReminderFields() {
        val snapshot = SnapshotMigration.migrate(
            AppSnapshot(
                protocols = listOf(
                    ProtocolItem(
                        id = "p",
                        name = "Routine",
                        customReminder = ItemReminderConfig(
                            title = "Routine",
                            hour = 7,
                            minute = 30,
                            weekdays = listOf(1, 3, 5)
                        )
                    )
                ),
                protocolCompletions = listOf(
                    ProtocolCompletion(
                        id = "pc",
                        protocolId = "p",
                        date = 100,
                        completed = false,
                        notes = "Décalé",
                        goal = "Objectif",
                        intervention = "Intervention"
                    )
                ),
                supplements = listOf(Supplement(id = "s", name = "Suivi", dose = "personnalisée")),
                supplementIntakes = listOf(
                    SupplementIntake(
                        id = "si",
                        supplementId = "s",
                        date = 200,
                        dose = "dose saisie",
                        brand = "marque saisie",
                        notes = "note"
                    )
                ),
                reminders = listOf(
                    Reminder(
                        id = "r",
                        title = "Routine",
                        hour = 7,
                        minute = 30,
                        targetKind = ReminderTargetKind.PROTOCOL,
                        targetId = "p"
                    )
                )
            )
        )

        val decoded = SnapshotMigration.decode(json.encodeToString(snapshot), json)

        assertEquals(snapshot, decoded)
    }

    @Test
    fun asNeededAndFrequencyHelpersKeepPersistedSemantics() {
        val asNeeded = Frequency.weekly(emptyList())
        val specific = Frequency.weekly(listOf(5, 1, 5))
        val multiple = Frequency.timesPerDay(0)

        assertTrue(asNeeded.isAsNeeded)
        assertFalse(asNeeded.hasSpecificDays)
        assertEquals(1, asNeeded.effectiveTimesPerDay)
        assertFalse(specific.isAsNeeded)
        assertTrue(specific.hasSpecificDays)
        assertEquals(listOf(1, 5), specific.days)
        assertEquals(1, multiple.effectiveTimesPerDay)
    }

    @Test
    fun builtInCatalogsAreExactFilterableAndDeduplicated() {
        assertEquals(
            listOf(
                "Pause respiratoire calme",
                "Marche en extérieur",
                "Bloc de concentration",
                "Journal de gratitude",
                "Préparation du coucher"
            ),
            ProtocolCatalog.builtIns.map { it.name }
        )
        assertEquals(
            listOf(
                "Vitamine D3",
                "Vitamine B12",
                "Vitamine C",
                "Magnésium glycinate",
                "Zinc",
                "Oméga-3",
                "Créatine",
                "L-théanine + caféine",
                "Rhodiola",
                "Bacopa",
                "Mélatonine"
            ),
            SupplementCatalog.builtIns.map { it.name }
        )
        assertEquals(5, ProtocolCatalog.builtIns.map { normalizedCatalogKey(it.name) }.distinct().size)
        assertEquals(11, SupplementCatalog.builtIns.map { normalizedCatalogKey(it.name) }.distinct().size)
        assertEquals(listOf("Préparation du coucher"), ProtocolCatalog.available(query = "coucher").map { it.name })
        assertEquals(listOf("Magnésium glycinate"), SupplementCatalog.available(query = "magnesium").map { it.name })
        assertEquals(2, ProtocolCatalog.available(category = "bien etre").size)
        assertEquals(3, SupplementCatalog.available(category = "nootropiques").size)
    }

    @Test
    fun customCatalogEntriesAreAvailableWithoutDuplicatingBuiltIns() {
        val protocols = ProtocolCatalog.available(
            listOf(
                CustomProtocolTemplate(id = "duplicate", name = "pause respiratoire calme"),
                CustomProtocolTemplate(id = "custom", name = "Ma routine")
            )
        )
        val supplements = SupplementCatalog.available(
            listOf(
                CustomSupplementTemplate(id = "duplicate", name = "VITAMINE D3"),
                CustomSupplementTemplate(id = "custom", name = "Mon suivi")
            )
        )

        assertEquals(6, protocols.size)
        assertEquals(1, protocols.count { normalizedCatalogKey(it.name) == "pause respiratoire calme" })
        assertTrue(protocols.single { it.name == "Ma routine" }.isCustom)
        assertEquals(12, supplements.size)
        assertEquals(1, supplements.count { normalizedCatalogKey(it.name) == "vitamine d3" })
        assertTrue(supplements.single { it.name == "Mon suivi" }.isCustom)
    }

    @Test
    fun catalogItemsCanBeCreatedEditedAndPersistedWithFullFields() {
        val createdProtocol = ProtocolCatalog.builtIns.first().toProtocolItem().copy(
            id = "created-protocol",
            detail = "Détail personnalisé",
            goal = "Observer la régularité",
            intervention = "Tester un créneau",
            notes = "Note locale",
            frequency = Frequency.timesPerDay(2),
            customReminder = ItemReminderConfig(hour = 9, minute = 15)
        )
        val editedSupplement = SupplementCatalog.builtIns.first().toSupplement().copy(
            id = "created-supplement",
            brand = "Marque saisie",
            dose = "Dose saisie",
            timeContext = "Avec un repas",
            frequency = Frequency.weekly(listOf(2, 4)),
            active = false,
            durationNote = "30 jours",
            notes = "Note locale"
        )
        val snapshot = AppSnapshot(protocols = listOf(createdProtocol), supplements = listOf(editedSupplement))

        val persisted = ExchangeFormat.decode(ExchangeFormat.encode(snapshot))

        assertEquals(createdProtocol, persisted.protocols.single())
        assertEquals(editedSupplement, persisted.supplements.single())
        assertEquals(2, persisted.protocols.single().frequency.effectiveTimesPerDay)
        assertEquals(listOf(2, 4), persisted.supplements.single().frequency.days)
    }

    @Test
    fun metricPresetsUseRmssdAndDeduplicateNormalizedExistingMetrics() {
        val snapshot = AppSnapshot(
            metrics = listOf(
                Metric(id = "sleep-1", name = "Sommeil", kind = MetricKind.HOURS_MINUTES, unit = "h"),
                Metric(id = "sleep-2", name = " durée  du SOMMEIL ", kind = MetricKind.HOURS_MINUTES, unit = "h"),
                Metric(id = "mood-1", name = "Humeur", unit = "1-10"),
                Metric(id = "mood-2", name = "humeur", unit = "1-10")
            ),
            metricEntries = listOf(MetricEntry(id = "entry", metricId = "sleep-2", date = 1, value = 8.0))
        )

        val ensured = MetricPresets.ensureIn(snapshot)

        assertEquals(10, ensured.metrics.size)
        assertEquals(1, ensured.metrics.count { it.kind == MetricKind.HOURS_MINUTES && normalizedCatalogKey(it.name).contains("sommeil") })
        assertEquals("sleep-1", ensured.metricEntries.single().metricId)
        assertTrue(ensured.metrics.any { it.name == "HRV (RMSSD)" })
        assertFalse(ensured.metrics.any { it.name.contains("SDNN") })
        assertEquals(ensured, MetricPresets.ensureIn(ensured))
    }

    @Test
    fun exchangeEnvelopeRoundTripsAndRejectsUnknownVersion() {
        val snapshot = AppSnapshot(
            protocols = listOf(ProtocolItem(id = "p", name = "Libre", frequency = Frequency.weekly(emptyList())))
        )
        val encoded = ExchangeFormat.encode(snapshot)

        assertEquals(ExchangePayloadKind.ANDROID_EXCHANGE, ExchangeFormat.detect(encoded))
        assertEquals(SnapshotMigration.migrate(snapshot), ExchangeFormat.decode(encoded))

        val unknownVersion = encoded.replace("\"formatVersion\": 1", "\"formatVersion\": 99")
        assertThrows(UnsupportedExchangeFormatException::class.java) {
            ExchangeFormat.decode(unknownVersion)
        }
    }

    @Test
    fun malformedImportIsRejectedBeforeAnySnapshotReplacement() {
        assertEquals(ExchangePayloadKind.UNKNOWN, ExchangeFormat.detect("{not-json"))
        assertThrows(UnsupportedExchangeFormatException::class.java) {
            ExchangeFormat.decode("{not-json")
        }
    }

    @Test
    fun iosCodableFrequencyIsDetectedButNotClaimedAsCompatible() {
        val iosLike = """
            {
              "schemaVersion": 3,
              "protocols": [{
                "id": "p", "name": "Libre", "frequency": {"weekly": {"days": []}}
              }]
            }
        """.trimIndent()

        assertEquals(ExchangePayloadKind.IOS_CODABLE_SNAPSHOT_UNSUPPORTED, ExchangeFormat.detect(iosLike))
        assertThrows(UnsupportedExchangeFormatException::class.java) {
            ExchangeFormat.decode(iosLike)
        }
    }
}
