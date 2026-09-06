package com.fabienlopes.biotrack.data

import android.app.Application
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import com.fabienlopes.biotrack.R
import com.fabienlopes.biotrack.domain.Planner
import com.fabienlopes.biotrack.domain.Statistics
import com.fabienlopes.biotrack.notifications.ReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

enum class HealthConnectionStatus { NOT_AVAILABLE, NOT_CONNECTED, CONNECTED, DENIED }

class BioTrackViewModel(application: Application) : AndroidViewModel(application) {
    private val store = LocalStore(application)
    private val preferences = application.getSharedPreferences("biotrack-preferences", 0)
    private val saveQueue = Channel<AppSnapshot>(Channel.UNLIMITED)
    private val persistenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _snapshot = MutableStateFlow(loadInitialSnapshot())
    val snapshot: StateFlow<AppSnapshot> = _snapshot.asStateFlow()

    private val _selectedCheckInMetricIds = MutableStateFlow(loadCheckInMetricSelection())
    val selectedCheckInMetricIds: StateFlow<List<String>> = _selectedCheckInMetricIds.asStateFlow()

    var onboardingComplete = MutableStateFlow(preferences.getBoolean("onboardingComplete", false))
        private set
    var darkMode = MutableStateFlow(preferences.getBoolean("darkMode", false))
        private set
    var showRecommendations = MutableStateFlow(preferences.getBoolean("showRecommendations", true))
        private set
    var healthStatus = MutableStateFlow(HealthConnectionStatus.NOT_AVAILABLE)
        private set
    var healthSyncing = MutableStateFlow(false)
        private set
    var healthSyncDays = MutableStateFlow(preferences.getInt("healthSyncDays", 30).coerceIn(1, 365))
        private set
    var lastMessage = MutableStateFlow<String?>(null)
        private set

    init {
        Planner.currentSnapshot = _snapshot.value
        persistenceScope.launch {
            for (snapshotToSave in saveQueue) {
                runCatching { store.save(snapshotToSave) }
                    .onFailure { lastMessage.value = getApplication<Application>().getString(R.string.local_save_failed) }
            }
        }
    }

    fun completeOnboarding() {
        preferences.edit { putBoolean("onboardingComplete", true) }
        onboardingComplete.value = true
    }

    fun reviewOnboarding() {
        preferences.edit { putBoolean("onboardingComplete", false) }
        onboardingComplete.value = false
    }

    fun setDarkMode(enabled: Boolean) {
        preferences.edit { putBoolean("darkMode", enabled) }
        darkMode.value = enabled
    }

    fun setShowRecommendations(enabled: Boolean) {
        preferences.edit { putBoolean("showRecommendations", enabled) }
        showRecommendations.value = enabled
    }

    fun clearMessage() {
        lastMessage.value = null
    }

    fun toggleProtocol(protocolId: String, now: Long = System.currentTimeMillis()) {
        toggleProtocolOccurrence(protocolId, 0, now)
    }

    fun toggleProtocolOccurrence(protocolId: String, occurrenceIndex: Int, now: Long = System.currentTimeMillis()) {
        val current = _snapshot.value
        val sameDay = current.protocolCompletions
            .filter { it.protocolId == protocolId && Planner.sameDay(it.date, now) }
            .sortedBy { it.date }
        val safeIndex = occurrenceIndex.coerceAtLeast(0)
        val existing = sameDay.firstOrNull { it.occurrenceIndex == safeIndex }
            ?: legacyOccurrence(sameDay, safeIndex) { it.occurrenceIndex }
        val completions = if (existing != null) current.protocolCompletions.filterNot { it.id == existing.id } else {
            current.protocolCompletions + ProtocolCompletion(protocolId = protocolId, date = now, completed = true, occurrenceIndex = safeIndex)
        }
        commit(current.copy(protocolCompletions = completions))
    }

    fun toggleSupplement(supplementId: String, now: Long = System.currentTimeMillis()) {
        toggleSupplementOccurrence(supplementId, 0, now)
    }

