package ir.guardianlink.server

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.util.UUID

class AppManagementUnitTest {
    private fun rule(limit: Int? = 60, near: Int = 80, critical: Int = 90) = AppRuleRow(
        id = UUID.randomUUID(), deviceId = UUID.randomUUID(), packageName = "com.example.game",
        ruleType = "LIMITED", dailyLimitSeconds = limit, nearPct = near, criticalPct = critical,
        enabled = true, createdBy = UUID.randomUUID(), version = 1,
        lastEnforcementStatus = "unknown", lastEnforcementAt = null,
        createdAt = OffsetDateTime.now(), updatedAt = OffsetDateTime.now(),
    )

    @Test fun `threshold stage is stable at exact boundaries`() {
        val r = rule()
        assertEquals(0, AppManagementRepo.thresholdState(r, 47))
        assertEquals(1, AppManagementRepo.thresholdState(r, 48))
        assertEquals(1, AppManagementRepo.thresholdState(r, 53))
        assertEquals(2, AppManagementRepo.thresholdState(r, 54))
        assertEquals(2, AppManagementRepo.thresholdState(r, 59))
        assertEquals(3, AppManagementRepo.thresholdState(r, 60))
    }

    @Test fun `no limit never creates threshold stage`() {
        assertEquals(0, AppManagementRepo.thresholdState(rule(limit = null), 100_000))
    }

}
