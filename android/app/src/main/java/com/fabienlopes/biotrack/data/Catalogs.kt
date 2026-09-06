package com.fabienlopes.biotrack.data

import java.text.Normalizer
import java.util.Locale

data class ProtocolCatalogEntry(
    val id: String,
    val name: String,
    val goal: String,
    val intervention: String,
    val category: String,
    val minutes: Int,
    val frequency: Frequency,
    val hour: Int,
    val minute: Int,
    val isCustom: Boolean = false,
    val customReminder: ItemReminderConfig? = null
) {
    fun toProtocolItem(): ProtocolItem = ProtocolItem(
        name = name,
        detail = goal,
        goal = goal,
        intervention = intervention,
        frequency = frequency,
        preferredHour = hour,
        preferredMinute = minute,
        targetMinutes = minutes,
        notes = intervention,
        category = category,
        customReminder = customReminder
    )
}

object ProtocolCatalog {
    /** Built-in choices are catalog data only; callers decide if and when to persist a selection. */
    val builtIns: List<ProtocolCatalogEntry> = listOf(
        ProtocolCatalogEntry(
            id = "pause-respiratoire-calme",
            name = "Pause respiratoire calme",
            goal = "Créer un moment de pause",
            intervention = "Respirer lentement à votre rythme. Arrêter en cas d’inconfort.",
            category = "Bien-être",
            minutes = 3,
            frequency = Frequency.daily(),
            hour = 10,
            minute = 0
        ),
        ProtocolCatalogEntry(
            id = "marche-exterieur",
            name = "Marche en extérieur",
            goal = "Prévoir un temps de mouvement",
            intervention = "Marcher à une allure confortable et adaptée à votre situation.",
            category = "Mouvement",
            minutes = 20,
            frequency = Frequency.daily(),
            hour = 12,
            minute = 0
        ),
        ProtocolCatalogEntry(
            id = "bloc-concentration",
            name = "Bloc de concentration",
            goal = "Réserver un temps sans interruption",
            intervention = "Choisir une tâche, couper les distractions puis faire une pause.",
            category = "Cognition",
            minutes = 25,
            frequency = Frequency.daily(),
            hour = 10,
            minute = 0
        ),
        ProtocolCatalogEntry(
            id = "journal-gratitude",
            name = "Journal de gratitude",
            goal = "Prendre du recul sur la journée",
            intervention = "Noter librement quelques éléments positifs ou importants.",
            category = "Bien-être",
            minutes = 5,
            frequency = Frequency.daily(),
            hour = 21,
            minute = 0
        ),
        ProtocolCatalogEntry(
            id = "preparation-coucher",
            name = "Préparation du coucher",
            goal = "Structurer la fin de journée",
            intervention = "Préparer le lendemain et choisir une heure de déconnexion.",
            category = "Sommeil",
            minutes = 10,
            frequency = Frequency.daily(),
            hour = 22,
            minute = 0
        )
    )

    fun available(
        customTemplates: List<CustomProtocolTemplate> = emptyList(),
        query: String = "",
        category: String? = null
    ): List<ProtocolCatalogEntry> {
        val custom = customTemplates.map { template ->
            val fallback = template.detail.orEmpty().ifBlank { "Modèle personnalisé" }
            ProtocolCatalogEntry(
                id = "custom-${template.id}",
                name = template.name,
                goal = template.goal.orEmpty().ifBlank { fallback },
                intervention = template.intervention.orEmpty().ifBlank { fallback },
                category = template.category,
                minutes = template.minutes.coerceAtLeast(1),
                frequency = template.frequency,
                hour = template.hour.coerceIn(0, 23),
                minute = template.minute.coerceIn(0, 59),
                isCustom = true,
                customReminder = template.customReminder
            )
        }
        return filterProtocolEntries(deduplicateByName(builtIns + custom), query, category)
    }
}