    fun toggleSupplementOccurrence(supplementId: String, occurrenceIndex: Int, now: Long = System.currentTimeMillis()) {
        val current = _snapshot.value
        val sameDay = current.supplementIntakes
            .filter { it.supplementId == supplementId && Planner.sameDay(it.date, now) }
            .sortedBy { it.date }
        val safeIndex = occurrenceIndex.coerceAtLeast(0)
        val existing = sameDay.firstOrNull { it.occurrenceIndex == safeIndex }
            ?: legacyOccurrence(sameDay, safeIndex) { it.occurrenceIndex }
        val supplement = current.supplements.firstOrNull { it.id == supplementId }
        val intakes = if (existing != null) current.supplementIntakes.filterNot { it.id == existing.id } else {
            current.supplementIntakes + SupplementIntake(
                supplementId = supplementId,
                date = now,
                taken = true,
                occurrenceIndex = safeIndex,
                dose = supplement?.dose,
                brand = supplement?.brand
            )
        }
        commit(current.copy(supplementIntakes = intakes))
    }

    fun upsertProtocolCompletion(completion: ProtocolCompletion) {
        val current = _snapshot.value
        val exists = current.protocolCompletions.any { it.id == completion.id }
        val values = if (exists) current.protocolCompletions.map { if (it.id == completion.id) completion else it }
        else current.protocolCompletions + completion
        commit(current.copy(protocolCompletions = values))
    }

    fun deleteProtocolCompletion(id: String) {
        commit(_snapshot.value.copy(protocolCompletions = _snapshot.value.protocolCompletions.filterNot { it.id == id }))
    }

    fun upsertSupplementIntake(intake: SupplementIntake) {
        val current = _snapshot.value
        val exists = current.supplementIntakes.any { it.id == intake.id }
        val values = if (exists) current.supplementIntakes.map { if (it.id == intake.id) intake else it }
        else current.supplementIntakes + intake
        commit(current.copy(supplementIntakes = values))
    }

    fun deleteSupplementIntake(id: String) {
        commit(_snapshot.value.copy(supplementIntakes = _snapshot.value.supplementIntakes.filterNot { it.id == id }))
    }

    fun upsertCheckIn(
        period: CheckInPeriod,
        energy: Int,
        mood: Int,
        sleepQuality: Int? = null,
        stress: Int? = null,
        note: String? = null,
        now: Long = System.currentTimeMillis(),
        metricValues: Map<String, Double> = emptyMap()
    ) {
        commit(CheckInData.upsert(_snapshot.value, period, energy, mood, sleepQuality, stress, note, now, metricValues))
    }

    fun setCheckInMetricSelected(metricId: String, selected: Boolean) {
        val available = _snapshot.value.metrics.map { it.id }.toSet()
        if (metricId !in available) return
        val updated = if (selected) (_selectedCheckInMetricIds.value + metricId).distinct()
        else _selectedCheckInMetricIds.value.filterNot { it == metricId }
        persistCheckInMetricSelection(updated)
    }

    fun moveCheckInMetric(metricId: String, direction: Int) {
        val values = _selectedCheckInMetricIds.value.toMutableList()
        val from = values.indexOf(metricId)
        if (from < 0) return
        val to = (from + direction).coerceIn(0, values.lastIndex)
        if (from == to) return
        values.add(to, values.removeAt(from))
        persistCheckInMetricSelection(values)
    }

    fun resetCheckInMetricSelection() {
        persistCheckInMetricSelection(defaultCheckInMetricIds(_snapshot.value.metrics))
    }

    fun addMetric(name: String, kind: MetricKind, unit: String?) {
        if (name.isBlank()) return
        commit(_snapshot.value.copy(metrics = _snapshot.value.metrics + Metric(name = name.trim(), kind = kind, unit = unit?.trim()?.takeIf { it.isNotEmpty() })))
    }

    fun deleteMetric(id: String) {
        val current = _snapshot.value
        commit(current.copy(metrics = current.metrics.filterNot { it.id == id }, metricEntries = current.metricEntries.filterNot { it.metricId == id }))
    }

    fun addMetricEntry(metricId: String, value: Double, notes: String? = null, date: Long = System.currentTimeMillis()) {
        if (!value.isFinite()) return
        commit(_snapshot.value.copy(metricEntries = _snapshot.value.metricEntries + MetricEntry(metricId = metricId, value = value, notes = notes, date = date)))
    }

