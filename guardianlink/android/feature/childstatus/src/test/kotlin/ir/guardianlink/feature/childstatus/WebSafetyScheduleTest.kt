package ir.guardianlink.feature.childstatus

import ir.guardianlink.core.network.WebSafetyScheduleOut
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZonedDateTime

class WebSafetyScheduleTest {
    private fun schedule(day: Int, start: String, end: String, action: String, zone: String? = "UTC") =
        WebSafetyScheduleOut("schedule", day, start, end, action, zone, true)

    @Test fun `same day window is inclusive at start and exclusive at end`() {
        val s = schedule(1, "08:00", "09:00", "BLOCK")
        assertEquals("BLOCK", WebSafetyScheduleMatcher.activeAction(listOf(s), ZonedDateTime.parse("2026-09-28T08:00:00Z")))
        assertEquals("BLOCK", WebSafetyScheduleMatcher.activeAction(listOf(s), ZonedDateTime.parse("2026-09-28T08:59:00Z")))
        assertEquals(null, WebSafetyScheduleMatcher.activeAction(listOf(s), ZonedDateTime.parse("2026-09-28T09:00:00Z")))
    }

    @Test fun `overnight window extends to next day`() {
        val s = schedule(1, "22:00", "06:00", "ALLOW")
        assertEquals("ALLOW", WebSafetyScheduleMatcher.activeAction(listOf(s), ZonedDateTime.parse("2026-09-28T23:00:00Z")))
        assertEquals("ALLOW", WebSafetyScheduleMatcher.activeAction(listOf(s), ZonedDateTime.parse("2026-09-29T05:59:00Z")))
        assertEquals(null, WebSafetyScheduleMatcher.activeAction(listOf(s), ZonedDateTime.parse("2026-09-29T06:00:00Z")))
    }

    @Test fun `schedule time zone is respected`() {
        val s = schedule(1, "09:00", "10:00", "BLOCK", "Europe/Paris")
        assertEquals("BLOCK", WebSafetyScheduleMatcher.activeAction(listOf(s), ZonedDateTime.parse("2026-09-28T07:30:00Z")))
        assertEquals(null, WebSafetyScheduleMatcher.activeAction(listOf(s), ZonedDateTime.parse("2026-09-28T08:30:00Z")))
    }
}