data class SupplementCatalogEntry(
    val id: String,
    val name: String,
    val categories: List<String>,
    val brand: String? = null,
    val dose: String? = null,
    val timeContext: String? = null,
    val frequency: Frequency = Frequency.daily(),
    val timeOfDay: Int? = null,
    val timesPerDay: Int? = null,
    val daysOfWeek: List<Int>? = null,
    val durationNote: String? = null,
    val notes: String? = null,
    val isCustom: Boolean = false,
    val customReminder: ItemReminderConfig? = null
) {
    fun toSupplement(): Supplement = Supplement(
        name = name,
        brand = brand,
        dose = dose,
        category = categories.firstOrNull() ?: "Autre",
        timeOfDay = timeOfDay,
        timeContext = timeContext,
        frequency = frequency,
        timesPerDay = timesPerDay,
        daysOfWeek = daysOfWeek,
        durationNote = durationNote,
        notes = notes,
        customReminder = customReminder
    )
}

object SupplementCatalog {
    /** Names intentionally follow the product brief; no dose or product recommendation is embedded. */
    val builtIns: List<SupplementCatalogEntry> = listOf(
        SupplementCatalogEntry("vitamine-d3", "Vitamine D3", listOf("Vitamines")),
        SupplementCatalogEntry("vitamine-b12", "Vitamine B12", listOf("Vitamines")),
        SupplementCatalogEntry("vitamine-c", "Vitamine C", listOf("Vitamines")),
        SupplementCatalogEntry("magnesium-glycinate", "Magnésium glycinate", listOf("Minéraux")),
        SupplementCatalogEntry("zinc", "Zinc", listOf("Minéraux")),
        SupplementCatalogEntry("omega-3", "Oméga-3", listOf("Énergie")),
        SupplementCatalogEntry("creatine", "Créatine", listOf("Énergie")),
        SupplementCatalogEntry("theanine-cafeine", "L-théanine + caféine", listOf("Nootropiques")),
        SupplementCatalogEntry("rhodiola", "Rhodiola", listOf("Nootropiques")),
        SupplementCatalogEntry("bacopa", "Bacopa", listOf("Nootropiques")),
        SupplementCatalogEntry("melatonine", "Mélatonine", listOf("Sommeil"))
    )

    fun available(
        customTemplates: List<CustomSupplementTemplate> = emptyList(),
        query: String = "",
        category: String? = null
    ): List<SupplementCatalogEntry> {
        val custom = customTemplates.map { template ->
            SupplementCatalogEntry(
                id = "custom-${template.id}",
                name = template.name,
                categories = listOf(template.category),
                brand = template.brand,
                dose = template.dose,
                timeContext = template.timeContext,
                frequency = template.frequency,
                timeOfDay = template.timeOfDay,
                timesPerDay = template.timesPerDay,
                daysOfWeek = template.daysOfWeek,
                durationNote = template.durationNote,
                notes = template.notes,
                isCustom = true,
                customReminder = template.customReminder
            )
        }
        return filterSupplementEntries(deduplicateByName(builtIns + custom), query, category)
    }
}

object MetricPresets {
    val all: List<Metric> = listOf(
        Metric(id = "preset-sleep-duration", name = "Durée du sommeil", kind = MetricKind.HOURS_MINUTES, unit = "h", description = "Heures de sommeil par nuit"),
        Metric(id = "preset-sleep-quality", name = "Qualité du sommeil", unit = "1-10"),
        Metric(id = "preset-mood", name = "Humeur", unit = "1-10"),
        Metric(id = "preset-energy", name = "Énergie", unit = "1-10"),
        Metric(id = "preset-focus", name = "Concentration", unit = "1-10"),
        Metric(id = "preset-weight", name = "Poids", unit = "kg"),
        Metric(id = "preset-steps", name = "Pas", unit = "pas"),
        Metric(id = "preset-resting-heart-rate", name = "FC au repos", unit = "bpm"),
        // Health Connect exposes RMSSD here; it must not be labelled as iOS HealthKit SDNN.
        Metric(id = "preset-hrv-rmssd", name = "HRV (RMSSD)", unit = "ms"),
        Metric(id = "preset-stress", name = "Stress", unit = "1-10")
    )