    fun updateMetricEntry(entry: MetricEntry) {
        if (!entry.value.isFinite()) return
        val current = _snapshot.value
        commit(current.copy(metricEntries = current.metricEntries.map { if (it.id == entry.id) entry else it }))
    }

    fun deleteMetricEntry(id: String) {
        commit(_snapshot.value.copy(metricEntries = _snapshot.value.metricEntries.filterNot { it.id == id }))
    }

    fun addProtocol(item: ProtocolItem) {
        if (item.name.isBlank()) return
        val value = item.copy(name = item.name.trim())
        val current = _snapshot.value
        val reminders = upsertTargetReminder(current.reminders, ReminderTargetKind.PROTOCOL, value.id, value.customReminder, value.name)
        commit(SnapshotEdits.addProtocol(current, value, reminders))
        syncScheduledTarget(ReminderTargetKind.PROTOCOL, value.id, current.reminders, reminders)
    }

    fun saveProtocolTemplate(item: ProtocolItem) {
        val normalizedName = normalizedName(item.name)
        if (normalizedName.isEmpty() || ProtocolCatalog.builtIns.any { normalizedName(it.name) == normalizedName }) return
        val current = _snapshot.value
        if (current.customProtocolTemplates.any { normalizedName(it.name) == normalizedName }) return
        val template = CustomProtocolTemplate(
            name = item.name.trim(),
            detail = item.detail,
            category = item.category ?: "Autre",
            minutes = item.targetMinutes ?: 10,
            frequency = item.frequency,
            hour = item.preferredHour ?: 8,
            minute = item.preferredMinute ?: 0,
            goal = item.goal,
            intervention = item.intervention,
            notes = item.notes,
            customReminder = item.customReminder
        )
        commit(current.copy(customProtocolTemplates = current.customProtocolTemplates + template))
    }

    fun updateProtocol(item: ProtocolItem) {
        val current = _snapshot.value
        val reminders = upsertTargetReminder(current.reminders, ReminderTargetKind.PROTOCOL, item.id, item.customReminder, item.name)
        commit(SnapshotEdits.updateProtocol(current, item, reminders))
        syncScheduledTarget(ReminderTargetKind.PROTOCOL, item.id, current.reminders, reminders)
    }

    fun deleteProtocol(id: String) {
        val current = _snapshot.value
        val removedReminders = current.reminders.filter { it.targetKind == ReminderTargetKind.PROTOCOL && it.targetId == id }
        commit(SnapshotEdits.deleteProtocol(current, id))
        removedReminders.forEach { ReminderScheduler.cancel(getApplication(), it) }
    }

    fun toggleProtocolActive(id: String) {
        val current = _snapshot.value
        commit(current.copy(protocols = current.protocols.map { if (it.id == id) it.copy(active = !it.active) else it }))
    }

    fun addSupplement(item: Supplement) {
        if (item.name.isBlank()) return
        val value = item.copy(name = item.name.trim())
        val current = _snapshot.value
        val reminders = upsertTargetReminder(current.reminders, ReminderTargetKind.SUPPLEMENT, value.id, value.customReminder, value.name)
        commit(SnapshotEdits.addSupplement(current, value, reminders))
        syncScheduledTarget(ReminderTargetKind.SUPPLEMENT, value.id, current.reminders, reminders)
    }

    fun saveSupplementTemplate(item: Supplement) {
        val normalizedName = normalizedName(item.name)
        if (normalizedName.isEmpty() || SupplementCatalog.builtIns.any { normalizedName(it.name) == normalizedName }) return
        val current = _snapshot.value
        if (current.customSupplementTemplates.any { normalizedName(it.name) == normalizedName }) return
        val template = CustomSupplementTemplate(
            name = item.name.trim(),
            brand = item.brand,
            dose = item.dose,
            category = item.category ?: "Autre",
            timeContext = item.timeContext,
            frequency = item.frequency,
            timeOfDay = item.timeOfDay,
            timesPerDay = item.timesPerDay,
            daysOfWeek = item.daysOfWeek,
            durationNote = item.durationNote,
            notes = item.notes,
            customReminder = item.customReminder
        )
        commit(current.copy(customSupplementTemplates = current.customSupplementTemplates + template))
    }

