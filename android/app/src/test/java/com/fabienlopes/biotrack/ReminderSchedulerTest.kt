package com.fabienlopes.biotrack

import com.fabienlopes.biotrack.data.AppSnapshot
import com.fabienlopes.biotrack.data.ProtocolItem
import com.fabienlopes.biotrack.data.Reminder
import com.fabienlopes.biotrack.data.ReminderTargetKind
import com.fabienlopes.biotrack.data.Supplement
import com.fabienlopes.biotrack.notifications.ReminderAction
import com.fabienlopes.biotrack.notifications.ReminderScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.TimeZone

class ReminderSchedulerTest {
    private val utc = TimeZone.getTimeZone("UTC")

    @Test
    fun nextDailyTriggerUsesTodayBeforeTimeAndTomorrowAfterTime() {
        val reminder = Reminder(id = "r", title = "Routine", hour = 9, minute = 0)
        val mondayEight = epoch(2026, 8, 17, 8, 0)
        val mondayTen = epoch(2026, 8, 17, 10, 0)

        assertEquals(epoch(2026, 8, 17, 9, 0), ReminderScheduler.nextTriggerMillis(reminder, 0, mondayEight, utc))
        assertEquals(epoch(2026, 8, 18, 9, 0), ReminderScheduler.nextTriggerMillis(reminder, 0, mondayTen, utc))
    }

    @Test
    fun weeklyTriggerUsesIsoMondayMapping() {
        val reminder = Reminder(id = "r", title = "Routine", hour = 9, minute = 0)
        val mondayTen = epoch(2026, 8, 17, 10, 0)

        assertEquals(epoch(2026, 8, 24, 9, 0), ReminderScheduler.nextTriggerMillis(reminder, 1, mondayTen, utc))
        assertEquals(epoch(2026, 8, 19, 9, 0), ReminderScheduler.nextTriggerMillis(reminder, 3, mondayTen, utc))
    }

    @Test
    fun notificationDoneCreatesOneProtocolCompletionOnly() {
        val now = epoch(2026, 8, 17, 10, 0)
        val reminder = Reminder(
            id = "r",
            title = "Routine",
            hour = 9,
            minute = 0,
            targetKind = ReminderTargetKind.PROTOCOL,
            targetId = "p"
        )
        val initial = AppSnapshot(protocols = listOf(ProtocolItem(id = "p", name = "Routine", startDate = now)))

        val first = ReminderAction.applyDone(initial, reminder, now)
        val second = ReminderAction.applyDone(first, reminder, now + 1_000)

        assertEquals(1, second.protocolCompletions.size)
        assertEquals("p", second.protocolCompletions.single().protocolId)
    }

    @Test
    fun notificationDoneCopiesSupplementDoseAndBrand() {
        val now = epoch(2026, 8, 17, 10, 0)
        val reminder = Reminder(
            id = "r",
            title = "Suivi",
            hour = 9,
            minute = 0,
            targetKind = ReminderTargetKind.SUPPLEMENT,
            targetId = "s"
        )
        val initial = AppSnapshot(supplements = listOf(Supplement(id = "s", name = "Suivi", dose = "1 repère", brand = "Libre")))

        val updated = ReminderAction.applyDone(initial, reminder, now)

        assertEquals("1 repère", updated.supplementIntakes.single().dose)
        assertEquals("Libre", updated.supplementIntakes.single().brand)
    }

    @Test
    fun requestCodesSeparateWeekdaysAndActions() {
        assertNotEquals(ReminderScheduler.requestCode("r", 1), ReminderScheduler.requestCode("r", 2))
        assertNotEquals(ReminderScheduler.requestCode("r", 15), ReminderScheduler.requestCode("r", 30))
    }

    @Test
    fun cancellationCoversRecurringAndEverySnoozeVariant() {
        assertEquals(
            (0..7).toSet() + setOf(1015, 1030, 1060),
            ReminderScheduler.cancellationVariants()
        )
    }

    private fun epoch(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute).toInstant(ZoneOffset.UTC).toEpochMilli()
}
