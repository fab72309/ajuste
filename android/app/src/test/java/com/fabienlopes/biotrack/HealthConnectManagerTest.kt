package com.fabienlopes.biotrack

import com.fabienlopes.biotrack.integration.overlapMinutes
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class HealthConnectManagerTest {
    @Test
    fun sleepSessionIsClippedToTheRequestedLocalDay() {
        val dayStart = Instant.parse("2026-08-18T00:00:00Z")
        val dayEnd = Instant.parse("2026-08-19T00:00:00Z")

        assertEquals(
            420.0,
            overlapMinutes(
                Instant.parse("2026-08-17T22:00:00Z"),
                Instant.parse("2026-08-18T07:00:00Z"),
                dayStart,
                dayEnd
            ),
            0.0
        )
        assertEquals(
            120.0,
            overlapMinutes(
                Instant.parse("2026-08-18T22:00:00Z"),
                Instant.parse("2026-08-19T06:00:00Z"),
                dayStart,
                dayEnd
            ),
            0.0
        )
        assertEquals(
            0.0,
            overlapMinutes(
                Instant.parse("2026-08-19T01:00:00Z"),
                Instant.parse("2026-08-19T02:00:00Z"),
                dayStart,
                dayEnd
            ),
            0.0
        )
    }
}