    fun updateSupplement(item: Supplement) {
        val current = _snapshot.value
        val reminders = upsertTargetReminder(current.reminders, ReminderTargetKind.SUPPLEMENT, item.id, item.customReminder, item.name)
        commit(SnapshotEdits.updateSupplement(current, item, reminders))
        syncScheduledTarget(ReminderTargetKind.SUPPLEMENT, item.id, current.reminders, reminders)
    }

    fun deleteSupplement(id: String) {
        val current = _snapshot.value
        val removedReminders = current.reminders.filter { it.targetKind == ReminderTargetKind.SUPPLEMENT && it.targetId == id }
        commit(SnapshotEdits.deleteSupplement(current, id))
        removedReminders.forEach { ReminderScheduler.cancel(getApplication(), it) }
    }

    fun toggleSupplementActive(id: String) {
        val current = _snapshot.value
        commit(current.copy(supplements = current.supplements.map { if (it.id == id) it.copy(active = !it.active) else it }))
    }

    fun addReminder(reminder: Reminder) {
        commit(SnapshotEdits.upsertReminder(_snapshot.value, reminder))
        ReminderScheduler.schedule(getApplication(), reminder)
    }

    fun updateReminder(reminder: Reminder) {
        val current = _snapshot.value
        current.reminders.firstOrNull { it.id == reminder.id }?.let { ReminderScheduler.cancel(getApplication(), it) }
        commit(SnapshotEdits.upsertReminder(current, reminder))
        if (reminder.enabled) ReminderScheduler.schedule(getApplication(), reminder)
    }

    fun deleteReminder(id: String) {
        val current = _snapshot.value
        current.reminders.firstOrNull { it.id == id }?.let { ReminderScheduler.cancel(getApplication(), it) }
        commit(SnapshotEdits.deleteReminder(current, id))
    }

    fun setReminderEnabled(id: String, enabled: Boolean) {
        val current = _snapshot.value
        val updated = current.reminders.map { if (it.id == id) it.copy(enabled = enabled) else it }
        commit(current.copy(reminders = updated))
        updated.firstOrNull { it.id == id }?.let { reminder ->
            if (enabled) ReminderScheduler.schedule(getApplication(), reminder) else ReminderScheduler.cancel(getApplication(), reminder)
        }
    }

    fun setRoutineProfile(kind: RoutineProfileKind) {
        commit(_snapshot.value.copy(activeRoutineProfileKindRaw = kind.name))
    }

    fun setProfileItemEnabled(kind: RoutineProfileKind, target: ReminderTargetKind, id: String, enabled: Boolean) {
        val current = _snapshot.value
        commit(SnapshotEdits.setProfileItemEnabled(current, kind, target, id, enabled))
    }

    fun setProfileReminderEnabled(kind: RoutineProfileKind, id: String, enabled: Boolean) {
        val current = _snapshot.value
        commit(SnapshotEdits.setProfileReminderEnabled(current, kind, id, enabled))
    }

    fun resetRoutineProfile(kind: RoutineProfileKind) {
        val current = _snapshot.value
        commit(SnapshotEdits.resetProfile(current, kind))
    }

    fun createExperiment(title: String, hypothesis: String, metricId: String, durationDays: Int, phaseDays: Int) {
        val experiment = NOf1Experiment(
            title = title.trim().ifBlank { "Expérience N-of-1" },
            hypothesis = hypothesis.trim(),
            targetMetricId = metricId,
            durationDays = durationDays.coerceAtLeast(7),
            phaseDurationDays = phaseDays.coerceAtLeast(3)
        )
        commit(_snapshot.value.copy(experiments = _snapshot.value.experiments + experiment))
    }

    fun recordObservation(experimentId: String, value: Double, notes: String? = null) {
        val experiment = _snapshot.value.experiments.firstOrNull { it.id == experimentId } ?: return
        val observation = NOf1Observation(experimentId = experimentId, phase = Statistics.experimentPhase(experiment), value = value, notes = notes)
        commit(_snapshot.value.copy(experimentObservations = _snapshot.value.experimentObservations + observation))
    }

