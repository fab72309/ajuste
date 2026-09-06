package com.fabienlopes.biotrack.data

/** Pure snapshot mutations shared by the ViewModel and deterministic JVM tests. */
object SnapshotEdits {
    fun addProtocol(snapshot: AppSnapshot, item: ProtocolItem, reminders: List<Reminder> = snapshot.reminders): AppSnapshot =
        snapshot.copy(protocols = snapshot.protocols + item, reminders = reminders)

    fun updateProtocol(snapshot: AppSnapshot, item: ProtocolItem, reminders: List<Reminder> = snapshot.reminders): AppSnapshot =
        snapshot.copy(
            protocols = snapshot.protocols.map { existing -> if (existing.id == item.id) item else existing },
            reminders = reminders
        )

    fun deleteProtocol(snapshot: AppSnapshot, id: String): AppSnapshot = snapshot.copy(
        protocols = snapshot.protocols.filterNot { it.id == id },
        protocolCompletions = snapshot.protocolCompletions.filterNot { it.protocolId == id },
        reminders = snapshot.reminders.filterNot { it.targetKind == ReminderTargetKind.PROTOCOL && it.targetId == id }
    )

    fun addSupplement(snapshot: AppSnapshot, item: Supplement, reminders: List<Reminder> = snapshot.reminders): AppSnapshot =
        snapshot.copy(supplements = snapshot.supplements + item, reminders = reminders)

    fun updateSupplement(snapshot: AppSnapshot, item: Supplement, reminders: List<Reminder> = snapshot.reminders): AppSnapshot =
        snapshot.copy(
            supplements = snapshot.supplements.map { existing -> if (existing.id == item.id) item else existing },
            reminders = reminders
        )

    fun deleteSupplement(snapshot: AppSnapshot, id: String): AppSnapshot = snapshot.copy(
        supplements = snapshot.supplements.filterNot { it.id == id },
        supplementIntakes = snapshot.supplementIntakes.filterNot { it.supplementId == id },
        reminders = snapshot.reminders.filterNot { it.targetKind == ReminderTargetKind.SUPPLEMENT && it.targetId == id }
    )

    fun upsertReminder(snapshot: AppSnapshot, reminder: Reminder): AppSnapshot {
        val exists = snapshot.reminders.any { it.id == reminder.id }
        val reminders = if (exists) snapshot.reminders.map { if (it.id == reminder.id) reminder else it }
        else snapshot.reminders + reminder
        return snapshot.copy(reminders = reminders)
    }

    fun deleteReminder(snapshot: AppSnapshot, id: String): AppSnapshot =
        snapshot.copy(reminders = snapshot.reminders.filterNot { it.id == id })

    fun setProfileItemEnabled(
        snapshot: AppSnapshot,
        kind: RoutineProfileKind,
        target: ReminderTargetKind,
        id: String,
        enabled: Boolean
    ): AppSnapshot = snapshot.copy(routineProfiles = snapshot.routineProfiles.map { profile ->
        if (profile.kind != kind) profile else when (target) {
            ReminderTargetKind.PROTOCOL -> profile.copy(disabledProtocolIds = profile.disabledProtocolIds.withEnabled(id, enabled))
            ReminderTargetKind.SUPPLEMENT -> profile.copy(disabledSupplementIds = profile.disabledSupplementIds.withEnabled(id, enabled))
        }
    })

    fun setProfileReminderEnabled(
        snapshot: AppSnapshot,
        kind: RoutineProfileKind,
        id: String,
        enabled: Boolean
    ): AppSnapshot = snapshot.copy(routineProfiles = snapshot.routineProfiles.map { profile ->
        if (profile.kind == kind) profile.copy(disabledReminderIds = profile.disabledReminderIds.withEnabled(id, enabled)) else profile
    })

    fun resetProfile(snapshot: AppSnapshot, kind: RoutineProfileKind): AppSnapshot =
        snapshot.copy(routineProfiles = snapshot.routineProfiles.map { profile ->
            if (profile.kind == kind) profile.copy(
                disabledProtocolIds = emptyList(),
                disabledSupplementIds = emptyList(),
                disabledReminderIds = emptyList()
            ) else profile
        })

    private fun List<String>.withEnabled(id: String, enabled: Boolean): List<String> =
        if (enabled) filterNot { it == id } else (this + id).distinct()
}
