package ir.guardianlink.feature.childstatus

import ir.guardianlink.core.network.AppRuleScheduleOut
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class AppPolicyScheduleTest {
    private val utc = ZoneId.of("UTC")
    private fun at(day: Int, hour: Int, minute: Int = 0) =
        ZonedDateTime.of(2026, 9, day, hour, minute, 0, 0, utc)

    @Test fun `same day schedule includes start and excludes end`() {
        val monday = AppRuleScheduleOut("id", 1, "20:00", "21:00", true)
        assertTrue(AppPolicyEngine.isWithinSchedule(monday, at(28, 20))) // Monday
        assertTrue(AppPolicyEngine.isWithinSchedule(monday, at(28, 20, 59)))
        assertFalse(AppPolicyEngine.isWithinSchedule(monday, at(28, 21)))
    }

    @Test fun `overnight schedule carries into following day`() {
        val monday = AppRuleScheduleOut("id", 1, "22:00", "06:00", true)
        assertTrue(AppPolicyEngine.isWithinSchedule(monday, at(28, 23)))
        assertTrue(AppPolicyEngine.isWithinSchedule(monday, at(29, 5, 59))) // Tuesday morning
        assertFalse(AppPolicyEngine.isWithinSchedule(monday, at(29, 6)))
        assertFalse(AppPolicyEngine.isWithinSchedule(monday, at(29, 23)))
    }

    @Test fun `previous selected day controls overnight tail`() {
        val monday = AppRuleScheduleOut("id", 1, "22:00", "06:00", true)
        assertFalse(AppPolicyEngine.isWithinSchedule(monday, at(30, 5))) // Wednesday, not Tuesday tail
    }
}