    fun applyImportedSnapshot(imported: AppSnapshot) {
        val previousReminders = _snapshot.value.reminders
        val migrated = SnapshotMigration.migrate(imported)
        previousReminders.forEach { ReminderScheduler.cancel(getApplication(), it) }
        commit(migrated)
        migrated.reminders.filter { it.enabled }.forEach { ReminderScheduler.schedule(getApplication(), it) }
        lastMessage.value = "Sauvegarde importée."
    }

    fun decodeImport(raw: String): AppSnapshot = ExchangeFormat.decode(raw)

    fun exportSnapshot(): String = ExchangeFormat.encode(_snapshot.value)

    fun exportEncrypted(passphrase: CharArray): String = EncryptedBackup.encrypt(exportSnapshot(), passphrase)

    fun importEncrypted(raw: String, passphrase: CharArray) {
        applyImportedSnapshot(decodeEncryptedImport(raw, passphrase))
    }

    fun decodeEncryptedImport(raw: String, passphrase: CharArray): AppSnapshot =
        ExchangeFormat.decode(EncryptedBackup.decrypt(raw, passphrase))

    fun setHealthStatus(status: HealthConnectionStatus) {
        healthStatus.value = status
    }

    fun setHealthSyncing(syncing: Boolean) {
        healthSyncing.value = syncing
    }

    fun setHealthSyncDays(days: Int) {
        val value = days.coerceIn(1, 365)
        preferences.edit { putInt("healthSyncDays", value) }
        healthSyncDays.value = value
    }

    fun syncHealthValues(valuesByMetricName: Map<String, Map<Long, Double>>) {
        if (valuesByMetricName.isEmpty()) return
        commit(HealthImport.merge(_snapshot.value, valuesByMetricName))
    }

    fun refreshInsights() {
        commit(_snapshot.value)
    }

    fun reloadIfExternallyUpdated() {
        if (!preferences.getBoolean("snapshotUpdatedExternally", false)) return
        val reloaded = loadInitialSnapshot()
        _snapshot.value = reloaded
        Planner.currentSnapshot = reloaded
        _selectedCheckInMetricIds.value = CheckInData.sanitizeSelection(_selectedCheckInMetricIds.value, reloaded.metrics)
        preferences.edit { putBoolean("snapshotUpdatedExternally", false) }
    }

    private fun commit(next: AppSnapshot) {
        val migrated = SnapshotMigration.migrate(next)
        val derived = migrated.copy(
            correlationInsights = Statistics.generateInsights(migrated),
        ).let { withInsights ->
            withInsights.copy(recommendations = Statistics.recommendations(withInsights))
        }
        val normalized = normalize(derived)
        _snapshot.value = normalized
        Planner.currentSnapshot = normalized
        saveQueue.trySend(normalized)
    }

    private fun loadInitialSnapshot(): AppSnapshot {
        val loaded = normalize(store.load())
        val seeded = seed(loaded)
        val derived = seeded.copy(correlationInsights = Statistics.generateInsights(seeded)).let { it.copy(recommendations = Statistics.recommendations(it)) }
        return normalize(derived)
    }

    private fun normalize(snapshot: AppSnapshot): AppSnapshot {
        val profiles = if (snapshot.routineProfiles.isEmpty()) defaultRoutineProfiles() else snapshot.routineProfiles
        return SnapshotMigration.migrate(snapshot.copy(
            routineProfiles = profiles,
            activeRoutineProfileKindRaw = snapshot.activeRoutineProfileKindRaw.ifBlank { RoutineProfileKind.WEEKDAY.name },
            adaptiveGoalPolicy = snapshot.adaptiveGoalPolicy.copy(
                minDailyTarget = snapshot.adaptiveGoalPolicy.minDailyTarget.coerceAtLeast(1),
                maxDailyTarget = snapshot.adaptiveGoalPolicy.maxDailyTarget.coerceAtLeast(snapshot.adaptiveGoalPolicy.minDailyTarget.coerceAtLeast(1))
            )
        ))
    }

