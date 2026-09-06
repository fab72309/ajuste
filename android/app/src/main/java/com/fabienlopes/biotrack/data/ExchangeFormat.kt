package com.fabienlopes.biotrack.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class AndroidExchangeEnvelope(
    val format: String = ExchangeFormat.FORMAT_ID,
    val formatVersion: Int = ExchangeFormat.CURRENT_FORMAT_VERSION,
    val platform: String = "android",
    val snapshot: AppSnapshot
)

enum class ExchangePayloadKind {
    ANDROID_EXCHANGE,
    LEGACY_ANDROID_SNAPSHOT,
    IOS_CODABLE_SNAPSHOT_UNSUPPORTED,
    UNKNOWN
}

class UnsupportedExchangeFormatException(message: String) : IllegalArgumentException(message)

/**
 * Stable Android exchange envelope. Legacy plain Android snapshots are adapted explicitly.
 * Swift's synthesized Codable representation for associated-value enums is only detected and
 * rejected: Android does not claim to decode it without a proven shared cross-platform schema.
 */
object ExchangeFormat {
    const val FORMAT_ID = "ajuste.android.exchange"
    const val CURRENT_FORMAT_VERSION = 1

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun encode(snapshot: AppSnapshot): String = json.encodeToString(
        AndroidExchangeEnvelope(snapshot = SnapshotMigration.migrate(snapshot))
    )

    fun decode(raw: String): AppSnapshot = when (detect(raw)) {
        ExchangePayloadKind.ANDROID_EXCHANGE -> decodeEnvelope(raw)
        ExchangePayloadKind.LEGACY_ANDROID_SNAPSHOT -> SnapshotMigration.decode(raw, json)
        ExchangePayloadKind.IOS_CODABLE_SNAPSHOT_UNSUPPORTED -> throw UnsupportedExchangeFormatException(
            "Ce snapshot semble utiliser les enums Codable iOS. Aucun adaptateur iOS vérifié n’est disponible."
        )
        ExchangePayloadKind.UNKNOWN -> throw UnsupportedExchangeFormatException(
            "Format d’échange AJUSTE inconnu."
        )
    }

    fun detect(raw: String): ExchangePayloadKind {
        val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull()
            ?: return ExchangePayloadKind.UNKNOWN
        if (root["format"]?.jsonPrimitive?.content == FORMAT_ID) {
            return ExchangePayloadKind.ANDROID_EXCHANGE
        }
        if (hasIosCodableFrequency(root)) return ExchangePayloadKind.IOS_CODABLE_SNAPSHOT_UNSUPPORTED
        if (root.containsKey("schemaVersion") && root.keys.any { it in androidSnapshotKeys }) {
            return ExchangePayloadKind.LEGACY_ANDROID_SNAPSHOT
        }
        return ExchangePayloadKind.UNKNOWN
    }

    private fun decodeEnvelope(raw: String): AppSnapshot {
        val envelope = json.decodeFromString<AndroidExchangeEnvelope>(raw)
        if (envelope.format != FORMAT_ID) {
            throw UnsupportedExchangeFormatException("Identifiant de format d’échange AJUSTE inconnu.")
        }
        if (envelope.formatVersion != CURRENT_FORMAT_VERSION) {
            throw UnsupportedExchangeFormatException(
                "Version de format d’échange AJUSTE inconnue: ${envelope.formatVersion}"
            )
        }
        if (envelope.platform != "android") {
            throw UnsupportedExchangeFormatException("Plateforme d’échange non prise en charge: ${envelope.platform}")
        }
        return SnapshotMigration.migrate(envelope.snapshot)
    }

    private fun hasIosCodableFrequency(root: JsonObject): Boolean {
        val collectionKeys = listOf("protocols", "supplements", "customProtocolTemplates", "customSupplementTemplates")
        return collectionKeys.any { key ->
            val items = runCatching { root[key]?.jsonArray }.getOrNull() ?: return@any false
            items.any { element ->
                val frequency = runCatching { element.jsonObject["frequency"]?.jsonObject }.getOrNull()
                    ?: return@any false
                "kind" !in frequency && frequency.keys.any { it in iosFrequencyCaseKeys }
            }
        }
    }

    private val iosFrequencyCaseKeys = setOf("daily", "weekly", "timesPerDay")
    private val androidSnapshotKeys = setOf(
        "protocols",
        "supplements",
        "metrics",
        "reminders",
        "dailyCheckIns",
        "routineProfiles"
    )
}
