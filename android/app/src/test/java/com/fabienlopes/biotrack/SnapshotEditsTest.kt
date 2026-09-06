package com.fabienlopes.biotrack

import com.fabienlopes.biotrack.data.AppSnapshot
import com.fabienlopes.biotrack.data.ProtocolCompletion
import com.fabienlopes.biotrack.data.ProtocolItem
import com.fabienlopes.biotrack.data.Reminder
import com.fabienlopes.biotrack.data.ReminderTargetKind
import com.fabienlopes.biotrack.data.RoutineProfile
import com.fabienlopes.biotrack.data.RoutineProfileKind
import com.fabienlopes.biotrack.data.SnapshotEdits
import com.fabienlopes.biotrack.data.Supplement
import com.fabienlopes.biotrack.data.SupplementIntake
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotEditsTest {
    @Test
    fun protocolCreationEditionAndDeletionUseProductionMutation() {
        val created = ProtocolItem(id = "p", name = "Routine", goal = "Initial")
        val reminder = Reminder(id = "rp", title = "Routine", hour = 8, minute = 0, targetKind = ReminderTargetKind.PROTOCOL, targetId = "p")
        val afterCreate = SnapshotEdits.addProtocol(AppSnapshot(), created, listOf(reminder))
        val afterEdit = SnapshotEdits.updateProtocol(afterCreate, created.copy(name = "Routine corrigée", goal = "Corrigé"))
            .copy(protocolCompletions = listOf(ProtocolCompletion(id = "pc", protocolId = "p")))
        val afterDelete = SnapshotEdits.deleteProtocol(afterEdit, "p")

        assertEquals("Routine corrigée", afterEdit.protocols.single().name)
        assertEquals("Corrigé", afterEdit.protocols.single().goal)
        assertTrue(afterDelete.protocols.isEmpty())
        assertTrue(afterDelete.protocolCompletions.isEmpty())
        assertTrue(afterDelete.reminders.isEmpty())
    }

    @Test
    fun supplementCreationEditionAndDeletionUseProductionMutation() {
        val created = Supplement(id = "s", name = "Suivi", dose = "Initiale")
        val reminder = Reminder(id = "rs", title = "Suivi", hour = 8, minute = 0, targetKind = ReminderTargetKind.SUPPLEMENT, targetId = "s")
        val afterCreate = SnapshotEdits.addSupplement(AppSnapshot(), created, listOf(reminder))
        val afterEdit = SnapshotEdits.updateSupplement(afterCreate, created.copy(brand = "Marque", dose = "Corrigée"))
            .copy(supplementIntakes = listOf(SupplementIntake(id = "si", supplementId = "s")))
        val afterDelete = SnapshotEdits.deleteSupplement(afterEdit, "s")

        assertEquals("Marque", afterEdit.supplements.single().brand)
        assertEquals("Corrigée", afterEdit.supplements.single().dose)
        assertTrue(afterDelete.supplements.isEmpty())
        assertTrue(afterDelete.supplementIntakes.isEmpty())
        assertTrue(afterDelete.reminders.isEmpty())
    }

    @Test
    fun reminderCrudDoesNotDuplicateIds() {
        val initial = Reminder(id = "r", title = "Initial", hour = 8, minute = 0)
        val created = SnapshotEdits.upsertReminder(AppSnapshot(), initial)
        val edited = SnapshotEdits.upsertReminder(created, initial.copy(title = "Corrigé", hour = 9))
        val deleted = SnapshotEdits.deleteReminder(edited, "r")

        assertEquals(1, edited.reminders.size)
        assertEquals("Corrigé", edited.reminders.single().title)
        assertEquals(9, edited.reminders.single().hour)
        assertTrue(deleted.reminders.isEmpty())
    }

    @Test
    fun profileMutationsDisableEnableAndResetEveryTargetType() {
        val initial = AppSnapshot(routineProfiles = listOf(RoutineProfile(kind = RoutineProfileKind.WEEKDAY, name = "Semaine", weekdays = listOf(1, 2, 3, 4, 5))))
        val disabledProtocol = SnapshotEdits.setProfileItemEnabled(initial, RoutineProfileKind.WEEKDAY, ReminderTargetKind.PROTOCOL, "p", false)
        val disabledSupplement = SnapshotEdits.setProfileItemEnabled(disabledProtocol, RoutineProfileKind.WEEKDAY, ReminderTargetKind.SUPPLEMENT, "s", false)
        val disabledReminder = SnapshotEdits.setProfileReminderEnabled(disabledSupplement, RoutineProfileKind.WEEKDAY, "r", false)
        val profile = disabledReminder.routineProfiles.single()

        assertTrue("p" in profile.disabledProtocolIds)
        assertTrue("s" in profile.disabledSupplementIds)
        assertTrue("r" in profile.disabledReminderIds)

        val reenabled = SnapshotEdits.setProfileItemEnabled(disabledReminder, RoutineProfileKind.WEEKDAY, ReminderTargetKind.PROTOCOL, "p", true)
        assertFalse("p" in reenabled.routineProfiles.single().disabledProtocolIds)

        val reset = SnapshotEdits.resetProfile(disabledReminder, RoutineProfileKind.WEEKDAY).routineProfiles.single()
        assertTrue(reset.disabledProtocolIds.isEmpty())
        assertTrue(reset.disabledSupplementIds.isEmpty())
        assertTrue(reset.disabledReminderIds.isEmpty())
    }
}
