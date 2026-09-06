package com.fabienlopes.biotrack

import com.fabienlopes.biotrack.data.AppSnapshot
import com.fabienlopes.biotrack.data.Frequency
import com.fabienlopes.biotrack.data.ProtocolItem
import com.fabienlopes.biotrack.data.ProtocolCompletion
import com.fabienlopes.biotrack.data.Reminder
import com.fabienlopes.biotrack.data.RoutineProfile
import com.fabienlopes.biotrack.data.RoutineProfileKind
import com.fabienlopes.biotrack.data.Supplement
import com.fabienlopes.biotrack.data.SupplementIntake
import com.fabienlopes.biotrack.domain.Planner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class PlannerTest {
    @Test
    fun dailyPlanIncludesActiveProtocolAndSupplement() {
        val protocol = ProtocolItem(name = "Routine")
        val supplement = Supplement(name = "Suivi")
        val plan = Planner.plan(AppSnapshot(protocols = listOf(protocol), supplements = listOf(supplement)))
        assertEquals(2, plan.total)
        assertEquals(0, plan.done)
    }

    @Test
    fun multipleTimesPerDayCreatesIndependentOccurrences() {
        val date = timestamp(LocalDate.of(2026, 8, 17))
        val supplement = Supplement(id = "s", name = "Suivi", frequency = Frequency.timesPerDay(3))
        val snapshot = AppSnapshot(
            supplements = listOf(supplement),
            supplementIntakes = listOf(
                SupplementIntake(id = "i1", supplementId = "s", date = date),
                SupplementIntake(id = "i2", supplementId = "s", date = date + 1_000)
            )
        )

        val plan = Planner.plan(snapshot, date)

        assertEquals(3, plan.total)
        assertEquals(2, plan.done)
        assertEquals(listOf(0, 1, 2), plan.items.map { it.occurrenceIndex }.sorted())
        assertEquals(3, plan.items.map { it.id }.distinct().size)
    }

    @Test
    fun explicitOccurrenceKeepsTheSelectedLineIndependent() {
        val date = timestamp(LocalDate.of(2026, 8, 17))
        val protocol = ProtocolItem(id = "p", name = "Routine", frequency = Frequency.timesPerDay(3), startDate = date)
        val snapshot = AppSnapshot(
            protocols = listOf(protocol),
            protocolCompletions = listOf(
                ProtocolCompletion(protocolId = "p", date = date, occurrenceIndex = 2)
            )
        )

        val plan = Planner.plan(snapshot, date)

        assertFalse(plan.items.first { it.occurrenceIndex == 0 }.done)
        assertFalse(plan.items.first { it.occurrenceIndex == 1 }.done)
        assertTrue(plan.items.first { it.occurrenceIndex == 2 }.done)
    }

    @Test
    fun legacyLogsFillOnlyUnoccupiedOccurrences() {
        val date = timestamp(LocalDate.of(2026, 8, 17))
        val supplement = Supplement(id = "s", name = "Suivi", frequency = Frequency.timesPerDay(3))
        val snapshot = AppSnapshot(
            supplements = listOf(supplement),
            supplementIntakes = listOf(
                SupplementIntake(id = "explicit", supplementId = "s", date = date, occurrenceIndex = 1),
                SupplementIntake(id = "legacy", supplementId = "s", date = date + 1_000)
            )
        )

        val plan = Planner.plan(snapshot, date)

        assertTrue(plan.items.first { it.occurrenceIndex == 0 }.done)
        assertTrue(plan.items.first { it.occurrenceIndex == 1 }.done)
        assertFalse(plan.items.first { it.occurrenceIndex == 2 }.done)
    }

    @Test
    fun specificDaysOnlySchedulesSelectedWeekday() {
        val monday = timestamp(LocalDate.of(2026, 8, 17))
        val tuesday = timestamp(LocalDate.of(2026, 8, 18))
        val protocol = ProtocolItem(id = "p", name = "Lundi", frequency = Frequency.weekly(listOf(1)), startDate = monday)
        val snapshot = AppSnapshot(protocols = listOf(protocol))

        assertEquals(1, Planner.plan(snapshot, monday).total)
        assertEquals(0, Planner.plan(snapshot, tuesday).total)
    }

    @Test
    fun asNeededIsNeverAutomaticallyPlannedButCanHaveManualLog() {
        val date = timestamp(LocalDate.of(2026, 8, 17))
        val protocol = ProtocolItem(id = "p", name = "Libre", frequency = Frequency.weekly(emptyList()), startDate = date)
        val snapshot = AppSnapshot(protocols = listOf(protocol))

        assertEquals(0, Planner.plan(snapshot, date).total)
        assertTrue(protocol.frequency.isAsNeeded)
        assertFalse(Planner.isProtocolDoneToday("p", snapshot, date))
    }

    @Test
    fun routineProfileExclusionsApplyToEveryTargetType() {
        val date = timestamp(LocalDate.of(2026, 8, 17))
        val profile = RoutineProfile(
            kind = RoutineProfileKind.WEEKDAY,
            name = "Semaine",
            weekdays = listOf(1, 2, 3, 4, 5),
            disabledProtocolIds = listOf("p"),
            disabledSupplementIds = listOf("s"),
            disabledReminderIds = listOf("r")
        )
        val snapshot = AppSnapshot(
            protocols = listOf(ProtocolItem(id = "p", name = "Routine", startDate = date)),
            supplements = listOf(Supplement(id = "s", name = "Suivi")),
            reminders = listOf(Reminder(id = "r", title = "Rappel", hour = 23, minute = 0, weekdays = listOf(1))),
            routineProfiles = listOf(profile),
            activeRoutineProfileKindRaw = RoutineProfileKind.WEEKDAY.name
        )

        val plan = Planner.plan(snapshot, date)
        assertEquals(0, plan.total)
        assertTrue(plan.reminders.isEmpty())
    }

    @Test
    fun remindersForTodayKeepDisabledItemsVisibleButExcludeOtherWeekdays() {
        val monday = timestamp(LocalDate.of(2026, 8, 17))
        val snapshot = AppSnapshot(reminders = listOf(
            Reminder(id = "later", title = "Plus tard", hour = 18, minute = 0, weekdays = listOf(1)),
            Reminder(id = "disabled", title = "Désactivé", hour = 8, minute = 0, weekdays = listOf(1), enabled = false),
            Reminder(id = "tuesday", title = "Mardi", hour = 9, minute = 0, weekdays = listOf(2))
        ))

        assertEquals(listOf("disabled", "later"), Planner.remindersScheduledToday(snapshot, monday).map { it.id })
        assertEquals(listOf("later"), Planner.upcomingRemindersToday(snapshot, monday).map { it.id })
    }

    private fun timestamp(date: LocalDate): Long = date.atTime(12, 0)
        .atZone(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
}