    fun ensureIn(metrics: List<Metric>): List<Metric> {
        val keys = mutableSetOf<String>()
        val result = metrics.filter { keys.add(metricPresetKey(it)) }.toMutableList()
        all.forEach { preset ->
            if (keys.add(metricPresetKey(preset))) result += preset
        }
        return result
    }

    fun ensureIn(snapshot: AppSnapshot): AppSnapshot {
        val canonicalIdByKey = mutableMapOf<String, String>()
        val canonicalIdByOriginalId = mutableMapOf<String, String>()
        val deduplicated = mutableListOf<Metric>()

        snapshot.metrics.forEach { metric ->
            val key = metricPresetKey(metric)
            val canonicalId = canonicalIdByKey.getOrPut(key) {
                deduplicated += metric
                metric.id
            }
            canonicalIdByOriginalId[metric.id] = canonicalId
        }
        all.forEach { preset ->
            val key = metricPresetKey(preset)
            if (canonicalIdByKey.putIfAbsent(key, preset.id) == null) deduplicated += preset
        }

        val entries = snapshot.metricEntries.map { entry ->
            val canonicalId = canonicalIdByOriginalId[entry.metricId]
            if (canonicalId == null || canonicalId == entry.metricId) entry else entry.copy(metricId = canonicalId)
        }
        return snapshot.copy(metrics = deduplicated, metricEntries = entries)
    }

    private fun metricPresetKey(metric: Metric): String {
        val normalizedName = normalizedCatalogKey(metric.name).replace(" ", "")
        if (metric.kind == MetricKind.HOURS_MINUTES && normalizedName.contains("sommeil")) {
            return "preset:hours:sommeil"
        }
        val normalized = "${metric.kind}:$normalizedName"
        return if (normalized in normalizedPresetKeys) "preset:$normalized" else "custom:${metric.id}"
    }

    private val normalizedPresetKeys: Set<String> by lazy {
        all.mapTo(mutableSetOf()) { preset ->
            "${preset.kind}:${normalizedCatalogKey(preset.name).replace(" ", "")}"
        }
    }
}

fun filterProtocolEntries(
    entries: List<ProtocolCatalogEntry>,
    query: String = "",
    category: String? = null
): List<ProtocolCatalogEntry> {
    val normalizedQuery = normalizedCatalogKey(query)
    val normalizedCategory = category?.let(::normalizedCatalogKey)?.takeIf { it.isNotEmpty() }
    return entries.filter { entry ->
        (normalizedCategory == null || normalizedCatalogKey(entry.category) == normalizedCategory) &&
            (normalizedQuery.isEmpty() || listOf(entry.name, entry.goal, entry.intervention, entry.category)
                .any { normalizedCatalogKey(it).contains(normalizedQuery) })
    }
}

fun filterSupplementEntries(
    entries: List<SupplementCatalogEntry>,
    query: String = "",
    category: String? = null
): List<SupplementCatalogEntry> {
    val normalizedQuery = normalizedCatalogKey(query)
    val normalizedCategory = category?.let(::normalizedCatalogKey)?.takeIf { it.isNotEmpty() }
    return entries.filter { entry ->
        (normalizedCategory == null || entry.categories.any { normalizedCatalogKey(it) == normalizedCategory }) &&
            (normalizedQuery.isEmpty() || (listOf(entry.name) + entry.categories)
                .any { normalizedCatalogKey(it).contains(normalizedQuery) })
    }
}

fun normalizedCatalogKey(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "")
    .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
    .trim()
    .replace(Regex("\\s+"), " ")
    .lowercase(Locale.ROOT)

private fun <T> deduplicateByName(entries: List<T>): List<T> {
    val seen = mutableSetOf<String>()
    return entries.filter { entry ->
        val name = when (entry) {
            is ProtocolCatalogEntry -> entry.name
            is SupplementCatalogEntry -> entry.name
            else -> error("Unsupported catalog entry type")
        }
        seen.add(normalizedCatalogKey(name))
    }
}