    private fun defaultRoutineProfiles(): List<RoutineProfile> = listOf(
        RoutineProfile(kind = RoutineProfileKind.WEEKDAY, name = "Semaine", weekdays = listOf(1, 2, 3, 4, 5)),
        RoutineProfile(kind = RoutineProfileKind.WEEKEND, name = "Weekend", weekdays = listOf(6, 7)),
        RoutineProfile(kind = RoutineProfileKind.TRAVEL, name = "Voyage", weekdays = (1..7).toList())
    )

    private fun seed(base: AppSnapshot): AppSnapshot {
        return MetricPresets.ensureIn(base.copy(
            routineProfiles = if (base.routineProfiles.isEmpty()) defaultRoutineProfiles() else base.routineProfiles
        ))
    }

    private fun normalizedName(value: String): String = normalizedCatalogKey(value)

    private fun <T> legacyOccurrence(values: List<T>, occurrenceIndex: Int, indexOf: (T) -> Int?): T? {
        val occupied = values.mapNotNull(indexOf).toSet()
        val legacyValues = values.filter { indexOf(it) == null }
        val legacyIndices = generateSequence(0) { it + 1 }
            .filterNot { it in occupied }
            .take(legacyValues.size)
            .toList()
        return legacyIndices.zip(legacyValues)
            .firstOrNull { it.first == occurrenceIndex }
            ?.second
    }

    private fun loadCheckInMetricSelection(): List<String> {
        val configured = preferences.getBoolean("checkInSelectionConfigured", false)
        if (!configured) return defaultCheckInMetricIds(_snapshot.value.metrics)
        return CheckInData.sanitizeSelection(preferences.getString("checkInSelectedMetricIds", "").orEmpty()
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }, _snapshot.value.metrics)
    }

    private fun persistCheckInMetricSelection(ids: List<String>) {
        val sanitized = CheckInData.sanitizeSelection(ids, _snapshot.value.metrics)
        preferences.edit {
            putBoolean("checkInSelectionConfigured", true)
            putString("checkInSelectedMetricIds", sanitized.joinToString(","))
        }
        _selectedCheckInMetricIds.value = sanitized
    }

    private fun defaultCheckInMetricIds(metrics: List<Metric>): List<String> {
        return CheckInData.defaultSelection(metrics)
    }

    private fun upsertTargetReminder(
        reminders: List<Reminder>,
        kind: ReminderTargetKind,
        targetId: String,
        config: ItemReminderConfig?,
        fallbackTitle: String
    ): List<Reminder> {
        val existing = reminders.firstOrNull { it.targetKind == kind && it.targetId == targetId }
        val withoutTarget = reminders.filterNot { it.targetKind == kind && it.targetId == targetId }
        if (config == null || !config.enabled) return withoutTarget
        val reminder = Reminder(
            id = existing?.id ?: java.util.UUID.randomUUID().toString(),
            notificationBaseId = existing?.notificationBaseId ?: "reminder-${kind.name.lowercase()}-$targetId",
            title = config.title?.trim()?.takeIf { it.isNotEmpty() } ?: fallbackTitle,
            hour = config.hour.coerceIn(0, 23),
            minute = config.minute.coerceIn(0, 59),
            weekdays = config.weekdays.filter { it in 1..7 }.distinct().sorted(),
            notes = config.notes?.trim()?.takeIf { it.isNotEmpty() },
            enabled = true,
            targetKind = kind,
            targetId = targetId
        )
        return withoutTarget + reminder
    }

    private fun syncScheduledTarget(
        kind: ReminderTargetKind,
        targetId: String,
        previous: List<Reminder>,
        updated: List<Reminder>
    ) {
        previous.filter { it.targetKind == kind && it.targetId == targetId }
            .forEach { ReminderScheduler.cancel(getApplication(), it) }
        updated.filter { it.targetKind == kind && it.targetId == targetId && it.enabled }
            .forEach { ReminderScheduler.schedule(getApplication(), it) }
    }

    override fun onCleared() {
        // Closing lets the dedicated IO consumer drain already-queued snapshots before ending.
        saveQueue.close()
        super.onCleared()
    }
}
